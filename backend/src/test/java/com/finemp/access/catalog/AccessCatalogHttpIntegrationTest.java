package com.finemp.access.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccessCatalogHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;

    @Test
    void shouldRequireLoginAndReturnMetadataNotIdentityPermissions() throws Exception {
        mvc.perform(get("/api/auth/access-catalog")).andExpect(status().isUnauthorized());
        var actor = users.saveAndFlush(new UserAccount("Synthetic", UUID.randomUUID() + "@example.invalid", "hash"));
        mvc.perform(get("/api/auth/access-catalog").with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("2026-10-28.2"))
                .andExpect(jsonPath("$.companyScreenPresets.length()").value(10))
                .andExpect(jsonPath("$.companyScreenPresets[9].editAvailabilityCondition").value("FINANCE_ENTRY_COMMANDS_ENABLED"))
                .andExpect(jsonPath("$.companies").doesNotExist()).andExpect(jsonPath("$.userId").doesNotExist());
    }
}
