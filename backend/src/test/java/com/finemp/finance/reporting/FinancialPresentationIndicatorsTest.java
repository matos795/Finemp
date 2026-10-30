package com.finemp.finance.reporting;

import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinancialPresentationIndicatorsTest {
    @Test void confirmedRatiosUseTheExistingDreCashAndAgingAmounts(){
        var result=FinancialPresentationIndicators.calculate("2026-07",cash("3.00","2.00"),receivables("3.00","1.00"),dre("3.00","-2.00","-0.50","-0.20"));
        assertThat(result.cash().generation()).isEqualTo(new BigDecimal("1.00"));
        assertThat(result.cash().margin().percent()).isEqualTo(new BigDecimal("33.3"));assertThat(result.cash().margin().numerator()).isEqualTo(new BigDecimal("1.00"));
        assertThat(result.receivables().overdueRate().percent()).isEqualTo(new BigDecimal("33.3"));
        assertThat(result.dre().grossMargin().percent()).isEqualTo(new BigDecimal("33.3"));assertThat(result.dre().ebitdaMargin().percent()).isEqualTo(new BigDecimal("16.7"));assertThat(result.dre().netMargin().percent()).isEqualTo(new BigDecimal("10.0"));
        assertThat(result.dre().partial()).isTrue();assertThat(result.dre().coverage()).containsExactly("FINANCIAL_ENTRIES");
        assertThat(result.percentUnit()).isEqualTo("PERCENT");assertThat(result.percentDecimalPlaces()).isEqualTo(1);
        var json=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(result);assertThat(json.get("dre").get("netMargin").get("percent").asString()).isEqualTo("10.0");
    }
    @Test void zeroBaseIsUnavailableAndNegativeNetRevenueRetainsItsSign(){
        var zero=FinancialPresentationIndicators.calculate("2026-07",cash("0.00","1.00"),receivables("0.00","0.00"),dre("0.00","-1.00","0.00","0.00"));
        for(var metric:List.of(zero.cash().margin(),zero.receivables().overdueRate(),zero.dre().grossMargin(),zero.dre().ebitdaMargin(),zero.dre().netMargin())){
            assertThat(metric.percent()).isNull();assertThat(metric.availability()).isEqualTo(FinancialPresentationIndicators.Availability.UNAVAILABLE_BASE);
        }
        var negative=FinancialPresentationIndicators.calculate("2026-07",cash("1.00","0.00"),receivables("1.00","0.00"),dre("-2.00","1.00","0.00","0.00"));
        assertThat(negative.dre().grossMargin().percent()).isEqualTo(new BigDecimal("50.0"));assertThat(negative.dre().grossMargin().denominator()).isEqualTo(new BigDecimal("-2.00"));
    }
    @Test void percentRoundingIsDirectAndSymmetricWithoutChangingCanonicalMoney(){
        var positive=FinancialPresentationIndicators.calculate("2026-07",cash("2000.00","1999.00"),receivables("2000.00","1.00"),null);
        var negative=FinancialPresentationIndicators.calculate("2026-07",cash("2000.00","2001.00"),receivables("1.00","0.00"),null);
        assertThat(positive.cash().margin().percent()).isEqualTo(new BigDecimal("0.1"));assertThat(negative.cash().margin().percent()).isEqualTo(new BigDecimal("-0.1"));
        assertThat(positive.cash().generation()).isEqualTo(new BigDecimal("1.00"));assertThat(negative.cash().generation()).isEqualTo(new BigDecimal("-1.00"));
        var large=FinancialPresentationIndicators.calculate("2026-07",cash("99999999999999999.99","0.01"),receivables("1.00","0.00"),null);
        assertThat(large.cash().generation()).isEqualTo(new BigDecimal("99999999999999999.98"));assertThat(large.cash().margin().percent()).isEqualTo(new BigDecimal("100.0"));
    }
    @Test void unavailableDreIsOmittedAndTheMonthMustBelongToTheCashYear(){
        var report=FinancialPresentationIndicators.calculate("2026-07",cash("1.00","0.00"),receivables("1.00","0.00"),null);
        assertThat(report.dre()).isNull();assertThat(tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(report).has("dre")).isFalse();
        assertThatThrownBy(()->FinancialPresentationIndicators.calculate("2025-07",cash("1.00","0.00"),receivables("1.00","0.00"),null)).isInstanceOf(IllegalArgumentException.class);
    }
    private MonthlyCashTotalsService.Report cash(String income,String expense){
        var months=new ArrayList<MonthlyCashTotalsService.Month>();var zero=new BigDecimal("0.00");
        for(int m=1;m<=12;m++)months.add(new MonthlyCashTotalsService.Month(m,m==7?new BigDecimal(income):zero,m==7?new BigDecimal(expense):zero,zero,zero));
        return new MonthlyCashTotalsService.Report(2026,months,List.of());
    }
    private OpenTitlesAgingService.Report receivables(String total,String overdue){return new OpenTitlesAgingService.Report(LocalDate.of(2026,7,31),FinancialDirection.INCOME,OpenTitlesAgingService.Scope.ALL,1,new BigDecimal(total),1,new BigDecimal(overdue),0,new BigDecimal("0.00"),List.of());}
    private OperationalDreMonthlyReadService.Report dre(String revenue,String costs,String admin,String tax){
        var amounts=Map.of(OperationalDreLine.REC,revenue,OperationalDreLine.CUS,costs,OperationalDreLine.DADM,admin,OperationalDreLine.IR,tax);
        var lines=Arrays.stream(OperationalDreLine.values()).filter(l->l!=OperationalDreLine.NAO).map(l->{var amount=new BigDecimal(amounts.getOrDefault(l,"0.00"));return new OperationalDreMonthlyReadService.Line(l,amount,new BigDecimal("0.00"),amount);}).toList();
        return new OperationalDreMonthlyReadService.Report(UUID.randomUUID(),"2026-07",true,List.of("FINANCIAL_ENTRIES"),lines);
    }
}
