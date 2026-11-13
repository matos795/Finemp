package com.finemp.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Embeddable
public record OpportunityValues(
        @Column(nullable = false, length = 180)
        String title,
        @Column(nullable = false, length = 80)
        String stage,
        @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 2)
        BigDecimal monthlyAmount,
        @Column(nullable = false)
        int probability,
        @Column(nullable = false, length = 160)
        String responsible,
        @Column(name = "expected_close_on")
        LocalDate expectedCloseOn,
        @Column(columnDefinition = "text")
        String notes) {

    public OpportunityValues {
        title = text(title, 180, 2);
        stage = text(stage, 80, 1);
        monthlyAmount = money(monthlyAmount);
        if (probability < 0 || probability > 100) throw invalid();
        responsible = text(responsible, 160, 1);
        if (notes != null && notes.length() > 4000) throw invalid();
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    private static String text(String value, int max, int min) {
        if (value == null || value.length() > max) throw invalid();
        String normalized = value.trim();
        if (normalized.length() < min) throw invalid();
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw invalid();
        try {
            var compact = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
            if (compact.scale() > 2 || (long) compact.precision() - compact.scale() > 17) throw invalid();
            return compact.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Estado da oportunidade inválido.");
    }
}
