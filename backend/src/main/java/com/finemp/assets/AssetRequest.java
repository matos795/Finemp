package com.finemp.assets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AssetRequest(
        @Size(max = 30) String assetTag,
        @NotBlank @Size(min = 2, max = 150) String description,
        @NotNull AssetCategory category,
        @Size(max = 80) String brandModel,
        @Size(max = 60) String serialNumber,
        @NotNull LocalDate acquisitionDate,
        @NotBlank String acquisitionValue,
        Integer quantity,
        UUID businessPartnerId,
        @Size(max = 60) String documentReference,
        UUID resultCenterId,
        @Size(max = 80) String location,
        @Size(max = 120) String responsible,
        Integer usefulLifeMonths,
        String residualPercent,
        Boolean depreciable,
        LocalDate depreciationStartDate,
        AssetStatus status,
        LocalDate warrantyUntil,
        @Size(max = 800) String notes,
        Long expectedVersion) {
}
