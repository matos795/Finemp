package com.finemp.sales;

import java.time.LocalDate;

public enum SalesActivityDueStatus {
    OVERDUE,
    TODAY,
    UPCOMING,
    COMPLETED;

    public static SalesActivityDueStatus of(
            LocalDate dueOn,
            boolean completed,
            LocalDate today) {
        if (dueOn == null || today == null) {
            throw new IllegalArgumentException("Prazo da atividade inválido.");
        }
        if (completed) return COMPLETED;
        if (dueOn.isBefore(today)) return OVERDUE;
        if (dueOn.isEqual(today)) return TODAY;
        return UPCOMING;
    }
}
