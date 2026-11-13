package com.finemp.debt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DebtAgreementTest {

    @Test
    void bankDebtRequiresPartnerAndGeneratesOfficialSchedule() {
        var values = sac(DebtOrigin.BANK, "Capital de giro", "1000.00", 3, "3.00");

        var agreement = new DebtAgreement(
                mock(Company.class),
                mock(BusinessPartner.class),
                values);

        assertThat(agreement.getInstallments()).hasSize(3);
        assertThat(agreement.getInstallments().getFirst().getAmortizationAmount())
                .isEqualByComparingTo("333.33");
        assertThat(agreement.getInstallments().getFirst().getInterestAmount())
                .isEqualByComparingTo("30.00");
        assertThat(agreement.getInstallments().getLast().getBalanceAfter())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void taxDebtDoesNotRequireBusinessPartner() {
        var agreement = new DebtAgreement(
                mock(Company.class),
                null,
                sac(DebtOrigin.TAX, "Parcelamento fiscal", "300.00", 3, "0"));

        assertThat(agreement.getBusinessPartner()).isNull();
        assertThat(agreement.getInstallments()).hasSize(3);
    }

    @Test
    void nonTaxDebtRejectsMissingPartner() {
        assertThatThrownBy(() -> new DebtAgreement(
                mock(Company.class),
                null,
                sac(DebtOrigin.SUPPLIER, "Fornecedor", "300.00", 3, "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taxDebtRejectsPartnerToAvoidAmbiguousCreditorModel() {
        assertThatThrownBy(() -> new DebtAgreement(
                mock(Company.class),
                mock(BusinessPartner.class),
                sac(DebtOrigin.TAX, "Parcelamento fiscal", "300.00", 3, "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacingAgreementRegeneratesPlannedInstallments() {
        var agreement = new DebtAgreement(
                mock(Company.class),
                mock(BusinessPartner.class),
                sac(DebtOrigin.BANK, "Original", "1000.00", 3, "3"));

        agreement.replace(
                agreement.getBusinessPartner(),
                sac(DebtOrigin.BANK, "Renegociada", "600.00", 2, "0"));

        assertThat(agreement.getValues().description()).isEqualTo("Renegociada");
        assertThat(agreement.getInstallments()).hasSize(2);
        assertThat(agreement.getInstallments().getFirst().getAmortizationAmount())
                .isEqualByComparingTo("300.00");
        assertThat(agreement.getInstallments().getLast().getBalanceAfter())
                .isEqualByComparingTo("0.00");
    }

    private static DebtAgreementValues sac(
            DebtOrigin origin,
            String description,
            String principal,
            int count,
            String monthlyRate) {
        return new DebtAgreementValues(
                origin,
                description,
                DebtAmortizationSystem.SAC,
                new BigDecimal(principal),
                count,
                new BigDecimal(monthlyRate),
                DebtRateInputPeriod.MONTHLY,
                null,
                LocalDate.of(2026, 1, 31));
    }
}
