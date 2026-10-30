package com.finemp.finance.reporting;

import com.finemp.finance.category.OperationalDreLine;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperationalDreSubtotalsTest {
    @Test void allHomologatedLinesComposeExactSubtotalsWithoutComputingTax(){
        var report=report("1000.10","-100.01","-300.02","-10.03","-20.04","-30.05","-40.06","5.07","-6.08","7.09","-8.10","-9.11");
        var totals=report.subtotals();
        assertThat(totals.netRevenue()).isEqualTo(new BigDecimal("900.09"));assertThat(totals.grossProfit()).isEqualTo(new BigDecimal("600.07"));
        assertThat(totals.ebitda()).isEqualTo(new BigDecimal("504.96"));assertThat(totals.ebit()).isEqualTo(new BigDecimal("498.88"));
        assertThat(totals.profitBeforeTax()).isEqualTo(new BigDecimal("497.87"));assertThat(totals.netResult()).isEqualTo(new BigDecimal("488.76"));
        assertThat(report.lines().stream().map(OperationalDreMonthlyReadService.Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(totals.netResult());
        var json=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(report);
        assertThat(json.get("subtotals").get("netResult").asString()).isEqualTo("488.76");assertThat(json.get("partial").asBoolean()).isTrue();
    }
    @Test void signedAdjustmentsAndLargeCanonicalAmountsAreNotRoundedOrInverted(){
        var totals=report("99999999999999999.99","0.01","1.00","0.00","0.00","0.00","0.00","0.00","0.00","0.00","-0.01","0.02").subtotals();
        assertThat(totals.netRevenue()).isEqualTo(new BigDecimal("100000000000000000.00"));
        assertThat(totals.grossProfit()).isEqualTo(new BigDecimal("100000000000000001.00"));
        assertThat(totals.profitBeforeTax()).isEqualTo(new BigDecimal("100000000000000000.99"));
        assertThat(totals.netResult()).isEqualTo(new BigDecimal("100000000000000001.01"));
    }
    @Test void emptyMonthKeepsAllSubtotalsAtExactZero(){
        var amounts=new String[12];Arrays.fill(amounts,"0.00");var totals=report(amounts).subtotals();
        assertThat(List.of(totals.netRevenue(),totals.grossProfit(),totals.ebitda(),totals.ebit(),totals.profitBeforeTax(),totals.netResult())).containsOnly(new BigDecimal("0.00"));
    }
    private OperationalDreMonthlyReadService.Report report(String... amounts){
        var lines=new ArrayList<OperationalDreMonthlyReadService.Line>();var types=OperationalDreLine.values();
        for(int i=0;i<amounts.length;i++){var value=new BigDecimal(amounts[i]);lines.add(new OperationalDreMonthlyReadService.Line(types[i],value,new BigDecimal("0.00"),value));}
        return new OperationalDreMonthlyReadService.Report(UUID.randomUUID(),"2026-07",true,List.of("FINANCIAL_ENTRIES"),lines);
    }
}
