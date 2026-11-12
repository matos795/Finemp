package com.finemp.sales.indicators;

import java.util.List;

public record SalesIndicatorsResponse(
        int wonCount,
        int lostCount,
        int openCount,
        String wonNominalValue,
        String averageTicket,
        String conversionRate,
        String averageCycleDays,
        String openPipelineNominal,
        String openPipelineWeighted,
        String openPipelineAnnualized,
        String openPipelineWeightedAnnualized,
        List<SalesForecastBucketResponse> forecast) {

    public SalesIndicatorsResponse {
        forecast = List.copyOf(forecast);
    }

    public static SalesIndicatorsResponse from(
            SalesIndicators indicators) {

        return new SalesIndicatorsResponse(
                indicators.wonCount(),
                indicators.lostCount(),
                indicators.openCount(),
                text(indicators.wonNominalValue()),
                text(indicators.averageTicket()),
                text(indicators.conversionRate()),
                text(indicators.averageCycleDays()),
                text(indicators.openPipelineNominal()),
                text(indicators.openPipelineWeighted()),
                text(indicators.openPipelineAnnualized()),
                text(indicators.openPipelineWeightedAnnualized()),
                indicators.forecast().stream()
                        .map(SalesForecastBucketResponse::from)
                        .toList());
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
