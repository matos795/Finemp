package com.finemp.finance.entry;

import com.finemp.finance.bank.BankAccountReferenceGuard;
import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import com.finemp.finance.category.FinancialCategoryUsageGuard;
import com.finemp.finance.center.ResultCenterUsageGuard;
import java.util.UUID;
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
    @Bean ResultCenterUsageGuard financialEntryCenterReferences(FinancialEntryRepository entries) {
        return new ResultCenterUsageGuard() {
            @Override public boolean hasEntries(UUID company,UUID center) {return entries.existsByCompanyIdAndValuesResultCenterId(company,center);}
            @Override public boolean hasReferences(UUID company,UUID center) {return hasEntries(company,center);}
        };
    }
}
