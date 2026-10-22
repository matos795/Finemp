package com.finemp.security;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.dre.DreCalculationResult;
import com.finemp.dre.DreCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompanyAccessHttpIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserAccountRepository userAccountRepository;

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private CompanyAccessGrantRepository companyAccessGrantRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @MockitoBean
        private DreCalculationService dreCalculationService;

        @Test
        void shouldAllowAuthorizedCompanyAndRejectAnotherCompany()
                        throws Exception {

                UserAccount userAccount = userAccountRepository.saveAndFlush(
                                new UserAccount(
                                                "Usuário Exemplo",
                                                "usuario@example.com",
                                                passwordEncoder.encode("senha-segura")));

                Company allowedCompany = companyRepository.saveAndFlush(
                                new Company(
                                                "Empresa Permitida Ltda",
                                                "11111111000111"));

                Company deniedCompany = companyRepository.saveAndFlush(
                                new Company(
                                                "Empresa Bloqueada Ltda",
                                                "22222222000122"));

                companyAccessGrantRepository.saveAndFlush(
                                new CompanyAccessGrant(
                                                userAccount,
                                                allowedCompany,
                                                CompanyAccessRole.CLIENT,
                                                CostCenterScope.ALL,
                                                Set.of(CompanyPermission.DRE_VIEW)));

                MockHttpSession session = login("usuario@example.com", "senha-segura");

                LocalDate startDate = LocalDate.of(2026, 7, 1);

                LocalDate endDate = LocalDate.of(2026, 7, 31);

                when(dreCalculationService.calculate(
                                allowedCompany.getId(),
                                startDate,
                                endDate))
                                .thenReturn(
                                                new DreCalculationResult(
                                                                List.of(),
                                                                0));

                mockMvc.perform(
                                get(
                                                "/api/companies/{companyId}/dre",
                                                allowedCompany.getId())
                                                .session(session)
                                                .param("startDate", "2026-07-01")
                                                .param("endDate", "2026-07-31"))
                                .andExpect(status().isOk());

                mockMvc.perform(
                                get(
                                                "/api/companies/{companyId}/dre",
                                                deniedCompany.getId())
                                                .session(session)
                                                .param("startDate", "2026-07-01")
                                                .param("endDate", "2026-07-31"))
                                .andExpect(status().isForbidden());

                verify(dreCalculationService)
                                .calculate(
                                                allowedCompany.getId(),
                                                startDate,
                                                endDate);

                verify(dreCalculationService, never())
                                .calculate(
                                                deniedCompany.getId(),
                                                startDate,
                                                endDate);
        }

        private MockHttpSession login(
                        String email,
                        String password)
                        throws Exception {

                String body = """
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password);

                var result = mockMvc.perform(
                                post("/api/auth/login")
                                                .with(csrf())
                                                .contentType(APPLICATION_JSON)
                                                .content(body))
                                .andExpect(status().isNoContent())
                                .andReturn();

                return (MockHttpSession) result
                                .getRequest()
                                .getSession(false);
        }

        @Test
        void shouldExposeCsrfToken()
                        throws Exception {

                mockMvc.perform(
                                get("/api/auth/csrf"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.headerName")
                                                .isNotEmpty())
                                .andExpect(jsonPath("$.token")
                                                .isNotEmpty());
        }

        @Test
        void shouldRejectDreWhenUserHasCompanyAccessWithoutDrePermission()
                        throws Exception {

                UserAccount userAccount = userAccountRepository.saveAndFlush(
                                new UserAccount(
                                                "Usuário Restrito",
                                                "restrito@example.com",
                                                passwordEncoder.encode(
                                                                "senha-segura")));

                Company company = companyRepository.saveAndFlush(
                                new Company(
                                                "Empresa Restrita Ltda",
                                                "33333333000133"));

                companyAccessGrantRepository.saveAndFlush(
                                new CompanyAccessGrant(
                                                userAccount,
                                                company,
                                                CompanyAccessRole.CLIENT,
                                                CostCenterScope.ALL,
                                                Set.of(
                                                                CompanyPermission.INDICATORS_VIEW)));

                MockHttpSession session = login(
                                "restrito@example.com",
                                "senha-segura");

                mockMvc.perform(
                                get(
                                                "/api/companies/{companyId}/dre",
                                                company.getId())
                                                .session(session)
                                                .param(
                                                                "startDate",
                                                                "2026-07-01")
                                                .param(
                                                                "endDate",
                                                                "2026-07-31"))
                                .andExpect(status().isForbidden());

                verify(
                                dreCalculationService,
                                never())
                                .calculate(
                                                eq(company.getId()),
                                                any(),
                                                any());
        }
}