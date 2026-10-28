package com.finemp.finance.period;

import com.finemp.access.*;
import com.finemp.administration.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(prefix="finemp.finance.periods",name="enabled",havingValue="true")
@Transactional(readOnly=true,timeout=5)
public class FinancialPeriodReadService {
    private final JdbcTemplate jdbc;private final CompanyPermissionAuthorizationService permissions;private final JsonMapper json=JsonMapper.builder().build();
    public FinancialPeriodReadService(JdbcTemplate jdbc,CompanyPermissionAuthorizationService permissions) {this.jdbc=jdbc;this.permissions=permissions;}
    public State find(UUID actor,UUID company,String period) {
        require(actor,company);PeriodCommandJsonDecoder.period(period);
        return jdbc.query("SELECT * FROM financial_periods WHERE company_id=? AND period=?",(row,index)->state(row),company,period).stream().findFirst().orElseGet(()->new State(null,company,period,false,null,null,null));
    }
    public PagedResponse<State> list(UUID actor,UUID company,String from,String to,Boolean closed,int page,int size) {
        require(actor,company);page(page,size);
        if(from!=null)PeriodCommandJsonDecoder.period(from);if(to!=null)PeriodCommandJsonDecoder.period(to);if(from!=null && to!=null && from.compareTo(to)>0)throw invalid();
        String where=" WHERE company_id=? AND (?::varchar IS NULL OR period>=?::varchar) AND (?::varchar IS NULL OR period<=?::varchar) AND (?::boolean IS NULL OR closed=?::boolean)";
        long total=jdbc.queryForObject("SELECT count(*) FROM financial_periods"+where,Long.class,company,from,from,to,to,closed,closed);
        var rows=jdbc.query("SELECT * FROM financial_periods"+where+" ORDER BY period DESC,id LIMIT ? OFFSET ?",(row,index)->state(row),company,from,from,to,to,closed,closed,size,(long)page*size);
        return new PagedResponse<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    public PagedResponse<History> history(UUID actor,UUID company,String period,int page,int size) {
        require(actor,company);PeriodCommandJsonDecoder.period(period);page(page,size);
        String where=" FROM access_audit_events a JOIN financial_periods p ON p.company_id=a.company_id AND p.id=a.resource_id WHERE a.company_id=? AND a.resource_type='FINANCIAL_PERIOD' AND p.period=?";
        long total=jdbc.queryForObject("SELECT count(*)"+where,Long.class,company,period);
        var rows=jdbc.query("SELECT a.*"+where+" ORDER BY a.occurred_at DESC,a.id LIMIT ? OFFSET ?",(row,index)->new History(row.getObject("id",UUID.class),row.getString("action"),row.getObject("actor_user_account_id",UUID.class),row.getTimestamp("occurred_at").toInstant(),node(row.getString("before_state")),node(row.getString("after_state")),row.getString("reason"),row.getObject("command_id",UUID.class)),company,period,size,(long)page*size);
        return new PagedResponse<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    private State state(ResultSet row)throws SQLException {return new State(row.getObject("id",UUID.class),row.getObject("company_id",UUID.class),row.getString("period"),row.getBoolean("closed"),row.getLong("version"),row.getObject("updated_by",UUID.class),row.getTimestamp("updated_at").toInstant());}
    private JsonNode node(String value) {return value==null?null:json.readTree(value);}
    private void require(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_PERIOD_VIEW)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_PERIOD_CLOSE)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_PERIOD_REOPEN))throw new OrganizationAccessDeniedException();
    }
    private void page(int page,int size) {if(page<0 || size<1 || size>100)throw invalid();}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Consulta de períodos inválida.");}
    public record State(UUID id,UUID companyId,String period,boolean closed,Long version,UUID updatedBy,Instant updatedAt) { }
    public record History(UUID id,String action,UUID actorId,Instant occurredAt,JsonNode beforeState,JsonNode afterState,String reason,UUID commandId) { }
}
