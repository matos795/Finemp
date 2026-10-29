package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Current entry state grouped by payment date or, when unpaid, due date. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.monthly-cash",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class MonthlyCashTotalsService {
    private final CompanyPermissionAuthorizationService permissions;private final NamedParameterJdbcTemplate jdbc;
    public MonthlyCashTotalsService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public Report report(UUID actor,UUID company,int year){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        if(year<1 || year>9999)throw new IllegalArgumentException("Ano de fluxo inválido.");
        var params=new MapSqlParameterSource().addValue("company",company).addValue("from",LocalDate.of(year,1,1)).addValue("to",LocalDate.of(year,12,31));
        String sql="""
            SELECT EXTRACT(MONTH FROM COALESCE(payment_date,due_date))::int AS month,
                sum(CASE WHEN payment_date IS NOT NULL AND direction='INCOME' THEN COALESCE(actual_paid_amount,nominal_amount) ELSE 0 END) AS real_income,
                sum(CASE WHEN payment_date IS NOT NULL AND direction='EXPENSE' THEN COALESCE(actual_paid_amount,nominal_amount) ELSE 0 END) AS real_expense,
                sum(CASE WHEN payment_date IS NULL AND direction='INCOME' THEN nominal_amount ELSE 0 END) AS open_income,
                sum(CASE WHEN payment_date IS NULL AND direction='EXPENSE' THEN nominal_amount ELSE 0 END) AS open_expense
            FROM financial_entries WHERE company_id=:company AND
                ((payment_date IS NOT NULL AND payment_date BETWEEN :from AND :to) OR (payment_date IS NULL AND due_date BETWEEN :from AND :to))
            GROUP BY 1
            """;
        var data=new HashMap<Integer,Month>();jdbc.query(sql,params,rs->{int m=rs.getInt("month");data.put(m,new Month(m,money(rs.getBigDecimal("real_income")),money(rs.getBigDecimal("real_expense")),money(rs.getBigDecimal("open_income")),money(rs.getBigDecimal("open_expense"))));});
        var months=new ArrayList<Month>(12);for(int m=1;m<=12;m++)months.add(data.getOrDefault(m,new Month(m,zero(),zero(),zero(),zero())));return new Report(year,months);
    }
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private static BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.UNNECESSARY);}
    public record Month(int month,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realExpense,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openExpense){}
    public record Report(int year,List<Month> months){
        public Report{months=List.copyOf(months);}
        @com.fasterxml.jackson.annotation.JsonProperty @JsonFormat(shape=JsonFormat.Shape.STRING)
        public BigDecimal totalRealIncome(){return sum(Month::realIncome);}
        @com.fasterxml.jackson.annotation.JsonProperty @JsonFormat(shape=JsonFormat.Shape.STRING)
        public BigDecimal totalRealExpense(){return sum(Month::realExpense);}
        @com.fasterxml.jackson.annotation.JsonProperty @JsonFormat(shape=JsonFormat.Shape.STRING)
        public BigDecimal totalOpenIncome(){return sum(Month::openIncome);}
        @com.fasterxml.jackson.annotation.JsonProperty @JsonFormat(shape=JsonFormat.Shape.STRING)
        public BigDecimal totalOpenExpense(){return sum(Month::openExpense);}
        private BigDecimal sum(java.util.function.Function<Month,BigDecimal> amount){return months.stream().map(amount).reduce(zero(),BigDecimal::add);}
    }
}
