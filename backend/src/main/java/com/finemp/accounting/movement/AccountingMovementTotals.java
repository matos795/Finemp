package com.finemp.accounting.movement;

import java.math.BigDecimal;

/** Somas exatas do filtro, na escala armazenada. */
public record AccountingMovementTotals(BigDecimal debit, BigDecimal credit) { }
