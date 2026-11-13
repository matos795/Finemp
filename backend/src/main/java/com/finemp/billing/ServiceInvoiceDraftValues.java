package com.finemp.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Embeddable
public record ServiceInvoiceDraftValues(
        @Column(nullable = false)
        LocalDate competence,
        @Column(name = "service_text", nullable = false, length = 4000)
        String serviceText,
        @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
        BigDecimal grossAmount,
        @Column(name = "iss_rate", nullable = false, precision = 7, scale = 4)
        BigDecimal issRate,
        @Column(name = "iss_withheld", nullable = false)
        boolean issWithheld,
        @Column(name = "ir_rate", nullable = false, precision = 7, scale = 4)
        BigDecimal irRate,
        @Column(name = "pis_rate", nullable = false, precision = 7, scale = 4)
        BigDecimal pisRate,
        @Column(name = "cofins_rate", nullable = false, precision = 7, scale = 4)
        BigDecimal cofinsRate,
        @Column(name = "csll_rate", nullable = false, precision = 7, scale = 4)
        BigDecimal csllRate,
        @Column(name = "inss_rate", nullable = false, precision = 7, scale = 4)
        BigDecimal inssRate) {

    public ServiceInvoiceDraftValues {
        if (competence == null || competence.getDayOfMonth() != 1) throw invalid();
        serviceText = text(serviceText, 4000);
        if (serviceText.length() < 2) throw invalid();

        var calculated = ServiceInvoiceTaxCalculator.calculate(
                grossAmount,
                issRate,
                issWithheld,
                irRate,
                pisRate,
                cofinsRate,
                csllRate,
                inssRate);

        grossAmount = calculated.grossAmount();
        issRate = rate(issRate);
        irRate = rate(irRate);
        pisRate = rate(pisRate);
        cofinsRate = rate(cofinsRate);
        csllRate = rate(csllRate);
        inssRate = rate(inssRate);
    }

    ServiceInvoiceTaxCalculator.Result taxes() {
        return ServiceInvoiceTaxCalculator.calculate(
                grossAmount,
                issRate,
                issWithheld,
                irRate,
                pisRate,
                cofinsRate,
                csllRate,
                inssRate);
    }

    private static BigDecimal rate(BigDecimal value) {
        if (value == null) throw invalid();
        var normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.signum() < 0 || normalized.compareTo(new BigDecimal("100")) > 0 || normalized.scale() > 4) {
            throw invalid();
        }
        return normalized;
    }

    private static String text(String value, int limit) {
        if (value == null || value.length() > limit) throw invalid();
        return value.trim();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Dados da nota fiscal inválidos.");
    }
}
