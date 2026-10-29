package com.finemp.finance.withholding;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WithholdingValuesTest {
    @Test void exactCentsMustKeepTheirValueWithoutRoundingIncludingZeroAndTheStorageBoundary() {
        for(var input:java.util.List.of("0","0.000","1.2300","99999999999999999.99")) {
            var result=values(input);assertThat(result.amount()).isEqualByComparingTo(new BigDecimal(input));assertThat(result.amount().scale()).isEqualTo(2);
        }
        for(var invalid:java.util.List.of("0.001","1.005","-0.01","100000000000000000.00","1E+1000"))assertThatThrownBy(()->values(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void treatmentMustBeDeclaredWithoutDerivingItFromTheAmountOrDocument() {
        for(var treatment:WithholdingTreatment.values())assertThat(new WithholdingValues(" IRRF ",treatment,new BigDecimal("1.23")," Doc informado ").treatment()).isEqualTo(treatment);
        assertThatThrownBy(()->new WithholdingValues("IRRF",null,BigDecimal.ONE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThat(values("1.23").toString()).doesNotContain("IRRF");
    }
    @Test void labelsMustRejectInvalidUnicodeAndMissingTaxCodeWithoutInventingATaxCatalogue() {
        for(var code:java.util.List.of(""," ","x".repeat(41),"bad\u0000code","bad\uD800code"))assertThatThrownBy(()->new WithholdingValues(code,WithholdingTreatment.PAYABLE,BigDecimal.ONE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new WithholdingValues("Outro tributo informado",WithholdingTreatment.RECOVERABLE,BigDecimal.ONE,null).documentReference()).isEmpty();
    }
    WithholdingValues values(String amount) {return new WithholdingValues("IRRF",WithholdingTreatment.PAYABLE,new BigDecimal(amount),"");}
}
