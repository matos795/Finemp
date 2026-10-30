package com.finemp.finance.reporting;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnnualCashTotalsTest {
    @Test void totalsPreserveCentsAndKeepRealizedAndOpenDirectionsSeparate(){
        var months=new ArrayList<MonthlyCashTotalsService.Month>();
        for(int i=1;i<=12;i++)months.add(new MonthlyCashTotalsService.Month(i,new BigDecimal("0.10"),new BigDecimal("0.01"),new BigDecimal("1.23"),new BigDecimal("1000000000000.99")));
        var report=new MonthlyCashTotalsService.Report(2026,months,List.of());
        assertThat(report.totalRealIncome()).isEqualByComparingTo("1.20");assertThat(report.totalRealExpense()).isEqualByComparingTo("0.12");
        assertThat(report.totalOpenIncome()).isEqualByComparingTo("14.76");assertThat(report.totalOpenExpense()).isEqualByComparingTo("12000000000011.88");
        months.clear();assertThat(report.months()).hasSize(12);assertThat(report.totalRealIncome()).isEqualByComparingTo("1.20");
        var json=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(report);
        assertThat(json.get("totalRealIncome").isString()).isTrue();assertThat(json.get("totalRealIncome").asString()).isEqualTo("1.20");
        assertThat(json.get("totalOpenExpense").asString()).isEqualTo("12000000000011.88");
    }
    @Test void yearWithoutMovementsKeepsAllTotalsAtExactZero(){
        var zero=new BigDecimal("0.00");var months=new ArrayList<MonthlyCashTotalsService.Month>();
        for(int i=1;i<=12;i++)months.add(new MonthlyCashTotalsService.Month(i,zero,zero,zero,zero));
        var report=new MonthlyCashTotalsService.Report(2026,months,List.of());
        assertThat(List.of(report.totalRealIncome(),report.totalRealExpense(),report.totalOpenIncome(),report.totalOpenExpense())).containsOnly(zero);
        assertThat(report.quarters()).hasSize(4).allSatisfy(p->assertThat(List.of(p.realIncome(),p.realExpense(),p.openIncome(),p.openExpense())).containsOnly(zero));
        assertThat(report.halfYears()).hasSize(2).allSatisfy(p->assertThat(List.of(p.realIncome(),p.realExpense(),p.openIncome(),p.openExpense())).containsOnly(zero));
    }
    @Test void civilPeriodsKeepBoundaryMonthsAndAllMeasuresExact(){
        var months=new ArrayList<MonthlyCashTotalsService.Month>();
        for(int i=1;i<=12;i++)months.add(new MonthlyCashTotalsService.Month(i,BigDecimal.valueOf(i,2),new BigDecimal("0.01"),new BigDecimal("1.23"),new BigDecimal("1000000000000.99")));
        // Order of the input must not determine the period membership.
        Collections.reverse(months);var report=new MonthlyCashTotalsService.Report(2026,months,List.of());
        assertThat(report.quarters()).extracting(MonthlyCashTotalsService.PeriodTotal::realIncome).containsExactly(new BigDecimal("0.06"),new BigDecimal("0.15"),new BigDecimal("0.24"),new BigDecimal("0.33"));
        assertThat(report.halfYears()).extracting(MonthlyCashTotalsService.PeriodTotal::realIncome).containsExactly(new BigDecimal("0.21"),new BigDecimal("0.57"));
        for(var periods:List.of(report.quarters(),report.halfYears())){
            int length=12/periods.size();
            for(var p:periods){assertThat(p.firstMonth()).isEqualTo((p.period()-1)*length+1);assertThat(p.lastMonth()).isEqualTo(p.period()*length);}
            assertThat(periods.stream().map(MonthlyCashTotalsService.PeriodTotal::realIncome).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(report.totalRealIncome());
            assertThat(periods.stream().map(MonthlyCashTotalsService.PeriodTotal::realExpense).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(report.totalRealExpense());
            assertThat(periods.stream().map(MonthlyCashTotalsService.PeriodTotal::openIncome).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(report.totalOpenIncome());
            assertThat(periods.stream().map(MonthlyCashTotalsService.PeriodTotal::openExpense).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(report.totalOpenExpense());
        }
        var json=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(report);
        assertThat(json.get("quarters").get(0).get("realIncome").asString()).isEqualTo("0.06");
        assertThat(json.get("halfYears").get(1).get("openExpense").asString()).isEqualTo("6000000000005.94");
    }
}
