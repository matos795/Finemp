package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.audit.AccessAuditRecorder;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import com.finemp.finance.period.FinancialPeriodGuard;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnExpression("${finemp.finance.statement-actions.enabled:false} && ${finemp.finance.entries.enabled:false}")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class StatementReconciliationCommandService {
    private final JdbcTemplate jdbc;private final CompanyRepository companies;private final CompanyPermissionAuthorizationService permissions;
    private final FinancialPeriodGuard periods;private final FinancialEntryCommandService entryCommands;private final EntrySettlementCommandService settlements;
    private final AccessAuditRecorder audit;private final JsonMapper json=JsonMapper.builder().build();private final ReconciliationCandidateMatcher matcher=new ReconciliationCandidateMatcher();
    public StatementReconciliationCommandService(JdbcTemplate jdbc,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,
            FinancialPeriodGuard periods,FinancialEntryCommandService entryCommands,EntrySettlementCommandService settlements,AccessAuditRecorder audit) {
        this.jdbc=jdbc;this.companies=companies;this.permissions=permissions;this.periods=periods;this.entryCommands=entryCommands;this.settlements=settlements;this.audit=audit;
    }

    public Receipt reconcile(UUID actor,UUID company,UUID command,UUID item,long expectedItemVersion,UUID entry,long expectedEntryVersion) {
        begin(actor,company);if(command==null || item==null || entry==null || expectedItemVersion<0 || expectedEntryVersion<0)throw invalid();
        String fingerprint=fingerprint("reconcile",company,actor,item,expectedItemVersion,entry,expectedEntryVersion);
        var replay=replay(company,actor,command,fingerprint);if(replay!=null)return replay;
        var source=source(company,item);requirePending(source,expectedItemVersion);periods.requireOpen(company,Set.of(YearMonth.from(source.date())));
        var target=entry(company,entry);if(target.version()!=expectedEntryVersion)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);
        boolean linked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_bank_statement_items WHERE company_id=? AND linked_entry_id=?)",Boolean.class,company,entry));
        var candidates=matcher.find(new ReconciliationCandidateMatcher.Statement(source.bank(),source.date(),source.amount()),
                List.of(target.match()),linked?Set.of(entry):Set.of());
        if(candidates.isEmpty())throw conflict(StatementActionConflictException.Reason.NOT_A_CANDIDATE);
        long entryVersion=target.version();
        if(target.paymentDate()==null) {
            var settled=settlements.settle(actor,company,sub(command,"settle"),entry,expectedEntryVersion,
                    new EntrySettlementData(source.date(),source.amount().abs(),source.bank()));
            entryVersion=settled.version();
        } else {
            periods.requireOpen(company,Set.of(YearMonth.from(target.paymentDate())));
        }
        return finish(actor,company,command,fingerprint,source,entry,entryVersion);
    }

    public Receipt createEntry(UUID actor,UUID company,UUID command,UUID item,long expectedItemVersion,CreateEntryData data) {
        begin(actor,company);if(command==null || item==null || expectedItemVersion<0 || data==null)throw invalid();
        String fingerprint=fingerprint("create-entry",company,actor,item,expectedItemVersion,data.description(),data.documentDate(),data.entryDate(),data.competence(),data.dueDate(),
                data.categoryId(),data.partnerId(),data.resultCenterId(),data.costBehaviorOverride(),data.paymentMethod(),data.documentNumber(),data.notes());
        var replay=replay(company,actor,command,fingerprint);if(replay!=null)return replay;
        var source=source(company,item);requirePending(source,expectedItemVersion);periods.requireOpen(company,Set.of(YearMonth.from(source.date())));
        var direction=source.amount().signum()>0?FinancialDirection.INCOME:FinancialDirection.EXPENSE;
        var canonical=new FinancialEntryCommandData(direction,data.description(),source.amount().abs(),data.documentDate(),data.entryDate(),data.competence(),data.dueDate(),null,
                source.bank(),data.categoryId(),data.partnerId(),data.resultCenterId(),data.costBehaviorOverride(),data.paymentMethod(),data.documentNumber(),data.notes());
        var created=entryCommands.create(actor,company,sub(command,"create"),canonical);
        var settled=settlements.settle(actor,company,sub(command,"settle"),created.entryId(),created.version(),
                new EntrySettlementData(source.date(),source.amount().abs(),source.bank()));
        return finish(actor,company,command,fingerprint,source,created.entryId(),settled.version());
    }

    private Receipt finish(UUID actor,UUID company,UUID command,String fingerprint,Source source,UUID entry,long entryVersion) {
        if(source.version()==Long.MAX_VALUE)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);
        long next=source.version()+1;
        int changed=jdbc.update("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=?,version=? WHERE company_id=? AND id=? AND version=? AND status='PENDING' AND linked_entry_id IS NULL",
                entry,next,company,source.id(),source.version());
        if(changed!=1)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);
        var before=new AuditState(StatementStatus.PENDING,null,source.version());
        var after=new AuditState(StatementStatus.RECONCILED,entry,next);
        UUID event=audit.recordStatementReconciled(actor,company,source.id(),command,json.writeValueAsString(before),json.writeValueAsString(after),Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        var result=new Receipt(company,source.id(),entry,command,next,entryVersion,event);
        jdbc.update("INSERT INTO financial_statement_action_receipts(company_id,actor_id,command_id,fingerprint,result_state,committed_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
                company,actor,command,fingerprint,json.writeValueAsString(result));
        return result;
    }

    private void begin(UUID actor,UUID company) {
        require(actor,company);
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->c.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))
            throw conflict(StatementActionConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);require(actor,company);
    }
    private void require(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private Source source(UUID company,UUID item) {
        return jdbc.query("SELECT id,bank_account_id,posted_date,signed_amount,status,version FROM financial_bank_statement_items WHERE company_id=? AND id=? FOR UPDATE",
                (r,n)->new Source(r.getObject("id",UUID.class),r.getObject("bank_account_id",UUID.class),r.getObject("posted_date",LocalDate.class),r.getBigDecimal("signed_amount"),
                        StatementStatus.valueOf(r.getString("status")),r.getLong("version")),company,item).stream().findFirst()
                .orElseThrow(()->new SaasResourceNotFoundException("Movimento de extrato"));
    }
    private EntryRow entry(UUID company,UUID entry) {
        return jdbc.query("SELECT id,direction,bank_account_id,due_date,payment_date,nominal_amount,actual_paid_amount,version FROM financial_entries WHERE company_id=? AND id=? FOR UPDATE",
                (r,n)->new EntryRow(r.getObject("id",UUID.class),FinancialDirection.valueOf(r.getString("direction")),r.getObject("bank_account_id",UUID.class),
                        r.getObject("due_date",LocalDate.class),r.getObject("payment_date",LocalDate.class),r.getBigDecimal("nominal_amount"),r.getBigDecimal("actual_paid_amount"),r.getLong("version")),
                company,entry).stream().findFirst().orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
    }
    private void requirePending(Source source,long version) {
        if(source.version()!=version)throw conflict(StatementActionConflictException.Reason.STALE_VERSION);
        if(source.status()!=StatementStatus.PENDING)throw conflict(StatementActionConflictException.Reason.INVALID_STATE);
    }
    private Receipt replay(UUID company,UUID actor,UUID command,String fingerprint) {
        var stored=jdbc.query("SELECT fingerprint,result_state FROM financial_statement_action_receipts WHERE company_id=? AND actor_id=? AND command_id=?",
                (r,n)->new Stored(r.getString("fingerprint"),json.readValue(r.getString("result_state"),Receipt.class)),company,actor,command).stream().findFirst();
        if(stored.isEmpty())return null;
        if(!stored.get().fingerprint().equals(fingerprint))throw conflict(StatementActionConflictException.Reason.IDEMPOTENCY_MISMATCH);
        return stored.get().receipt();
    }
    private static UUID sub(UUID command,String action) {return UUID.nameUUIDFromBytes(("finemp-statement-reconciliation-v1:"+action+":"+command).getBytes(StandardCharsets.UTF_8));}
    private static String fingerprint(String action,Object...values) {
        var canonical=new StringBuilder("finemp-statement-reconciliation-v1\n");append(canonical,action);for(var value:values)append(canonical,value);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void append(StringBuilder out,Object value){String text=value==null?"":value.toString();out.append(text.length()).append(':').append(text).append('|');}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Comando de conciliação inválido.");}
    private static StatementActionConflictException conflict(StatementActionConflictException.Reason reason){return new StatementActionConflictException(reason);}

    public record CreateEntryData(String description,LocalDate documentDate,LocalDate entryDate,YearMonth competence,LocalDate dueDate,UUID categoryId,
            UUID partnerId,UUID resultCenterId,CostBehavior costBehaviorOverride,String paymentMethod,String documentNumber,String notes) {}
    public record Receipt(UUID companyId,UUID itemId,UUID entryId,UUID commandId,long itemVersion,long entryVersion,UUID auditEventId) {}
    private record Source(UUID id,UUID bank,LocalDate date,BigDecimal amount,StatementStatus status,long version) {}
    private record EntryRow(UUID id,FinancialDirection direction,UUID bank,LocalDate dueDate,LocalDate paymentDate,BigDecimal nominal,BigDecimal actual,long version) {
        ReconciliationCandidateMatcher.Entry match(){return new ReconciliationCandidateMatcher.Entry(id,direction,bank,dueDate,paymentDate,nominal,actual);}
    }
    private record Stored(String fingerprint,Receipt receipt) {}
    private record AuditState(StatementStatus status,UUID linkedEntryId,long version) {}
}
