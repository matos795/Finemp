package com.finemp.security;

import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AccountingFileImportSecurityConfiguration {

    @Bean
    AccountingFileImportAuthorizationManager accountingFileImportAuthorizationManager(
            AccountingSourceRepository sources,
            CompanyPermissionAuthorizationService permissions) {
        return new AccountingFileImportAuthorizationManager(sources, permissions);
    }
}
