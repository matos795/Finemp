package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.audit.*;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.bank.BankAccountRepository;
import com.finemp.finance.period.FinancialPeriodGuard;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Connection;
import java.time.YearMonth;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Internal command only: callers must validate file/account and explicit selection before use. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.statement-import",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class StatementImportCommandService {
    private static final int MAX_ITEMS=5000;
    private final JdbcTemplate jdbc;private final CompanyRepository companies;private final CompanyPermissionAuthorizationService permissions;
    private final BankAccountRepository banks;private final FinancialPeriodGuard periods;private final AccessAuditRecorder audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public StatementImportCommandService(JdbcTemplate jdbc,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,BankAccountRepository banks,FinancialPeriodGuard periods,AccessAuditRecorder audit){this.jdbc=jdbc;this.companies=companies;this.permissions=permissions;this.banks=banks;this.periods=periods;this.audit=audit;}
    public Receipt confirm(UUID actor,UUID company,UUID command,UUID bank,List<StatementValues> selected) {
        require(actor,company);
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->c.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))throw conflict(StatementImportConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);require(actor,company);
        if(command==null || bank==null || selected==null || selected.isEmpty() || selected.size()>MAX_ITEMS || selected.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Seleção de extrato inválida.");
        var items=List.copyOf(selected);String fingerprint=hash(json.writeValueAsString(new Input(company,actor,bank,items)));
        var existing=jdbc.query("SELECT fingerprint,result_state FROM financial_statement_import_receipts WHERE company_id=? AND actor_id=? AND command_id=?",(row,index)->new Stored(row.getString("fingerprint"),json.readValue(row.getString("result_state"),Receipt.class)),company,actor,command).stream().findFirst();
        if(existing.isPresent()){if(!fingerprint.equals(existing.get().fingerprint()))throw conflict(StatementImportConflictException.Reason.IDEMPOTENCY_MISMATCH);return existing.get().receipt();}
        var account=banks.findByIdAndCompanyId(bank,company).orElseThrow(()->new SaasResourceNotFoundException("Conta financeira"));if(!account.isActive())throw conflict(StatementImportConflictException.Reason.INACTIVE_BANK);
        var months=items.stream().map(v->YearMonth.from(v.postedDate())).distinct().toList();periods.requireOpen(company,months);
        var seen=new HashSet<String>();for(var item:items)if(!item.externalId().isEmpty()) {
            if(!seen.add(item.identityKey()) || Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_bank_statement_items WHERE company_id=? AND bank_account_id=? AND identity_key=? AND external_id<>'')",Boolean.class,company,bank,item.identityKey())))throw conflict(StatementImportConflictException.Reason.DUPLICATE_FITID);
        }
        var ids=new ArrayList<UUID>(items.size());for(var item:items){UUID id=UUID.randomUUID();ids.add(id);jdbc.update("INSERT INTO financial_bank_statement_items(id,company_id,bank_account_id,posted_date,signed_amount,memo,external_id,document_reference,identity_key,status,created_by,created_at,version) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,CURRENT_TIMESTAMP,0)",id,company,bank,item.postedDate(),item.signedAmount(),item.memo(),item.externalId(),item.documentReference(),item.identityKey(),actor);}
        var after=new AuditState(bank,List.copyOf(ids));UUID event=audit.recordStatementImport(actor,company,bank,command,json.writeValueAsString(after),Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        var receipt=new Receipt(company,bank,command,List.copyOf(ids),event);jdbc.update("INSERT INTO financial_statement_import_receipts(company_id,actor_id,command_id,bank_account_id,fingerprint,result_state,committed_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",company,actor,command,bank,fingerprint,json.writeValueAsString(receipt));return receipt;
    }
    private void require(UUID actor,UUID company){if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("finemp-statement-import-v1\n"+value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private StatementImportConflictException conflict(StatementImportConflictException.Reason reason){return new StatementImportConflictException(reason);}
    public record Receipt(UUID companyId,UUID bankAccountId,UUID commandId,List<UUID> itemIds,UUID auditEventId){public Receipt{itemIds=List.copyOf(itemIds);}}
    private record AuditState(UUID bankAccountId,List<UUID> itemIds){}
    private record Input(UUID company,UUID actor,UUID bank,List<StatementValues> items){@Override public String toString(){return "StatementImportInput[redacted]";}}
    private record Stored(String fingerprint,Receipt receipt){@Override public String toString(){return "StatementImportStored[redacted]";}}
}
