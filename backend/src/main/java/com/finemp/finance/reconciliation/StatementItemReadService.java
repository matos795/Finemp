package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.statement-read",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class StatementItemReadService {
    private final CompanyPermissionAuthorizationService permissions;private final NamedParameterJdbcTemplate jdbc;
    public StatementItemReadService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public PagedResponse<View> list(UUID actor,UUID company,UUID bank,StatementStatus status,LocalDate from,LocalDate to,int page,int size){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        if(bank==null || page<0 || size<1 || size>100 || !valid(from) || !valid(to) || from!=null && to!=null && from.isAfter(to))throw new IllegalArgumentException("Filtros de extrato inválidos.");
        var args=new MapSqlParameterSource().addValue("company",company).addValue("bank",bank);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_bank_accounts WHERE company_id=:company AND id=:bank)",args,Boolean.class)))throw new SaasResourceNotFoundException("Conta financeira");
        String where=" FROM financial_bank_statement_items WHERE company_id=:company AND bank_account_id=:bank";
        if(status!=null){where+=" AND status=:status";args.addValue("status",status.name());}if(from!=null){where+=" AND posted_date>=:from";args.addValue("from",from);}if(to!=null){where+=" AND posted_date<=:to";args.addValue("to",to);}
        long count=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)"+where,args,Long.class));args.addValue("limit",size).addValue("offset",(long)page*size);
        var rows=jdbc.query("SELECT *"+where+" ORDER BY posted_date DESC,id ASC LIMIT :limit OFFSET :offset",args,(rs,n)->view(rs));
        return new PagedResponse<>(rows,page,size,count,(int)Math.min(Integer.MAX_VALUE,(count+size-1)/size));
    }
    private boolean valid(LocalDate value){return value==null || value.getYear()>=1 && value.getYear()<=9999;}
    private View view(ResultSet rs)throws SQLException{return new View(rs.getObject("id",UUID.class),rs.getObject("bank_account_id",UUID.class),new StatementValues(rs.getObject("posted_date",LocalDate.class),rs.getBigDecimal("signed_amount"),rs.getString("memo"),rs.getString("external_id"),rs.getString("document_reference")),StatementStatus.valueOf(rs.getString("status")),rs.getObject("linked_entry_id",UUID.class),rs.getObject("created_by",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getLong("version"));}
    public record View(UUID id,UUID bankAccountId,StatementValues values,StatementStatus status,UUID linkedEntryId,UUID createdBy,Instant createdAt,long version){}
}
