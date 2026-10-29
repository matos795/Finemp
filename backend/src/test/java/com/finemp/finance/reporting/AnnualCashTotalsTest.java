package com.finemp.finance.reporting;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnnualCashTotalsTest {
    @Test void totalsPreserveCentsAndKeepRealizedAndOpenDirectionsSeparate(){
        var months=new ArrayList<MonthlyCashTotalsService.Month>();
        for(int i=1;i<=12;i++)months.add(new MonthlyCashTotalsService.Month(i,new BigDecimal("0.10"),new BigDecimal("0.01"),new BigDecimal("1.23"),new BigDecimal("1000000000000.99")));
        var report=new MonthlyCashTotalsService.Report(2026,months);
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
        var report=new MonthlyCashTotalsService.Report(2026,months);
        assertThat(List.of(report.totalRealIncome(),report.totalRealExpense(),report.totalOpenIncome(),report.totalOpenExpense())).containsOnly(zero);
    }
}
