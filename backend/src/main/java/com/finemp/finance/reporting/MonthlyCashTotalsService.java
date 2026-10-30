package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.CashActivity;
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
            SELECT EXTRACT(MONTH FROM COALESCE(e.payment_date,e.due_date))::int AS month,
                COALESCE(c.cash_activity,p.cash_activity,'OPERATING') AS activity,
                sum(CASE WHEN e.payment_date IS NOT NULL AND e.direction='INCOME' THEN COALESCE(e.actual_paid_amount,e.nominal_amount) ELSE 0 END) AS real_income,
                sum(CASE WHEN e.payment_date IS NOT NULL AND e.direction='EXPENSE' THEN COALESCE(e.actual_paid_amount,e.nominal_amount) ELSE 0 END) AS real_expense,
                sum(CASE WHEN e.payment_date IS NULL AND e.direction='INCOME' THEN e.nominal_amount ELSE 0 END) AS open_income,
                sum(CASE WHEN e.payment_date IS NULL AND e.direction='EXPENSE' THEN e.nominal_amount ELSE 0 END) AS open_expense
            FROM financial_entries e
            LEFT JOIN financial_categories c ON c.company_id=e.company_id AND c.id=e.category_id
            LEFT JOIN financial_categories p ON p.company_id=e.company_id AND p.id=c.parent_id
            WHERE e.company_id=:company AND
                ((e.payment_date IS NOT NULL AND e.payment_date BETWEEN :from AND :to) OR (e.payment_date IS NULL AND e.due_date BETWEEN :from AND :to))
            GROUP BY 1,2
            """;
        var data=new HashMap<Integer,EnumMap<CashActivity,ActivityMonth>>();
        jdbc.query(sql,params,rs->{int m=rs.getInt("month");var activity=CashActivity.valueOf(rs.getString("activity"));data.computeIfAbsent(m,key->new EnumMap<>(CashActivity.class)).put(activity,new ActivityMonth(m,activity,money(rs.getBigDecimal("real_income")),money(rs.getBigDecimal("real_expense")),money(rs.getBigDecimal("open_income")),money(rs.getBigDecimal("open_expense"))));});
        var months=new ArrayList<Month>(12);var activities=new ArrayList<ActivityMonth>(36);
        for(int m=1;m<=12;m++){
            var slices=new ArrayList<ActivityMonth>(3);var classified=data.getOrDefault(m,new EnumMap<>(CashActivity.class));
            for(var activity:CashActivity.values())slices.add(classified.getOrDefault(activity,new ActivityMonth(m,activity,zero(),zero(),zero(),zero())));
            activities.addAll(slices);months.add(new Month(m,sum(slices,ActivityMonth::realIncome),sum(slices,ActivityMonth::realExpense),sum(slices,ActivityMonth::openIncome),sum(slices,ActivityMonth::openExpense)));
        }
        return new Report(year,months,activities);
    }
    private static BigDecimal sum(List<ActivityMonth> rows,java.util.function.Function<ActivityMonth,BigDecimal> value){return rows.stream().map(value).reduce(zero(),BigDecimal::add);}
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private static BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.UNNECESSARY);}
    public record Month(int month,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realExpense,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openExpense){}
    public record ActivityMonth(int month,CashActivity activity,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realExpense,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openExpense){}
    public record PeriodTotal(int period,int firstMonth,int lastMonth,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realExpense,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openIncome,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openExpense){}
    public record Report(int year,List<Month> months,List<ActivityMonth> activityMonths){
        public Report{months=List.copyOf(months);activityMonths=List.copyOf(activityMonths);}
        @com.fasterxml.jackson.annotation.JsonProperty public List<PeriodTotal> quarters(){return periods(3);}
        @com.fasterxml.jackson.annotation.JsonProperty public List<PeriodTotal> halfYears(){return periods(6);}
        private List<PeriodTotal> periods(int length){
            var result=new ArrayList<PeriodTotal>(12/length);
            for(int first=1;first<=12;first+=length){
                int start=first,end=first+length-1;
                var slice=months.stream().filter(m->m.month()>=start && m.month()<=end).toList();
                result.add(new PeriodTotal((first-1)/length+1,start,end,sum(slice,Month::realIncome),sum(slice,Month::realExpense),sum(slice,Month::openIncome),sum(slice,Month::openExpense)));
            }
            return List.copyOf(result);
        }
        private static BigDecimal sum(List<Month> months,java.util.function.Function<Month,BigDecimal> amount){return months.stream().map(amount).reduce(zero(),BigDecimal::add);}
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
