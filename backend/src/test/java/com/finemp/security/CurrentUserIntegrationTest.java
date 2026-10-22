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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrentUserIntegrationTest {

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

    @Test
    void shouldReturnCurrentUserAndAllowedCompanies()
            throws Exception {

        UserAccount user = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "Usuário Exemplo",
                        "usuario@example.com",
                        passwordEncoder.encode(
                                "senha-segura")));

        Company allowedCompany = companyRepository.saveAndFlush(
                new Company(
                        "Empresa Permitida Ltda",
                        "11111111000111"));

        companyRepository.saveAndFlush(
                new Company(
                        "Empresa Não Permitida Ltda",
                        "22222222000122"));

        companyAccessGrantRepository.saveAndFlush(
                new CompanyAccessGrant(
                        user,
                        allowedCompany,
                        CompanyAccessRole.CLIENT,
                        CostCenterScope.ALL,
                        Set.of(
                                CompanyPermission.DASHBOARD_VIEW,
                                CompanyPermission.DRE_VIEW,
                                CompanyPermission.INDICATORS_VIEW)));

        MockHttpSession session = login();

        mockMvc.perform(
                get("/api/auth/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name")
                        .value("Usuário Exemplo"))
                .andExpect(jsonPath("$.email")
                        .value("usuario@example.com"))
                .andExpect(jsonPath("$.companies.length()")
                        .value(1))
                .andExpect(jsonPath("$.companies[0].name")
                        .value("Empresa Permitida Ltda"))
                .andExpect(jsonPath("$.companies[0].role")
                        .value("CLIENT"))
                .andExpect(jsonPath(
                        "$.companies[0].permissions")
                        .isArray())
                .andExpect(jsonPath(
                        "$.companies[0].costCenterScope")
                        .value("ALL"));
    }

    private MockHttpSession login() throws Exception {

        var result = mockMvc.perform(
                post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "usuario@example.com",
                                  "password": "senha-segura"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andReturn();

        return (MockHttpSession) result
                .getRequest()
                .getSession(false);
    }
}