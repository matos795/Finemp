package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.identity.delivery.AccountActionMailSender;
import com.finemp.security.UserAccountPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "finemp.identity.mail.enabled=true",
        "finemp.identity.mail.from=sender@example.invalid",
        "finemp.identity.mail.frontend-base-uri=https://app.example.invalid"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FirmInvitationHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;
    @MockitoBean private AccountActionMailSender sender;

    @Test
    void shouldRequireAuthenticationAndExplicitUserAdministrationPermission() throws Exception {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Firm", true));
        String body = body("denied-" + UUID.randomUUID() + "@example.invalid");
        mvc.perform(post(route(firm)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        var actor = actor();
        mvc.perform(post(route(firm)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldInviteOnlyWithinTheGrantedFirmAndNeverReturnTheToken() throws Exception {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Allowed", true));
        var other = firms.saveAndFlush(new AccountingFirm("Synthetic Other", true));
        var actor = actor();
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        String email = "http-invite-" + UUID.randomUUID() + "@example.invalid";

        var response = mvc.perform(post(route(firm)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content(body(email)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(response.getContentAsString()).isEmpty();
        mvc.perform(post(route(other)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content(body("other-" + UUID.randomUUID() + "@example.invalid")))
                .andExpect(status().isForbidden());
        mvc.perform(post(route(firm)).with(user(new UserAccountPrincipal(actor)))
                        .contentType(APPLICATION_JSON).content(body(email)))
                .andExpect(status().isForbidden());
    }

    private UserAccount actor() {
        return users.saveAndFlush(new UserAccount("Synthetic Actor", "actor-" + UUID.randomUUID()
                + "@example.invalid", "synthetic-hash"));
    }

    private String route(AccountingFirm firm) {
        return "/api/admin/accounting-firms/" + firm.getId() + "/invitations";
    }

    private String body(String email) {
        return "{\"name\":\"Synthetic Invited\",\"email\":\"" + email + "\",\"permissions\":[]}";
    }
}
