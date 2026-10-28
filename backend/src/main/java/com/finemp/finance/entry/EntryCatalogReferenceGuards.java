package com.finemp.finance.entry;

import com.finemp.finance.bank.BankAccountReferenceGuard;
import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import com.finemp.finance.category.FinancialCategoryUsageGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Separate adapters preserve the meaning of IDs even if different catalogs share the same UUID. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
public class EntryCatalogReferenceGuards {
    @Bean BankAccountReferenceGuard financialEntryBankReferences(FinancialEntryRepository entries) {
        return entries::existsByCompanyIdAndValuesBankAccountId;
    }
    @Bean BusinessPartnerReferenceGuard financialEntryPartnerReferences(FinancialEntryRepository entries) {
        return entries::existsByCompanyIdAndValuesPartnerId;
    }
    @Bean FinancialCategoryUsageGuard financialEntryCategoryReferences(FinancialEntryRepository entries) {
        return entries::hasCategoryOrChildEntries;
    }
}
