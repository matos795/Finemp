package com.finemp.finance.withholding;

import com.finemp.access.*;
import com.finemp.administration.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(prefix="finemp.finance.withholdings",name="enabled",havingValue="true")
@Transactional(readOnly=true,timeout=5)
public class WithholdingReadService {
    private final JdbcTemplate jdbc;private final CompanyPermissionAuthorizationService permissions;private final JsonMapper json=JsonMapper.builder().build();
    public WithholdingReadService(JdbcTemplate jdbc,CompanyPermissionAuthorizationService permissions) {this.jdbc=jdbc;this.permissions=permissions;}
    public View find(UUID actor,UUID company,UUID entry,UUID id) {require(actor,company);return jdbc.query("SELECT * FROM financial_tax_withholdings WHERE company_id=? AND entry_id=? AND id=?",(row,index)->view(row),company,entry,id).stream().findFirst().orElseThrow(()->new SaasResourceNotFoundException("Retenção financeira"));}
    public PagedResponse<View> list(UUID actor,UUID company,UUID entry,WithholdingTreatment treatment,int page,int size) {
        require(actor,company);page(page,size);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_entries WHERE company_id=? AND id=?)",Boolean.class,company,entry)))throw new SaasResourceNotFoundException("Lançamento financeiro");
        String filter=treatment==null?null:treatment.name();String where=" WHERE company_id=? AND entry_id=? AND (?::varchar IS NULL OR treatment=?::varchar)";
        long total=jdbc.queryForObject("SELECT count(*) FROM financial_tax_withholdings"+where,Long.class,company,entry,filter,filter);
        var rows=jdbc.query("SELECT * FROM financial_tax_withholdings"+where+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(row,index)->view(row),company,entry,filter,filter,size,(long)page*size);return new PagedResponse<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    public PagedResponse<History> history(UUID actor,UUID company,UUID entry,UUID id,int page,int size) {
        require(actor,company);page(page,size);
        String where=" FROM access_audit_events WHERE company_id=? AND resource_type='FINANCIAL_WITHHOLDING' AND resource_id=? AND COALESCE(after_state::jsonb->>'entryId',before_state::jsonb->>'entryId')=?";
        long total=jdbc.queryForObject("SELECT count(*)"+where,Long.class,company,id,entry.toString());
        var rows=jdbc.query("SELECT *"+where+" ORDER BY occurred_at DESC,id LIMIT ? OFFSET ?",(row,index)->new History(row.getObject("id",UUID.class),row.getString("action"),row.getObject("actor_user_account_id",UUID.class),row.getTimestamp("occurred_at").toInstant(),node(row.getString("before_state")),node(row.getString("after_state")),row.getObject("command_id",UUID.class)),company,id,entry.toString(),size,(long)page*size);return new PagedResponse<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    private View view(ResultSet row)throws SQLException {return new View(row.getObject("id",UUID.class),row.getObject("company_id",UUID.class),row.getObject("entry_id",UUID.class),new WithholdingValues(row.getString("tax_code"),WithholdingTreatment.valueOf(row.getString("treatment")),row.getBigDecimal("amount"),row.getString("document_reference")),row.getLong("version"),row.getObject("created_by",UUID.class),row.getObject("updated_by",UUID.class),row.getTimestamp("created_at").toInstant(),row.getTimestamp("updated_at").toInstant());}
    private JsonNode node(String value) {return value==null?null:json.readTree(value);}
    private void require(UUID actor,UUID company) {if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();}
    private void page(int page,int size) {if(page<0 || size<1 || size>100)throw new IllegalArgumentException("Página de retenções inválida.");}
    public record View(UUID id,UUID companyId,UUID entryId,WithholdingValues values,long version,UUID createdBy,UUID updatedBy,Instant createdAt,Instant updatedAt) { }
    public record History(UUID id,String action,UUID actorId,Instant occurredAt,JsonNode beforeState,JsonNode afterState,UUID commandId) { }
}
