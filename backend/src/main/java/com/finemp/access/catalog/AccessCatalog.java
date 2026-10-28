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
        return new AccessCatalog("2026-10-27.5", Set.of(PlatformPermission.values()),
                Set.of(AccountingFirmPermission.values()), Set.of(CompanyPermission.values()), List.of(
                new ScreenPreset("accounting-ledger", "Razão contábil", true, false,
                        Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW), Set.of(), null),
                new ScreenPreset("accounting-dre", "DRE contábil", true, false,
                        Set.of(CompanyPermission.DRE_VIEW), Set.of(), null),
                new ScreenPreset("account-classification", "Classificação gerencial", true, true,
                        Set.of(CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW),
                        Set.of(CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE), null),
                new ScreenPreset("questor-import", "Importação Questor", false, true,
                        Set.of(), Set.of(CompanyPermission.QUESTOR_SYNC), "QUESTOR_INTEGRATION_ENABLED"),
                new ScreenPreset("sync-history", "Controle e histórico de sincronização", true, true,
                        Set.of(CompanyPermission.QUESTOR_SYNC), Set.of(CompanyPermission.QUESTOR_SYNC), "SYNC_CONTROL_ENABLED"),
                new ScreenPreset("financial-bank-accounts", "Contas financeiras", true, true,
                        Set.of(CompanyPermission.BANK_ACCOUNT_VIEW),
                        Set.of(CompanyPermission.BANK_ACCOUNT_VIEW, CompanyPermission.BANK_ACCOUNT_MANAGE), "FINANCE_CATALOG_ENABLED"),
                new ScreenPreset("business-partners", "Clientes e fornecedores comerciais", true, true,
                        Set.of(CompanyPermission.BUSINESS_PARTNER_VIEW),
                        Set.of(CompanyPermission.BUSINESS_PARTNER_VIEW, CompanyPermission.BUSINESS_PARTNER_MANAGE), "FINANCE_CATALOG_ENABLED"),
                new ScreenPreset("result-centers", "Centros de resultado", true, true,
                        Set.of(CompanyPermission.RESULT_CENTER_VIEW),
                        Set.of(CompanyPermission.RESULT_CENTER_VIEW, CompanyPermission.RESULT_CENTER_MANAGE), "FINANCE_CATALOG_ENABLED"),
                new ScreenPreset("financial-categories", "Categorias financeiras", true, true,
                        Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW),
                        Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW, CompanyPermission.FINANCIAL_CATEGORY_MANAGE), "FINANCE_CATALOG_ENABLED")));
    }
}
