package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Actual payment dates and unpaid due dates; this is not a balance projection. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.daily-cash",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class DailyCashTotalsService {
    private final CompanyPermissionAuthorizationService permissions;private final NamedParameterJdbcTemplate jdbc;
    public DailyCashTotalsService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public Report report(UUID actor,UUID company,String month){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        YearMonth period;try{if(month==null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid();period=YearMonth.parse(month);if(period.getYear()<1 || period.getYear()>9999)throw invalid();}catch(DateTimeException invalid){throw invalid();}
        var params=new MapSqlParameterSource().addValue("company",company).addValue("from",period.atDay(1)).addValue("to",period.atEndOfMonth());
        String sql="""
            SELECT COALESCE(payment_date,due_date) AS day,
                sum(CASE WHEN payment_date IS NOT NULL AND direction='INCOME' THEN COALESCE(actual_paid_amount,nominal_amount) ELSE 0 END) AS real_income,
                sum(CASE WHEN payment_date IS NOT NULL AND direction='EXPENSE' THEN COALESCE(actual_paid_amount,nominal_amount) ELSE 0 END) AS real_expense,
                sum(CASE WHEN payment_date IS NULL AND direction='INCOME' THEN nominal_amount ELSE 0 END) AS open_income,
                sum(CASE WHEN payment_date IS NULL AND direction='EXPENSE' THEN nominal_amount ELSE 0 END) AS open_expense
            FROM financial_entries WHERE company_id=:company AND
                ((payment_date IS NOT NULL AND payment_date BETWEEN :from AND :to) OR (payment_date IS NULL AND due_date BETWEEN :from AND :to))
            GROUP BY 1
            """;
        var data=new HashMap<LocalDate,Day>();jdbc.query(sql,params,rs->{var day=rs.getObject("day",LocalDate.class);data.put(day,new Day(day,money(rs.getBigDecimal("real_income")),money(rs.getBigDecimal("real_expense")),money(rs.getBigDecimal("open_income")),money(rs.getBigDecimal("open_expense"))));});
        var days=new ArrayList<Day>(period.lengthOfMonth());for(int i=1;i<=period.lengthOfMonth();i++){var date=period.atDay(i);days.add(data.getOrDefault(date,new Day(date,zero(),zero(),zero(),zero())));}return new Report(period.toString(),days);
    }
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private static BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.UNNECESSARY);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Mês de fluxo inválido.");}
    public record Day(LocalDate date,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realExpense,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openExpense){}
    public record Report(String month,List<Day> days){public Report{days=List.copyOf(days);}}
}
