package com.finemp.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetCatalogReferenceGuardsTest {

    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetCatalogReferenceGuards configuration =
            new AssetCatalogReferenceGuards();

    @Test
    void partnerReferenceIsCompanyScoped() {
        UUID companyId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();

        when(assets.existsByCompanyIdAndBusinessPartnerId(
                companyId,
                partnerId))
                .thenReturn(true);

        var guard = configuration.assetPartnerReferences(assets);

        assertThat(guard.hasReferences(companyId, partnerId)).isTrue();
        verify(assets).existsByCompanyIdAndBusinessPartnerId(
                companyId,
                partnerId);
    }

    @Test
    void resultCenterReferenceDoesNotPretendToBeFinancialEntry() {
        UUID companyId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();

        when(assets.existsByCompanyIdAndResultCenterId(
                companyId,
                centerId))
                .thenReturn(true);

        var guard = configuration.assetResultCenterReferences(assets);

        assertThat(guard.hasEntries(companyId, centerId)).isFalse();
        assertThat(guard.hasReferences(companyId, centerId)).isTrue();
        verify(assets).existsByCompanyIdAndResultCenterId(
                companyId,
                centerId);
    }
}
