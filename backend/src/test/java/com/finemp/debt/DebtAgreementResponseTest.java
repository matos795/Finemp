package com.finemp.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.finemp.company.Company;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebtAgreementResponseTest {

    @Test
    void zeroEffectiveRateIsSerializedWithoutScientificNotation() {
        var company = mock(Company.class);
        var agreement = mock(DebtAgreement.class);
        var companyId = UUID.randomUUID();

        when(company.getId()).thenReturn(companyId);
        when(agreement.getCompany()).thenReturn(company);
        when(agreement.getValues()).thenReturn(new DebtAgreementValues(
                DebtOrigin.TAX,
                "Parcelamento fiscal",
                DebtAmortizationSystem.SAC,
                new BigDecimal("1000.00"),
                10,
                BigDecimal.ZERO,
                DebtRateInputPeriod.MONTHLY,
                null,
                LocalDate.of(2026, 1, 31)));
        when(agreement.getBusinessPartner()).thenReturn(null);

        var response = DebtAgreementResponse.from(agreement);

        assertThat(response.inputRate()).isEqualTo("0");
        assertThat(response.effectiveMonthlyRate()).isEqualTo("0.000000000000");
        assertThat(response.effectiveMonthlyRate()).doesNotContainIgnoringCase("E");
    }
}
