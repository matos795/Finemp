package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.FinancialDirection;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** As-of report of current stored title state, not an event-sourced historical reconstruction. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.aging",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class OpenTitlesAgingService {
    private final CompanyPermissionAuthorizationService permissions;private final NamedParameterJdbcTemplate jdbc;
    public OpenTitlesAgingService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public Report report(UUID actor,UUID company,FinancialDirection direction,LocalDate asOf,Scope scope){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        if(direction==null || asOf==null || asOf.getYear()<1 || asOf.getYear()>9999 || scope==null)throw new IllegalArgumentException("Parâmetros de aging inválidos.");
        var params=new MapSqlParameterSource().addValue("company",company).addValue("direction",direction.name()).addValue("asOf",asOf).addValue("competence",YearMonth.from(asOf).toString());
        String sql="""
            SELECT CASE WHEN days_late<=0 AND days_late>=-30 THEN 'DUE_0_30'
                WHEN days_late<=0 AND days_late>=-60 THEN 'DUE_31_60'
                WHEN days_late<=0 AND days_late>=-90 THEN 'DUE_61_90'
                WHEN days_late<=0 THEN 'DUE_OVER_90'
                WHEN days_late<=30 THEN 'OVERDUE_1_30'
                WHEN days_late<=60 THEN 'OVERDUE_31_60'
                WHEN days_late<=90 THEN 'OVERDUE_61_90'
                ELSE 'OVERDUE_OVER_90' END AS band,
                count(*) AS item_count,sum(nominal_amount) AS amount
            FROM (SELECT CAST(:asOf AS date)-due_date AS days_late,nominal_amount FROM financial_entries
                WHERE company_id=:company AND direction=:direction AND (payment_date IS NULL OR payment_date>CAST(:asOf AS date))
            """+(scope==Scope.COMPETENCE?" AND competence<=:competence":"")+") titles GROUP BY band";
        var data=new EnumMap<Band,BandTotal>(Band.class);jdbc.query(sql,params,rs->{var band=Band.valueOf(rs.getString("band"));data.put(band,new BandTotal(band,rs.getLong("item_count"),rs.getBigDecimal("amount").setScale(2,RoundingMode.UNNECESSARY)));});
        var bands=Arrays.stream(Band.values()).map(band->data.getOrDefault(band,new BandTotal(band,0,BigDecimal.ZERO.setScale(2)))).toList();
        long count=bands.stream().mapToLong(BandTotal::count).sum();BigDecimal total=bands.stream().map(BandTotal::amount).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);
        var overdue=bands.stream().filter(band->Set.of(Band.OVERDUE_1_30,Band.OVERDUE_31_60,Band.OVERDUE_61_90,Band.OVERDUE_OVER_90).contains(band.band())).toList();
        long overdueCount=overdue.stream().mapToLong(BandTotal::count).sum();BigDecimal overdueAmount=overdue.stream().map(BandTotal::amount).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);
        var due30=data.getOrDefault(Band.DUE_0_30,new BandTotal(Band.DUE_0_30,0,BigDecimal.ZERO.setScale(2)));return new Report(asOf,direction,scope,count,total,overdueCount,overdueAmount,due30.count(),due30.amount(),bands);
    }
    public enum Scope { ALL,COMPETENCE }
    public enum Band {
        DUE_0_30,DUE_31_60,DUE_61_90,DUE_OVER_90,OVERDUE_1_30,OVERDUE_31_60,OVERDUE_61_90,OVERDUE_OVER_90;
        public static Band forDays(int days){if(days<=0)return days>=-30?DUE_0_30:days>=-60?DUE_31_60:days>=-90?DUE_61_90:DUE_OVER_90;return days<=30?OVERDUE_1_30:days<=60?OVERDUE_31_60:days<=90?OVERDUE_61_90:OVERDUE_OVER_90;}
    }
    public record BandTotal(Band band,long count,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount){}
    public record Report(LocalDate asOf,FinancialDirection direction,Scope scope,long totalCount,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal totalAmount,
            long overdueCount,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal overdueAmount,long dueWithin30Count,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal dueWithin30Amount,List<BandTotal> bands){public Report{bands=List.copyOf(bands);}}
}
