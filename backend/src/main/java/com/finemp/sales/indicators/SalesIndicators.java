package com.finemp.sales.indicators;

import java.math.BigDecimal;
import java.util.List;

public record SalesIndicators(
        int wonCount,
        int lostCount,
        int openCount,
        BigDecimal wonNominalValue,
        BigDecimal averageTicket,
        BigDecimal conversionRate,
        BigDecimal averageCycleDays,
        BigDecimal openPipelineNominal,
        BigDecimal openPipelineWeighted,
        BigDecimal openPipelineAnnualized,
        BigDecimal openPipelineWeightedAnnualized,
        List<SalesForecastBucket> forecast) {

    public SalesIndicators {
        forecast = List.copyOf(forecast);
    }
}
