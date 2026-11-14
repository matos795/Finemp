package com.finemp.assets;

import com.finemp.finance.center.ResultCenter;
import com.finemp.finance.partner.BusinessPartner;
import java.math.BigDecimal;
import java.time.LocalDate;

record AssetValues(
        String assetTag,
        String description,
        AssetCategory category,
        String brandModel,
        String serialNumber,
        LocalDate acquisitionDate,
        BigDecimal acquisitionValue,
        int quantity,
        BusinessPartner businessPartner,
        String documentReference,
        ResultCenter resultCenter,
        String location,
        String responsible,
        int usefulLifeMonths,
        BigDecimal residualPercent,
        boolean depreciable,
        LocalDate depreciationStartDate,
        AssetStatus status,
        LocalDate warrantyUntil,
        String notes) {
}
