package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.category.FinancialDirection;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.open-titles",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class OpenTitlesReadService {
    private final CompanyPermissionAuthorizationService permissions;private final NamedParameterJdbcTemplate jdbc;
    public OpenTitlesReadService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public PagedResponse<View> list(UUID actor,UUID company,FinancialDirection direction,LocalDate asOf,OpenTitlesAgingService.Scope scope,int page,int size){
        return list(actor,company,direction,asOf,scope,null,page,size);
    }
    public PagedResponse<View> list(UUID actor,UUID company,FinancialDirection direction,LocalDate asOf,OpenTitlesAgingService.Scope scope,OpenTitlesAgingService.Band band,int page,int size){
        return list(actor,company,direction,asOf,scope,band,null,false,page,size);
    }
    public PagedResponse<View> list(UUID actor,UUID company,FinancialDirection direction,LocalDate asOf,OpenTitlesAgingService.Scope scope,OpenTitlesAgingService.Band band,UUID partnerId,boolean unassignedPartner,int page,int size){
        var selection=selection(actor,company,direction,asOf,scope,band,page,size);var args=selection.args();String where=selection.where();
        if(partnerId!=null && unassignedPartner)throw new IllegalArgumentException("Filtros de parceiro incompatíveis.");
        if(partnerId!=null){where+=" AND partner_id=:partner";args.addValue("partner",partnerId);}
        if(unassignedPartner)where+=" AND partner_id IS NULL";
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)"+where,args,Long.class));args.addValue("limit",size).addValue("offset",(long)page*size);
        var rows=jdbc.query("SELECT *,CAST(:asOf AS date)-due_date AS days_late"+where+" ORDER BY due_date ASC,id ASC LIMIT :limit OFFSET :offset",args,(rs,n)->view(rs));
        return new PagedResponse<>(rows,page,size,total,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size));
    }
    public PagedResponse<PartnerTotal> byPartner(UUID actor,UUID company,FinancialDirection direction,LocalDate asOf,OpenTitlesAgingService.Scope scope,OpenTitlesAgingService.Band band,int page,int size){
        var selection=selection(actor,company,direction,asOf,scope,band,page,size);var args=selection.args();
        String grouped="SELECT partner_id,count(*) AS title_count,sum(nominal_amount) AS amount"+selection.where()+" GROUP BY partner_id";
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM ("+grouped+") g",args,Long.class));
        args.addValue("limit",size).addValue("offset",(long)page*size);
        var rows=jdbc.query("SELECT g.*,COALESCE(NULLIF(p.trade_name,''),p.name) AS partner_name FROM ("+grouped+") g LEFT JOIN business_partners p ON p.company_id=:company AND p.id=g.partner_id ORDER BY (g.partner_id IS NULL),p.display_name_key,g.partner_id LIMIT :limit OFFSET :offset",args,
                (rs,n)->new PartnerTotal(rs.getObject("partner_id",UUID.class),rs.getString("partner_name"),rs.getLong("title_count"),rs.getBigDecimal("amount")));
        return new PagedResponse<>(rows,page,size,total,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size));
    }
    private Selection selection(UUID actor,UUID company,FinancialDirection direction,LocalDate asOf,OpenTitlesAgingService.Scope scope,OpenTitlesAgingService.Band band,int page,int size){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        if(direction==null || asOf==null || asOf.getYear()<1 || asOf.getYear()>9999 || scope==null || page<0 || size<1 || size>100)throw new IllegalArgumentException("Consulta de títulos inválida.");
        var args=new MapSqlParameterSource().addValue("company",company).addValue("direction",direction.name()).addValue("asOf",asOf).addValue("competence",YearMonth.from(asOf).toString());
        String where=" FROM financial_entries WHERE company_id=:company AND direction=:direction AND (payment_date IS NULL OR payment_date>CAST(:asOf AS date))"+(scope==OpenTitlesAgingService.Scope.COMPETENCE?" AND competence<=:competence":"");
        if(band!=null){where+=" AND (CAST(:asOf AS date)-due_date) BETWEEN :minDays AND :maxDays";args.addValue("minDays",band.minDays()).addValue("maxDays",band.maxDays());}
        return new Selection(where,args);
    }
    private record Selection(String where,MapSqlParameterSource args){}
    public record PartnerTotal(UUID partnerId,String partnerName,long titleCount,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal nominalAmount){}
    private View view(ResultSet rs)throws SQLException{int days=rs.getInt("days_late");return new View(rs.getObject("id",UUID.class),rs.getString("description"),FinancialDirection.valueOf(rs.getString("direction")),rs.getBigDecimal("nominal_amount"),YearMonth.parse(rs.getString("competence")).toString(),rs.getObject("due_date",LocalDate.class),rs.getObject("payment_date",LocalDate.class),days,OpenTitlesAgingService.Band.forDays(days),rs.getObject("bank_account_id",UUID.class),rs.getObject("partner_id",UUID.class),rs.getObject("result_center_id",UUID.class),rs.getLong("version"),rs.getString("document_number"),rs.getObject("document_date",LocalDate.class),rs.getObject("category_id",UUID.class));}
    public record View(UUID id,String description,FinancialDirection direction,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal nominalAmount,String competence,LocalDate dueDate,LocalDate paymentDate,int daysLate,OpenTitlesAgingService.Band band,UUID bankAccountId,UUID partnerId,UUID resultCenterId,long version,String documentNumber,LocalDate documentDate,UUID categoryId){}
}
