package com.finemp.finance.withholding;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.audit.*;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.period.FinancialPeriodGuard;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.YearMonth;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(prefix="finemp.finance.withholdings",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class WithholdingCommandService {
    private final JdbcTemplate jdbc;private final CompanyRepository companies;private final CompanyPermissionAuthorizationService permissions;
    private final AccessAuditRecorder audit;private final FinancialPeriodGuard periods;private final JsonMapper json=JsonMapper.builder().build();
    public WithholdingCommandService(JdbcTemplate jdbc,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,AccessAuditRecorder audit,FinancialPeriodGuard periods) {
        this.jdbc=jdbc;this.companies=companies;this.permissions=permissions;this.audit=audit;this.periods=periods;
    }
    public WithholdingReceipt create(UUID actor,UUID company,UUID command,UUID entry,Long entryVersion,WithholdingValues values) {return execute(actor,company,command,entry,entryVersion,null,null,values,WithholdingAction.CREATED);}
    public WithholdingReceipt update(UUID actor,UUID company,UUID command,UUID entry,Long entryVersion,UUID id,Long expectedVersion,WithholdingValues values) {return execute(actor,company,command,entry,entryVersion,id,expectedVersion,values,WithholdingAction.UPDATED);}
    public WithholdingReceipt delete(UUID actor,UUID company,UUID command,UUID entry,Long entryVersion,UUID id,Long expectedVersion) {return execute(actor,company,command,entry,entryVersion,id,expectedVersion,null,WithholdingAction.DELETED);}
    private WithholdingReceipt execute(UUID actor,UUID company,UUID command,UUID entry,Long entryVersion,UUID target,Long expectedVersion,WithholdingValues values,WithholdingAction action) {
        require(actor,company);
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->c.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))throw conflict(WithholdingConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);require(actor,company);
        if(command==null || entry==null || entryVersion==null || entryVersion<0 || action!=WithholdingAction.DELETED && values==null
                || action!=WithholdingAction.CREATED && (target==null || expectedVersion==null || expectedVersion<0))throw invalid();
        var input=new Input(company,actor,entry,entryVersion,target,expectedVersion,values,action);var fingerprint=hash(json.writeValueAsString(input));
        var replay=jdbc.query("SELECT fingerprint,result_state FROM financial_withholding_command_receipts WHERE company_id=? AND actor_id=? AND command_id=?",
                (row,index)->new Stored(row.getString("fingerprint"),json.readValue(row.getString("result_state"),WithholdingReceipt.class)),company,actor,command).stream().findFirst();
        if(replay.isPresent()) {if(!fingerprint.equals(replay.get().fingerprint()))throw conflict(WithholdingConflictException.Reason.IDEMPOTENCY_MISMATCH);return replay.get().receipt();}
        var parent=jdbc.query("SELECT version,competence FROM financial_entries WHERE company_id=? AND id=? FOR UPDATE",(row,index)->new Parent(row.getLong("version"),YearMonth.parse(row.getString("competence"))),company,entry).stream().findFirst().orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        if(parent.version()!=entryVersion)throw conflict(WithholdingConflictException.Reason.STALE_ENTRY_VERSION);
        State before=null;
        if(action!=WithholdingAction.CREATED) {
            before=jdbc.query("SELECT * FROM financial_tax_withholdings WHERE company_id=? AND entry_id=? AND id=? FOR UPDATE",(row,index)->state(row),company,entry,target).stream().findFirst().orElseThrow(()->new SaasResourceNotFoundException("Retenção financeira"));
            if(before.version()!=expectedVersion)throw conflict(WithholdingConflictException.Reason.STALE_VERSION);
        }
        periods.requireOpen(company,List.of(parent.competence()));
        UUID id=action==WithholdingAction.CREATED?UUID.randomUUID():target;long version=action==WithholdingAction.CREATED?0L:action==WithholdingAction.UPDATED?Math.addExact(before.version(),1L):before.version();
        if(action==WithholdingAction.CREATED)jdbc.update("""
                INSERT INTO financial_tax_withholdings(id,company_id,entry_id,tax_code,treatment,amount,document_reference,created_by,updated_by,created_at,updated_at,version)
                VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """,id,company,entry,values.taxCode(),values.treatment().name(),values.amount(),values.documentReference(),actor,actor);
        else {
            int changed=action==WithholdingAction.UPDATED?jdbc.update("UPDATE financial_tax_withholdings SET tax_code=?,treatment=?,amount=?,document_reference=?,version=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE company_id=? AND entry_id=? AND id=? AND version=?",
                    values.taxCode(),values.treatment().name(),values.amount(),values.documentReference(),version,actor,company,entry,id,expectedVersion)
                    :jdbc.update("DELETE FROM financial_tax_withholdings WHERE company_id=? AND entry_id=? AND id=? AND version=?",company,entry,id,expectedVersion);
            if(changed!=1)throw conflict(WithholdingConflictException.Reason.STALE_VERSION);
        }
        var after=action==WithholdingAction.DELETED?null:new State(id,entry,values,version);
        var auditAction=switch(action) {case CREATED->AccessAuditAction.FINANCIAL_WITHHOLDING_CREATED;case UPDATED->AccessAuditAction.FINANCIAL_WITHHOLDING_UPDATED;case DELETED->AccessAuditAction.FINANCIAL_WITHHOLDING_DELETED;};
        UUID event=audit.recordFinancialWithholding(auditAction,actor,company,id,command,before==null?null:json.writeValueAsString(before),after==null?null:json.writeValueAsString(after),Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        var receipt=new WithholdingReceipt(company,command,entry,id,action,version,event);
        jdbc.update("INSERT INTO financial_withholding_command_receipts(company_id,actor_id,command_id,fingerprint,result_state,committed_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",company,actor,command,fingerprint,json.writeValueAsString(receipt));return receipt;
    }
    private State state(ResultSet row)throws SQLException {return new State(row.getObject("id",UUID.class),row.getObject("entry_id",UUID.class),new WithholdingValues(row.getString("tax_code"),WithholdingTreatment.valueOf(row.getString("treatment")),row.getBigDecimal("amount"),row.getString("document_reference")),row.getLong("version"));}
    private void require(UUID actor,UUID company) {if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();}
    private String hash(String value) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("finemp-withholding-v1\n"+value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Comando de retenção inválido.");}
    private static WithholdingConflictException conflict(WithholdingConflictException.Reason reason) {return new WithholdingConflictException(reason);}
    public record State(UUID id,UUID entryId,WithholdingValues values,long version) { }
    private record Parent(long version,YearMonth competence) { }
    private record Input(UUID company,UUID actor,UUID entry,Long entryVersion,UUID target,Long expectedVersion,WithholdingValues values,WithholdingAction action) {@Override public String toString(){return "WithholdingInput[redacted]";}}
    private record Stored(String fingerprint,WithholdingReceipt receipt) {@Override public String toString(){return "WithholdingStored[redacted]";}}
}
