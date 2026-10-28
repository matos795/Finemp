package com.finemp.access.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.access.CompanyPermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AccessCatalogTest {
    @Test
    void shouldExposeOnlyImplementedScreenPresetsWithoutInferringFinancialAccessFromAdministration() {
        var catalog = AccessCatalog.current();
        assertThat(catalog.companyScreenPresets()).extracting(AccessCatalog.ScreenPreset::id)
                .containsExactly("accounting-ledger", "accounting-dre", "account-classification", "questor-import", "sync-history", "financial-bank-accounts", "business-partners", "result-centers", "financial-categories", "financial-entries");
        var dre = catalog.companyScreenPresets().get(1);
        assertThat(dre.viewPermissions()).containsExactly(CompanyPermission.DRE_VIEW);
        assertThat(dre.editAvailable()).isFalse();
        assertThat(dre.editPermissions()).isEmpty();
        var classification = catalog.companyScreenPresets().get(2);
        assertThat(classification.editPermissions()).containsAll(classification.viewPermissions());
        assertThat(classification.editPermissions()).contains(CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE);
        var sync = catalog.companyScreenPresets().get(3);
        assertThat(sync.viewAvailable()).isFalse();
        assertThat(sync.availabilityCondition()).isEqualTo("QUESTOR_INTEGRATION_ENABLED");
        var history = catalog.companyScreenPresets().get(4);
        assertThat(history.availabilityCondition()).isEqualTo("SYNC_CONTROL_ENABLED");
        assertThat(history.viewPermissions()).containsExactly(CompanyPermission.QUESTOR_SYNC);
    }

    @Test
    void shouldReturnImmutableMetadataAndNeverUseItAsUserGrants() {
        var catalog = AccessCatalog.current();
        assertThatThrownBy(() -> catalog.companyPermissions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.companyScreenPresets().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(catalog.companyScreenPresets()).allSatisfy(preset -> {
            assertThat(catalog.companyPermissions()).containsAll(preset.viewPermissions()).containsAll(preset.editPermissions());
            if (!preset.viewAvailable()) assertThat(preset.viewPermissions()).isEmpty();
            if (!preset.editAvailable()) assertThat(preset.editPermissions()).isEmpty();
        });
        var empty = new AccessCatalog.ScreenPreset("empty", "Empty", false, false, Set.of(), Set.of(), null);
        assertThat(empty.viewPermissions()).isEmpty();
    }

    @Test
    void shouldConfigureSecureHttpOnlySameSiteCookiesInProductionProfile() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        var properties = yaml.getObject();
        assertThat(properties.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(properties.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(properties.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
    }
}
