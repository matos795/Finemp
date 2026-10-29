package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.audit.*;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.period.FinancialPeriodGuard;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(prefix="finemp.finance.statement-actions",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class StatementIgnoreCommandService {
    private final JdbcTemplate jdbc;private final CompanyRepository companies;private final CompanyPermissionAuthorizationService permissions;
    private final FinancialPeriodGuard periods;private final AccessAuditRecorder audit;private final JsonMapper json=JsonMapper.builder().build();
    public StatementIgnoreCommandService(JdbcTemplate jdbc,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,FinancialPeriodGuard periods,AccessAuditRecorder audit){this.jdbc=jdbc;this.companies=companies;this.permissions=permissions;this.periods=periods;this.audit=audit;}
    public Receipt ignore(UUID actor,UUID company,UUID command,UUID item,Long expectedVersion){
        require(actor,company);if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->c.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))throw conflict(StatementActionConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);require(actor,company);
        if(command==null || item==null || expectedVersion==null || expectedVersion<0)throw new IllegalArgumentException("Comando de extrato inválido.");
        String fingerprint=hash(json.writeValueAsString(new Input(company,actor,item,expectedVersion)));
        var stored=jdbc.query("SELECT fingerprint,result_state FROM financial_statement_action_receipts WHERE company_id=? AND actor_id=? AND command_id=?",(row,n)->new Stored(row.getString("fingerprint"),json.readValue(row.getString("result_state"),Receipt.class)),company,actor,command).stream().findFirst();
        if(stored.isPresent()){if(!fingerprint.equals(stored.get().fingerprint()))throw conflict(StatementActionConflictException.Reason.IDEMPOTENCY_MISMATCH);return stored.get().receipt();}
        var before=jdbc.query("SELECT posted_date,status,version FROM financial_bank_statement_items WHERE company_id=? AND id=? FOR UPDATE",(row,n)->new State(row.getObject("posted_date",LocalDate.class),StatementStatus.valueOf(row.getString("status")),row.getLong("version")),company,item).stream().findFirst().orElseThrow(()->new SaasResourceNotFoundException("Movimento de extrato"));
        if(before.version()!=expectedVersion)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);if(before.status()!=StatementStatus.PENDING)throw conflict(StatementActionConflictException.Reason.INVALID_STATE);
        periods.requireOpen(company,List.of(YearMonth.from(before.date())));if(before.version()==Long.MAX_VALUE)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);long version=before.version()+1;
        if(jdbc.update("UPDATE financial_bank_statement_items SET status='IGNORED',version=? WHERE company_id=? AND id=? AND version=? AND status='PENDING'",version,company,item,expectedVersion)!=1)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);
        var after=new State(before.date(),StatementStatus.IGNORED,version);UUID event=audit.recordStatementIgnored(actor,company,item,command,json.writeValueAsString(before),json.writeValueAsString(after),Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        var result=new Receipt(company,item,command,version,event);jdbc.update("INSERT INTO financial_statement_action_receipts(company_id,actor_id,command_id,fingerprint,result_state,committed_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",company,actor,command,fingerprint,json.writeValueAsString(result));return result;
    }
    private void require(UUID actor,UUID company){if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("finemp-statement-ignore-v1\n"+value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private StatementActionConflictException conflict(StatementActionConflictException.Reason reason){return new StatementActionConflictException(reason);}
    public record Receipt(UUID companyId,UUID itemId,UUID commandId,long version,UUID auditEventId){}
    private record Input(UUID company,UUID actor,UUID item,long expectedVersion){}
    private record Stored(String fingerprint,Receipt receipt){}
    private record State(LocalDate date,StatementStatus status,long version){}
}
