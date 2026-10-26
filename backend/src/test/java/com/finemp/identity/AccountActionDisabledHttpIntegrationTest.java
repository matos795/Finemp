package com.finemp.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "finemp.identity.mail.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountActionDisabledHttpIntegrationTest {
    @Autowired private MockMvc mvc;

    @Test
    void shouldNotExposeRecoveryWhenMailDeliveryIsDisabled() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"synthetic@example.invalid\"}"))
                .andExpect(status().isNotFound());
    }
}
