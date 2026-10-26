package com.finemp.security;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.dre.DreCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationSessionIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserAccountRepository userAccountRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @MockitoBean
        private DreCalculationService dreCalculationService;

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private CompanyAccessGrantRepository companyAccessGrantRepository;

        @Test
        void shouldRotateAnExistingAnonymousSessionAfterLogin() throws Exception {
                userAccountRepository.saveAndFlush(new UserAccount(
                                "Synthetic Rotation", "rotation@example.invalid", passwordEncoder.encode("synthetic123")));
                MockHttpSession anonymous = new MockHttpSession();
                String previousId = anonymous.getId();

                mockMvc.perform(post("/api/auth/login").session(anonymous).with(csrf())
                                .contentType(APPLICATION_JSON)
                                .content("{\"email\":\"rotation@example.invalid\",\"password\":\"synthetic123\"}"))
                                .andExpect(status().isNoContent());

                assertThat(anonymous.getId()).isNotEqualTo(previousId);
        }

        @Test
        void shouldRevokeAnExistingSessionAfterPasswordChange() throws Exception {
                UserAccount user = userAccountRepository.saveAndFlush(new UserAccount(
                                "Synthetic Revocation", "revoke@example.invalid", passwordEncoder.encode("synthetic123")));
                MockHttpSession session = loginSyntheticUser("revoke@example.invalid");
                user.changePassword(passwordEncoder.encode("replacement123"));
                userAccountRepository.saveAndFlush(user);

                mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

                assertThat(session.isInvalid()).isTrue();
        }

        @Test
        void shouldRevokeAnExistingSessionAfterDeactivation() throws Exception {
                UserAccount user = userAccountRepository.saveAndFlush(new UserAccount(
                                "Synthetic Disabled", "disabled@example.invalid", passwordEncoder.encode("synthetic123")));
                MockHttpSession session = loginSyntheticUser("disabled@example.invalid");
                user.deactivate();
                userAccountRepository.saveAndFlush(user);

                mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

                assertThat(session.isInvalid()).isTrue();
        }

        private MockHttpSession loginSyntheticUser(String email) throws Exception {
                var result = mockMvc.perform(post("/api/auth/login").with(csrf())
                                .contentType(APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"synthetic123\"}"))
                                .andExpect(status().isNoContent()).andReturn();
                return (MockHttpSession) result.getRequest().getSession(false);
        }

        @Test
        void shouldCreateAuthenticatedSessionWhenCredentialsAreValid()
                        throws Exception {

                UserAccount userAccount = userAccountRepository.saveAndFlush(
                                new UserAccount(
                                                "Usuário Exemplo",
                                                "usuario@example.com",
                                                passwordEncoder.encode("senha-segura")));

                Company company = companyRepository.saveAndFlush(
                                new Company(
                                                "Empresa Exemplo Ltda",
                                                "11111111000111"));

                companyAccessGrantRepository.saveAndFlush(
                                new CompanyAccessGrant(
                                                userAccount,
                                                company));

                var loginResult = mockMvc.perform(
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

                MockHttpSession session = (MockHttpSession) loginResult.getRequest()
                                .getSession(false);

                assertThat(session).isNotNull();

                mockMvc.perform(
                                get("/api/auth/me")
                                                .session(session))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldReturnUnauthorizedWhenPasswordIsInvalid()
                        throws Exception {

                userAccountRepository.saveAndFlush(
                                new UserAccount(
                                                "Usuário Exemplo",
                                                "usuario@example.com",
                                                passwordEncoder.encode("senha-correta")));

                mockMvc.perform(
                                post("/api/auth/login")
                                                .with(csrf())
                                                .contentType(APPLICATION_JSON)
                                                .content("""
                                                                {
                                                                  "email": "usuario@example.com",
                                                                  "password": "senha-errada"
                                                                }
                                                                """))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturnUnauthorizedWhenUserAccountIsInactive()
                        throws Exception {

                UserAccount userAccount = new UserAccount(
                                "Usuário Exemplo",
                                "usuario@example.com",
                                passwordEncoder.encode("senha-segura"));

                userAccount.deactivate();

                userAccountRepository.saveAndFlush(userAccount);

                mockMvc.perform(
                                post("/api/auth/login")
                                                .with(csrf())
                                                .contentType(APPLICATION_JSON)
                                                .content("""
                                                                {
                                                                  "email": "usuario@example.com",
                                                                  "password": "senha-segura"
                                                                }
                                                                """))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldInvalidateSessionWhenUserLogsOut()
                        throws Exception {

                userAccountRepository.saveAndFlush(
                                new UserAccount(
                                                "Usuário Exemplo",
                                                "usuario@example.com",
                                                passwordEncoder.encode("senha-segura")));

                var loginResult = mockMvc.perform(
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

                MockHttpSession session = (MockHttpSession) loginResult
                                .getRequest()
                                .getSession(false);

                assertThat(session).isNotNull();

                mockMvc.perform(
                                post("/api/auth/logout")
                                                .session(session)
                                                .with(csrf()))
                                .andExpect(status().isNoContent());

                assertThat(session.isInvalid())
                                .isTrue();
        }
}
