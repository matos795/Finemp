package com.finemp.sales.indicators;

import java.math.BigDecimal;

public record SalesForecastBucket(
        String month,
        BigDecimal nominal,
        BigDecimal weighted,
        BigDecimal annualized,
        BigDecimal weightedAnnualized) {
}
