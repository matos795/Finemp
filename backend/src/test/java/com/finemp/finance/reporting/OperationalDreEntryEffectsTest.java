package com.finemp.finance.reporting;

import com.finemp.finance.category.*;
import com.finemp.finance.entry.FinancialEntryValues;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperationalDreEntryEffectsTest {
    @Test void nominalUsesDirectionAndCompetenceWhileSettlementUsesPaymentMonth(){
        for(var direction:FinancialDirection.values()){
            var values=entry(direction,"100.00","101.23",LocalDate.of(2027,1,5));
            var line=direction==FinancialDirection.INCOME?OperationalDreLine.REC:OperationalDreLine.DADM;
            var effects=OperationalDreEntryEffects.calculate(values,line);assertThat(effects).hasSize(2);
            var nominal=effects.getFirst();assertThat(nominal.period()).isEqualTo(YearMonth.of(2026,12));assertThat(nominal.line()).isEqualTo(line);assertThat(nominal.categoryId()).isEqualTo(values.categoryId());
            assertThat(nominal.source()).isEqualTo(OperationalDreEntryEffects.Source.COMPETENCE);
            assertThat(nominal.amount()).isEqualByComparingTo(direction==FinancialDirection.INCOME?"100.00":"-100.00");
            var delta=effects.getLast();assertThat(delta.period()).isEqualTo(YearMonth.of(2027,1));assertThat(delta.source()).isEqualTo(OperationalDreEntryEffects.Source.SETTLEMENT_DIFFERENCE);
            assertThat(delta.line()).isEqualTo(direction==FinancialDirection.INCOME?OperationalDreLine.RFIN:OperationalDreLine.DFIN);
            assertThat(delta.amount()).isEqualByComparingTo(direction==FinancialDirection.INCOME?"1.23":"-1.23");
        }
    }
    @Test void discountsReverseTheFinancialEffectAndOneCentIsNotDiscarded(){
        for(var direction:FinancialDirection.values()){
            var effect=OperationalDreEntryEffects.calculate(entry(direction,"100.00","99.99",LocalDate.of(2027,1,1)),OperationalDreLine.OUT).getLast();
            assertThat(effect.line()).isEqualTo(direction==FinancialDirection.INCOME?OperationalDreLine.DFIN:OperationalDreLine.RFIN);
            assertThat(effect.amount()).isEqualByComparingTo(direction==FinancialDirection.INCOME?"-0.01":"0.01");
        }
    }
    @Test void absentOrEqualEffectiveAmountProducesOnlyTheNominalEffect(){
        assertThat(OperationalDreEntryEffects.calculate(entry(FinancialDirection.INCOME,"1.23",null,null),OperationalDreLine.REC)).hasSize(1);
        assertThat(OperationalDreEntryEffects.calculate(entry(FinancialDirection.INCOME,"1.23",null,LocalDate.of(2027,1,1)),OperationalDreLine.REC)).hasSize(1);
        assertThat(OperationalDreEntryEffects.calculate(entry(FinancialDirection.INCOME,"1.23","1.23",LocalDate.of(2027,1,1)),OperationalDreLine.REC)).hasSize(1);
    }
    @Test void canonicalZeroAndLargeAmountsRemainExactWithoutDoubleArithmetic(){
        var zero=OperationalDreEntryEffects.calculate(entry(FinancialDirection.EXPENSE,"0.00","0.01",LocalDate.of(2027,1,1)),OperationalDreLine.DADM);
        assertThat(zero.getFirst().amount()).isEqualTo(new BigDecimal("0.00"));assertThat(zero.getLast().amount()).isEqualTo(new BigDecimal("-0.01"));
        var large=OperationalDreEntryEffects.calculate(entry(FinancialDirection.INCOME,"99999999999999999.98","99999999999999999.99",LocalDate.of(2027,1,1)),OperationalDreLine.REC);
        assertThat(large.getLast().amount()).isEqualTo(new BigDecimal("0.01"));
    }
    @Test void naoPreservesObservedExclusionWithoutInventingTreatmentOfItsInterest(){
        assertThat(OperationalDreEntryEffects.calculate(entry(FinancialDirection.INCOME,"100.00","101.00",LocalDate.of(2027,1,1)),OperationalDreLine.NAO)).isEmpty();
    }
    @Test void inputsMustBeExplicitAndResultCannotBeMutated(){
        var value=entry(FinancialDirection.INCOME,"1.00",null,null);
        assertThatThrownBy(()->OperationalDreEntryEffects.calculate(null,OperationalDreLine.REC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->OperationalDreEntryEffects.calculate(value,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->OperationalDreEntryEffects.calculate(value,OperationalDreLine.REC).clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    private FinancialEntryValues entry(FinancialDirection direction,String nominal,String actual,LocalDate payment){
        var date=LocalDate.of(2026,11,1);
        return new FinancialEntryValues(direction,"Synthetic DRE entry",new BigDecimal(nominal),date,date,YearMonth.of(2026,12),date,payment,actual==null?null:new BigDecimal(actual),UUID.randomUUID(),UUID.randomUUID(),null,null,null,"","","");
    }
}
