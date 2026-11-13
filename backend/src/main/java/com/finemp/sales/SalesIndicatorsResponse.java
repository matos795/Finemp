package com.finemp.sales;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record SalesIndicatorsResponse(
        long wonCount,
        long lostCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal conversionRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageWonTicket,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageSalesCycleDays,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal weightedOpenPipeline,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal annualizedWonValue,
        List<ForecastMonth> forecast) {

    public SalesIndicatorsResponse {
        forecast = List.copyOf(forecast);
    }

    public record ForecastMonth(
            String month,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal weightedAmount) {
        static ForecastMonth of(YearMonth month, BigDecimal amount) {
            return new ForecastMonth(month.toString(), amount);
        }
    }
}
