package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.category.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Detail of the current-state monthly cash totals; not a historical ledger. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.monthly-cash",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class CashTitlesReadService {
    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;
    public CashTitlesReadService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public PagedResponse<View> list(UUID actor,UUID company,String month,FinancialDirection direction,State state,CashActivity activity,int page,int size){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        if(month==null || !month.matches("[0-9]{4}-[0-9]{2}") || direction==null || state==null || page<0 || size<1 || size>100)throw new IllegalArgumentException("Consulta de caixa inválida.");
        YearMonth period;
        try{period=YearMonth.parse(month);}catch(DateTimeException ex){throw new IllegalArgumentException("Mês inválido.");}
        if(period.getYear()<1)throw new IllegalArgumentException("Mês inválido.");
        var args=new MapSqlParameterSource().addValue("company",company).addValue("direction",direction.name()).addValue("from",period.atDay(1)).addValue("to",period.atEndOfMonth());
        String resolvedActivity="COALESCE(c.cash_activity,p.cash_activity,'OPERATING')";
        String date=state==State.REALIZED?"e.payment_date":"e.due_date";
        String amount=state==State.REALIZED?"COALESCE(e.actual_paid_amount,e.nominal_amount)":"e.nominal_amount";
        String from=" FROM financial_entries e LEFT JOIN financial_categories c ON c.company_id=e.company_id AND c.id=e.category_id LEFT JOIN financial_categories p ON p.company_id=e.company_id AND p.id=c.parent_id WHERE e.company_id=:company AND e.direction=:direction AND "+date+" BETWEEN :from AND :to";
        if(state==State.OPEN)from+=" AND e.payment_date IS NULL";
        if(activity!=null){from+=" AND "+resolvedActivity+"=:activity";args.addValue("activity",activity.name());}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)"+from,args,Long.class));
        args.addValue("limit",size).addValue("offset",(long)page*size);
        var rows=jdbc.query("SELECT e.id,e.description,e.direction,e.nominal_amount,e.due_date,e.payment_date,e.category_id,e.partner_id,e.bank_account_id,"+date+" AS cash_date,"+amount+" AS cash_amount,"+resolvedActivity+" AS activity"+from+" ORDER BY "+date+",e.id LIMIT :limit OFFSET :offset",args,(rs,n)->new View(rs.getObject("id",UUID.class),rs.getString("description"),FinancialDirection.valueOf(rs.getString("direction")),state,CashActivity.valueOf(rs.getString("activity")),rs.getObject("cash_date",LocalDate.class),rs.getBigDecimal("cash_amount"),rs.getBigDecimal("nominal_amount"),rs.getObject("due_date",LocalDate.class),rs.getObject("payment_date",LocalDate.class),rs.getObject("category_id",UUID.class),rs.getObject("partner_id",UUID.class),rs.getObject("bank_account_id",UUID.class)));
        return new PagedResponse<>(rows,page,size,total,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size));
    }
    public enum State {REALIZED,OPEN}
    public record View(UUID id,String description,FinancialDirection direction,State state,CashActivity activity,LocalDate cashDate,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal nominalAmount,
            LocalDate dueDate,LocalDate paymentDate,UUID categoryId,UUID partnerId,UUID bankAccountId){}
}
