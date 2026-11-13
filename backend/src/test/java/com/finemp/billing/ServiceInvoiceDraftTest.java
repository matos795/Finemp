package com.finemp.billing;

import static org.assertj.core.api.Assertions.*;

import com.finemp.company.Company;
import com.finemp.finance.partner.BusinessPartner;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ServiceInvoiceDraftTest {

    @Test
    void rateMultiplesOfTenKeepPlainDecimalScale() {
        var values = new ServiceInvoiceDraftValues(
                LocalDate.of(2026, 11, 1),
                "Prestação de serviços",
                new BigDecimal("1000.00"),
                new BigDecimal("10.0000"),
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(values.issRate().toPlainString()).isEqualTo("10");
        assertThat(values.issRate().scale()).isZero();
    }

    @Test
    void issuedDraftBecomesImmutableAndKeepsInvoiceNumber() {
        var draft = draft();

        draft.markIssued(" 12345 ");

        assertThat(draft.getStatus()).isEqualTo(ServiceInvoiceStatus.ISSUED);
        assertThat(draft.getInvoiceNumber()).isEqualTo("12345");
        assertThatThrownBy(() -> draft.replace(
                draft.getBusinessPartner(),
                null,
                values()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelIsIdempotentAndClearsInvoiceNumber() {
        var draft = draft();
        draft.markIssued("12345");

        draft.cancel();
        draft.cancel();

        assertThat(draft.getStatus()).isEqualTo(ServiceInvoiceStatus.CANCELED);
        assertThat(draft.getInvoiceNumber()).isEqualTo("12345");
    }

    private static ServiceInvoiceDraft draft() {
        return new ServiceInvoiceDraft(
                org.mockito.Mockito.mock(Company.class),
                org.mockito.Mockito.mock(BusinessPartner.class),
                null,
                values());
    }

    private static ServiceInvoiceDraftValues values() {
        return new ServiceInvoiceDraftValues(
                LocalDate.of(2026, 11, 1),
                "Prestação de serviços",
                new BigDecimal("1000.00"),
                new BigDecimal("5.00"),
                true,
                new BigDecimal("1.50"),
                new BigDecimal("0.65"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO);
    }
}
