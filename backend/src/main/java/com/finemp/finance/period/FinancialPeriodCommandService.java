package com.finemp.finance.period;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.audit.*;
import com.finemp.company.CompanyRepository;
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

@Service
@ConditionalOnProperty(prefix="finemp.finance.periods",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class FinancialPeriodCommandService {
    private final JdbcTemplate jdbc;private final CompanyRepository companies;
    private final CompanyPermissionAuthorizationService permissions;private final AccessAuditRecorder audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public FinancialPeriodCommandService(JdbcTemplate jdbc,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,AccessAuditRecorder audit) {
        this.jdbc=jdbc;this.companies=companies;this.permissions=permissions;this.audit=audit;
    }
    public FinancialPeriodReceipt close(UUID actor,UUID company,UUID command,YearMonth period,Long expectedVersion) {
        return transition(actor,company,command,period,expectedVersion,true,"");
    }
    public FinancialPeriodReceipt reopen(UUID actor,UUID company,UUID command,YearMonth period,Long expectedVersion,String reason) {
        return transition(actor,company,command,period,expectedVersion,false,reason);
    }
    private FinancialPeriodReceipt transition(UUID actor,UUID company,UUID command,YearMonth period,Long expectedVersion,boolean closed,String reason) {
        var permission=closed?CompanyPermission.FINANCIAL_PERIOD_CLOSE:CompanyPermission.FINANCIAL_PERIOD_REOPEN;
        require(actor,company,permission);
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)c->c.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))
            throw conflict(FinancialPeriodConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);require(actor,company,permission);
        FinancialPeriodGuard.valid(period);
        if(command==null || expectedVersion!=null && expectedVersion<0)throw invalid();
        String normalized=closed?"":reason(reason);
        var input=new Input(company,actor,period.toString(),closed,expectedVersion,normalized);String fingerprint=hash(json.writeValueAsString(input));
        var replay=jdbc.query("SELECT fingerprint,result_state FROM financial_period_command_receipts WHERE company_id=? AND actor_id=? AND command_id=?",
                (row,index)->new Stored(row.getString("fingerprint"),json.readValue(row.getString("result_state"),FinancialPeriodReceipt.class)),company,actor,command).stream().findFirst();
        if(replay.isPresent()) {
            if(!fingerprint.equals(replay.get().fingerprint()))throw conflict(FinancialPeriodConflictException.Reason.IDEMPOTENCY_MISMATCH);
            return replay.get().receipt();
        }
        var current=jdbc.query("SELECT id,closed,version FROM financial_periods WHERE company_id=? AND period=? FOR UPDATE",
                (row,index)->new State(row.getObject("id",UUID.class),period.toString(),row.getBoolean("closed"),row.getLong("version")),company,period.toString()).stream().findFirst();
        if(!Objects.equals(expectedVersion,current.map(State::version).orElse(null)))throw conflict(FinancialPeriodConflictException.Reason.STALE_VERSION);
        boolean wasClosed=current.map(State::closed).orElse(false);
        if(wasClosed==closed)throw conflict(closed?FinancialPeriodConflictException.Reason.ALREADY_CLOSED:FinancialPeriodConflictException.Reason.ALREADY_OPEN);
        UUID id=current.map(State::id).orElseGet(UUID::randomUUID);long next=current.isEmpty()?0L:Math.addExact(current.orElseThrow().version(),1L);
        if(current.isEmpty())jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,company,period.toString(),closed,next,actor);
        else if(jdbc.update("UPDATE financial_periods SET closed=?,version=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE company_id=? AND id=? AND version=?",closed,next,actor,company,id,expectedVersion)!=1)
            throw conflict(FinancialPeriodConflictException.Reason.STALE_VERSION);
        var after=new State(id,period.toString(),closed,next);
        var event=audit.recordFinancialPeriod(closed?AccessAuditAction.FINANCIAL_PERIOD_CLOSED:AccessAuditAction.FINANCIAL_PERIOD_REOPENED,
                actor,company,id,command,current.map(json::writeValueAsString).orElse(null),json.writeValueAsString(after),normalized,Set.of(permission));
        var receipt=new FinancialPeriodReceipt(company,command,id,period.toString(),closed,next,event);
        jdbc.update("INSERT INTO financial_period_command_receipts(company_id,actor_id,command_id,fingerprint,result_state,committed_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",company,actor,command,fingerprint,json.writeValueAsString(receipt));
        return receipt;
    }
    private void require(UUID actor,UUID company,CompanyPermission permission) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,permission))throw new OrganizationAccessDeniedException();
    }
    private String reason(String value) {
        if(value==null || value.isBlank() || value.length()>1000)throw invalid();
        for(int i=0;i<value.length();i++) {char c=value.charAt(i);if(c==0 || Character.isLowSurrogate(c))throw invalid();if(Character.isHighSurrogate(c) && (++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))))throw invalid();}
        return value.strip();
    }
    private String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("finemp-period-v1\n"+value).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Comando de período financeiro inválido.");}
    private static FinancialPeriodConflictException conflict(FinancialPeriodConflictException.Reason reason) {return new FinancialPeriodConflictException(reason);}
    private record Input(UUID company,UUID actor,String period,boolean closed,Long expectedVersion,String reason) {@Override public String toString(){return "PeriodInput[redacted]";}}
    private record State(UUID id,String period,boolean closed,Long version) { }
    private record Stored(String fingerprint,FinancialPeriodReceipt receipt) {@Override public String toString(){return "PeriodStored[redacted]";}}
}
