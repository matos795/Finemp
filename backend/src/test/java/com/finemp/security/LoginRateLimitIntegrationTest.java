package com.finemp.security;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "finemp.identity.rate-limit.login-pair=1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginRateLimitIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    void shouldReturn429AfterTheConfiguredLimitWithoutDisablingTheAccount() throws Exception {
        users.saveAndFlush(new UserAccount("Synthetic Limit", "limit@example.invalid", passwords.encode("synthetic123")));
        String credentials = "{\"email\":\"limit@example.invalid\",\"password\":\"synthetic123\"}";
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON).content(credentials))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON).content(credentials))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").with(csrf())
                        .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; })
                        .contentType(APPLICATION_JSON).content(credentials))
                .andExpect(status().isNoContent());
    }
}
