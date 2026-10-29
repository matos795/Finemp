package com.finemp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.access.CompanyPermissionAuthorizationService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import com.finemp.access.CompanyPermission;
import com.finemp.finance.entry.EntryCommandAuthorizationManager;
import com.finemp.finance.entry.FinancialEntryPayloadLimitFilter;
import com.finemp.finance.transfer.BankTransferCommandAuthorizationManager;
import com.finemp.finance.transfer.BankTransferPayloadLimitFilter;
import com.finemp.finance.period.PeriodCommandAuthorizationManager;
import com.finemp.finance.period.PeriodPayloadLimitFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        SecurityContextRepository securityContextRepository,
                        SessionAccountStateValidator sessionAccountStateValidator,
                        QuestorImportAuthorizationManager questorImportAuthorizationManager,
                        EntryCommandAuthorizationManager financialCommands,
                        BankTransferCommandAuthorizationManager transferCommands,
                        @org.springframework.beans.factory.annotation.Qualifier("periodCloseAuthorizationManager") PeriodCommandAuthorizationManager periodClose,
                        @org.springframework.beans.factory.annotation.Qualifier("periodReopenAuthorizationManager") PeriodCommandAuthorizationManager periodReopen,
                        CompanyPermissionAuthorizationManagerFactory permissionAuthorizationManagerFactory)
                        throws Exception {

                http
                                .addFilterAfter(new SessionCredentialsFilter(sessionAccountStateValidator),
                                                SecurityContextHolderFilter.class)
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/csrf",
                                                                "/api/auth/activate",
                                                                "/api/auth/password-reset/request",
                                                                "/api/auth/password-reset/complete",
                                                                "/actuator/health")
                                                .permitAll()

                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/accounting-sources/{accountingSourceId}/questor-import")
                                                .access(questorImportAuthorizationManager)

                                                .requestMatchers(
                                                                "/api/accounting-sources/{accountingSourceId}/sync-runs/**")
                                                .access(questorImportAuthorizationManager)

                                                .requestMatchers(HttpMethod.PUT,
                                                                "/api/companies/{companyId}/accounting-accounts/{accountId}/classification")
                                                .access(permissionAuthorizationManagerFactory.forPermission(
                                                                CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE))

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/accounting-movements")
                                                .access(permissionAuthorizationManagerFactory.forPermission(
                                                                CompanyPermission.ACCOUNTING_MOVEMENT_VIEW))

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/accounting-accounts/classifications")
                                                .access(org.springframework.security.authorization.AuthorizationManagers.anyOf(
                                                                permissionAuthorizationManagerFactory.forPermission(
                                                                                CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW),
                                                                permissionAuthorizationManagerFactory.forPermission(
                                                                                CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE)))

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/dre")
                                                .access(
                                                                permissionAuthorizationManagerFactory
                                                                                .forPermission(
                                                                                                CompanyPermission.DRE_VIEW))

                                                // Financial services enforce fresh company permissions and ownership.
                                                // Keep this route explicit: unrelated company paths remain denied.
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/financial-reports/open-titles/aging")
                                                .authenticated()

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/statement-items/{statementId}/candidates")
                                                .authenticated()

                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/companies/{companyId}/bank-accounts/{bankId}/statement-previews",
                                                                "/api/companies/{companyId}/bank-accounts/{bankId}/statement-imports")
                                                .authenticated()

                                                .requestMatchers(
                                                                "/api/companies/{companyId}/bank-accounts",
                                                                "/api/companies/{companyId}/bank-accounts/{bankId}")
                                                .authenticated()

                                                .requestMatchers(
                                                                "/api/companies/{companyId}/business-partners",
                                                                "/api/companies/{companyId}/business-partners/{partnerId}")
                                                .authenticated()

                                                .requestMatchers(
                                                                "/api/companies/{companyId}/result-centers",
                                                                "/api/companies/{companyId}/result-centers/{centerId}")
                                                .authenticated()

                                                .requestMatchers(
                                                                "/api/companies/{companyId}/financial-categories",
                                                                "/api/companies/{companyId}/financial-categories/{categoryId}",
                                                                "/api/companies/{companyId}/financial-categories/{groupId}/subcategories")
                                                .authenticated()

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/bank-transfers",
                                                                "/api/companies/{companyId}/bank-transfers/{transferId}")
                                                .authenticated()

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/financial-entries",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/withholdings",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/withholdings/{id}",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/withholdings/{id}/history")
                                                .authenticated()

                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/companies/{companyId}/financial-periods",
                                                                "/api/companies/{companyId}/financial-periods/{period}",
                                                                "/api/companies/{companyId}/financial-periods/{period}/history")
                                                .authenticated()

                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/companies/{companyId}/financial-periods/{period}/closures")
                                                .access(periodClose)

                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/companies/{companyId}/financial-periods/{period}/reopenings")
                                                .access(periodReopen)

                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/companies/{companyId}/bank-transfers")
                                                .access(transferCommands)

                                                .requestMatchers(HttpMethod.DELETE,
                                                                "/api/companies/{companyId}/bank-transfers/{transferId}")
                                                .access(transferCommands)

                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/companies/{companyId}/financial-entries",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/withholdings",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/withholdings/{id}/deletions",
                                                                "/api/companies/{companyId}/financial-entry-series/monthly",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/settlements",
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/settlement-reversals")
                                                .access(financialCommands)

                                                .requestMatchers(HttpMethod.PUT,
                                                                "/api/companies/{companyId}/financial-entries/{entryId}")
                                                .access(financialCommands)

                                                .requestMatchers(HttpMethod.PUT,
                                                                "/api/companies/{companyId}/financial-entries/{entryId}/withholdings/{id}")
                                                .access(financialCommands)

                                                .requestMatchers(HttpMethod.DELETE,
                                                                "/api/companies/{companyId}/financial-entries/{entryId}")
                                                .access(financialCommands)

                                                .requestMatchers(
                                                                "/api/companies/{companyId}/**")
                                                .denyAll()

                                                .anyRequest()
                                                .authenticated())
                                .securityContext(context -> context
                                                .securityContextRepository(
                                                                securityContextRepository))
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(
                                                                new HttpStatusEntryPoint(
                                                                                HttpStatus.UNAUTHORIZED)))
                                .logout(logout -> logout
                                                .logoutUrl("/api/auth/logout")
                                                .logoutSuccessHandler(
                                                                new HttpStatusReturningLogoutSuccessHandler(
                                                                                HttpStatus.NO_CONTENT))
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .deleteCookies("JSESSIONID"));

                if (financialCommands.isEnabled())
                        http.addFilterAfter(new FinancialEntryPayloadLimitFilter(65536, 4), AuthorizationFilter.class);
                if (transferCommands.isEnabled())
                        http.addFilterAfter(new BankTransferPayloadLimitFilter(65536, 4), AuthorizationFilter.class);
                if (periodClose.isEnabled())
                        http.addFilterAfter(new PeriodPayloadLimitFilter(65536, 4), AuthorizationFilter.class);
                return http.build();
        }

        @Bean
        EntryCommandAuthorizationManager financialEntryCommandAuthorizationManager(
                        CompanyPermissionAuthorizationService permissions,
                        @Value("${finemp.finance.entries.enabled:false}") boolean entriesEnabled,
                        @Value("${finemp.finance.entries.commands.enabled:false}") boolean commandsEnabled) {
                return new EntryCommandAuthorizationManager(permissions, entriesEnabled, commandsEnabled);
        }

        @Bean
        BankTransferCommandAuthorizationManager bankTransferCommandAuthorizationManager(
                        CompanyPermissionAuthorizationService permissions,
                        @Value("${finemp.finance.transfers.enabled:false}") boolean transfersEnabled,
                        @Value("${finemp.finance.transfers.commands.enabled:false}") boolean commandsEnabled) {
                return new BankTransferCommandAuthorizationManager(permissions, transfersEnabled, commandsEnabled);
        }

        @Bean
        PeriodCommandAuthorizationManager periodCloseAuthorizationManager(CompanyPermissionAuthorizationService permissions,
                        @Value("${finemp.finance.periods.enabled:false}") boolean enabled,
                        @Value("${finemp.finance.periods.commands.enabled:false}") boolean commandsEnabled) {
                return new PeriodCommandAuthorizationManager(permissions,CompanyPermission.FINANCIAL_PERIOD_CLOSE,enabled,commandsEnabled);
        }

        @Bean
        PeriodCommandAuthorizationManager periodReopenAuthorizationManager(CompanyPermissionAuthorizationService permissions,
                        @Value("${finemp.finance.periods.enabled:false}") boolean enabled,
                        @Value("${finemp.finance.periods.commands.enabled:false}") boolean commandsEnabled) {
                return new PeriodCommandAuthorizationManager(permissions,CompanyPermission.FINANCIAL_PERIOD_REOPEN,enabled,commandsEnabled);
        }

        @Bean
        QuestorImportAuthorizationManager questorImportAuthorizationManager(
                        AccountingSourceRepository sources, CompanyPermissionAuthorizationService permissions) {
                return new QuestorImportAuthorizationManager(sources, permissions);
        }

        @Bean
        AuthenticationManager authenticationManager(
                        UserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {

                DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);

                authenticationProvider.setPasswordEncoder(passwordEncoder);

                return new ProviderManager(authenticationProvider);
        }

        @Bean
        SecurityContextRepository securityContextRepository() {
                return new HttpSessionSecurityContextRepository();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
                return PasswordEncoderFactories
                                .createDelegatingPasswordEncoder();
        }
}
