package com.finemp.finance.entry;

import com.finemp.company.Company;
import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialEntryValuesTest {
    private FinancialEntryValues values(BigDecimal nominal,LocalDate paid,BigDecimal actual) {
        return new FinancialEntryValues(FinancialDirection.EXPENSE," Synthetic entry ",nominal,LocalDate.of(2026,1,31),LocalDate.of(2026,2,5),YearMonth.of(2025,12),
                LocalDate.of(2026,1,15),paid,actual,UUID.randomUUID(),UUID.randomUUID(),null,null,null,"","","");
    }
    @Test void canonicalStateMustKeepExactCentsAndAllowZeroGeneratedInstallments() {
        assertThat(values(new BigDecimal("1.2300"),null,null).nominalAmount()).isEqualTo(new BigDecimal("1.23"));
        assertThat(values(BigDecimal.ZERO,null,null).nominalAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(values(new BigDecimal("0e9999999"),null,null).nominalAmount()).isEqualTo(new BigDecimal("0.00"));
    }
    @Test void storageMustRejectSubcentsNegativesNullsAndUnboundedExponentsBeforeAllocation() {
        for(var amount:new BigDecimal[]{new BigDecimal("1.005"),new BigDecimal("-0.01"),new BigDecimal("1e1000000")})
            assertThatThrownBy(()->values(amount,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->values(null,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void paidEffectiveAmountMustUseOverrideOnlyForASettledState() {
        assertThat(values(new BigDecimal("1.00"),null,null).effectiveAmount()).isEqualByComparingTo("1.00");
        assertThat(values(new BigDecimal("1.00"),LocalDate.of(2026,2,1),null).effectiveAmount()).isEqualByComparingTo("1.00");
        assertThat(values(new BigDecimal("1.00"),LocalDate.of(2026,2,1),new BigDecimal("0.90")).effectiveAmount()).isEqualByComparingTo("0.90");
        assertThat(values(new BigDecimal("1.00"),LocalDate.of(2026,2,1),BigDecimal.ZERO).effectiveAmount()).isEqualByComparingTo("0.00");
        assertThatThrownBy(()->values(BigDecimal.ONE,null,new BigDecimal("0.90"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void persistedStateMustRemainSeparateFromChronologyPolicyAndSensitiveDiagnosticOutput() {
        var data=values(new BigDecimal("1.00"),LocalDate.of(2025,1,1),null);
        assertThat(data.entryDate()).isAfter(data.dueDate());assertThat(data.competence()).isEqualTo(YearMonth.of(2025,12));
        assertThat(data.toString()).doesNotContain(data.description(),data.nominalAmount().toPlainString());
        assertThatThrownBy(()->new FinancialEntry(null,data,UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FinancialEntry(mock(Company.class),data,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
