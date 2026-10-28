package com.finemp.security;

import org.springframework.context.annotation.Bean;
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

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        SecurityContextRepository securityContextRepository,
                        SessionAccountStateValidator sessionAccountStateValidator,
                        QuestorImportAuthorizationManager questorImportAuthorizationManager,
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

                return http.build();
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
