package com.finemp.sales.indicators;

public record SalesForecastBucketResponse(
        String month,
        String nominal,
        String weighted,
        String annualized,
        String weightedAnnualized) {

    public static SalesForecastBucketResponse from(
            SalesForecastBucket bucket) {

        return new SalesForecastBucketResponse(
                bucket.month(),
                bucket.nominal().toPlainString(),
                bucket.weighted().toPlainString(),
                bucket.annualized().toPlainString(),
                bucket.weightedAnnualized().toPlainString());
    }
}
