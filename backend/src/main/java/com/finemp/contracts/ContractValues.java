package com.finemp.contracts;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Embeddable
public record ContractValues(
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        ContractDirection direction,
        @Column(nullable = false, length = 255)
        String description,
        @Column(name = "start_date", nullable = false)
        LocalDate startDate,
        @Column(name = "end_date")
        LocalDate endDate,
        @Column(name = "billing_day", nullable = false)
        int billingDay,
        @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 2)
        BigDecimal monthlyAmount,
        @Column(nullable = false)
        boolean active) {

    public ContractValues {
        if (direction == null || startDate == null) throw invalid();
        description = text(description, 255);
        if (description.length() < 2) throw invalid();
        if (endDate != null && endDate.isBefore(startDate)) throw invalid();
        if (billingDay < 1 || billingDay > 31) throw invalid();
        monthlyAmount = money(monthlyAmount);
    }

    private static String text(String value, int max) {
        if (value == null || value.length() > max) throw invalid();
        return value.trim();
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
        return new IllegalArgumentException("Estado do contrato inválido.");
    }
}
