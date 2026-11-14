package com.finemp.assets;

import com.finemp.finance.center.ResultCenterUsageGuard;
import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assets keep catalog references addressable even when a catalog record is deactivated. */
@Configuration(proxyBeanMethods = false)
public class AssetCatalogReferenceGuards {

    @Bean
    BusinessPartnerReferenceGuard assetPartnerReferences(
            AssetRepository assets) {
        return assets::existsByCompanyIdAndBusinessPartnerId;
    }

    @Bean
    ResultCenterUsageGuard assetResultCenterReferences(
            AssetRepository assets) {
        return new ResultCenterUsageGuard() {
            @Override
            public boolean hasEntries(UUID companyId, UUID centerId) {
                return false;
            }

            @Override
            public boolean hasReferences(UUID companyId, UUID centerId) {
                return assets.existsByCompanyIdAndResultCenterId(
                        companyId,
                        centerId);
            }
        };
    }
}
