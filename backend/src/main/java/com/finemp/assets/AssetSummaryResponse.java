package com.finemp.assets;

public record AssetSummaryResponse(
        long activeCount,
        String acquisitionValue,
        String bookValue,
        String monthlyDepreciation) {
}
