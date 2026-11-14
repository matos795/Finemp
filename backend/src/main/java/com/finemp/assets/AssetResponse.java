package com.finemp.assets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String assetTag,
        String description,
        AssetCategory category,
        String brandModel,
        String serialNumber,
        LocalDate acquisitionDate,
        String acquisitionValue,
        int quantity,
        UUID businessPartnerId,
        String businessPartnerName,
        String documentReference,
        UUID resultCenterId,
        String resultCenterCode,
        String resultCenterName,
        String location,
        String responsible,
        int usefulLifeMonths,
        String residualPercent,
        boolean depreciable,
        LocalDate depreciationStartDate,
        AssetStatus status,
        LocalDate warrantyUntil,
        String notes,
        String monthlyDepreciation,
        String accumulatedDepreciation,
        String bookValue,
        long version) {

    static AssetResponse from(Asset asset, LocalDate asOf) {
        var input = asset.depreciationInput();
        BigDecimal monthly = AssetDepreciationCalculator.monthlyDepreciation(input);
        BigDecimal accumulated = AssetDepreciationCalculator.accumulatedDepreciation(input, asOf);
        BigDecimal book = AssetDepreciationCalculator.bookValue(input, asOf);

        var partner = asset.getBusinessPartner();
        var center = asset.getResultCenter();

        return new AssetResponse(
                asset.getId(),
                asset.getAssetTag(),
                asset.getDescription(),
                asset.getCategory(),
                asset.getBrandModel(),
                asset.getSerialNumber(),
                asset.getAcquisitionDate(),
                money(asset.getAcquisitionValue()),
                asset.getQuantity(),
                partner == null ? null : partner.getId(),
                partner == null ? null : partner.getProfile().name(),
                asset.getDocumentReference(),
                center == null ? null : center.getId(),
                center == null ? null : center.getCode(),
                center == null ? null : center.getName(),
                asset.getLocation(),
                asset.getResponsible(),
                asset.getUsefulLifeMonths(),
                money(asset.getResidualPercent()),
                asset.isDepreciable(),
                asset.getDepreciationStartDate(),
                asset.getStatus(),
                asset.getWarrantyUntil(),
                asset.getNotes(),
                money(monthly),
                money(accumulated),
                money(book),
                asset.getVersion());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
