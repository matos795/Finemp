package com.finemp.access.catalog;

import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.CompanyPermission;
import com.finemp.access.PlatformPermission;
import java.util.List;
import java.util.Set;

public record AccessCatalog(String version, Set<PlatformPermission> platformPermissions,
                            Set<AccountingFirmPermission> accountingFirmPermissions,
                            Set<CompanyPermission> companyPermissions, List<ScreenPreset> companyScreenPresets) {
    public AccessCatalog {
        platformPermissions = Set.copyOf(platformPermissions);
        accountingFirmPermissions = Set.copyOf(accountingFirmPermissions);
        companyPermissions = Set.copyOf(companyPermissions);
        companyScreenPresets = List.copyOf(companyScreenPresets);
    }

    public record ScreenPreset(String id, String label, boolean viewAvailable, boolean editAvailable,
                               Set<CompanyPermission> viewPermissions, Set<CompanyPermission> editPermissions,
                               String availabilityCondition) {
        public ScreenPreset {
            viewPermissions = Set.copyOf(viewPermissions);
            editPermissions = Set.copyOf(editPermissions);
        }
    }

    public static AccessCatalog current() {
        return new AccessCatalog("2026-10-26.1", Set.of(PlatformPermission.values()),
                Set.of(AccountingFirmPermission.values()), Set.of(CompanyPermission.values()), List.of(
                new ScreenPreset("accounting-ledger", "Razão contábil", true, false,
                        Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW), Set.of(), null),
                new ScreenPreset("accounting-dre", "DRE contábil", true, false,
                        Set.of(CompanyPermission.DRE_VIEW), Set.of(), null),
                new ScreenPreset("account-classification", "Classificação gerencial", true, true,
                        Set.of(CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW),
                        Set.of(CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE), null),
                new ScreenPreset("questor-import", "Importação Questor", false, true,
                        Set.of(), Set.of(CompanyPermission.QUESTOR_SYNC), "QUESTOR_INTEGRATION_ENABLED")));
    }
}
