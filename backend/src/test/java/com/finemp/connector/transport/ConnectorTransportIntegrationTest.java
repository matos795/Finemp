package com.finemp.connector.transport;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.source.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.connector.*;
import com.finemp.identity.OpaqueAccountToken;
import com.finemp.security.UserAccountPrincipal;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;

@SpringBootTest(properties = {"finemp.connector.enabled=true", "finemp.connector.protocol.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional(isolation = Isolation.READ_COMMITTED)
class ConnectorTransportIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ConnectorCredentialService service;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingFirmAccessGrantRepository memberships;
    @Autowired private EntityManager entities;
    private UserAccount actor;
    private UUID firmId, sourceId;
    private ConnectorSecretResponse issued;
    private final String route = "/api/connector/v1/session";
    @BeforeEach void prepare() {
        actor = users.saveAndFlush(new UserAccount("Synthetic Transport Admin", UUID.randomUUID() + "@example.invalid", "hash"));
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Transport Firm", true)); firmId = firm.getId();
        var client = clients.saveAndFlush(new Client(firm, "Synthetic Transport Client", true));
        var company = companies.saveAndFlush(new Company(client, "Synthetic Transport Company", "46464646000146"));
        sourceId = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true)).getId();
        memberships.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        issued = service.issue(actor.getId(), firmId, new CreateConnectorCredentialRequest(sourceId, "Synthetic Transport"));
    }
    @Test void shouldAuthenticateWithTlsBearerOnlyWithoutCreatingSessionOrExposingUserIdentityOrSecret() throws Exception {
        var result = mvc.perform(get(route).secure(true).header("Authorization", "Bearer " + issued.secret()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.credentialId").value(issued.credential().id().toString()))
                .andExpect(jsonPath("$.accountingFirmId").value(firmId.toString()))
                .andExpect(jsonPath("$.accountingSourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.issuedByUserId").doesNotExist()).andExpect(jsonPath("$.secret").doesNotExist()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(issued.secret(), OpaqueAccountToken.hash(issued.secret()));
        mvc.perform(post(route).secure(true).header("Authorization", "Bearer " + issued.secret()))
                .andExpect(status().isMethodNotAllowed()); // Auth passed without browser CSRF; no write handler exists.
    }
    @Test void shouldRejectBrowserOnlyPlainHttpSpoofedForwardingAndQueryTokens() throws Exception {
        mvc.perform(get(route).secure(true).with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CONNECTOR_UNAUTHORIZED"));
        mvc.perform(get(route).header("Authorization", "Bearer " + issued.secret()).header("X-Forwarded-Proto", "https"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(route + "?access_token=synthetic").secure(true).header("Authorization", "Bearer " + issued.secret()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(route).secure(true).header("Authorization", "Bearer " + OpaqueAccountToken.generate().rawValue()))
                .andExpect(status().isUnauthorized());
    }
    @Test void shouldUseTheBoundCredentialEvenWhenABrowserContextIsAlsoPresentAndNeverWeakenBrowserCsrf() throws Exception {
        var stranger = users.saveAndFlush(new UserAccount("Synthetic Other Browser", UUID.randomUUID() + "@example.invalid", "hash"));
        mvc.perform(get(route).secure(true).with(user(new UserAccountPrincipal(stranger))).header("Authorization", "Bearer " + issued.secret()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountingSourceId").value(sourceId.toString()));
        mvc.perform(post("/api/admin/accounting-firms/" + firmId + "/connectors").secure(true)
                .with(user(new UserAccountPrincipal(actor))).header("Authorization", "Bearer " + issued.secret())
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").secure(true).header("Authorization", "Bearer " + issued.secret()))
                .andExpect(status().isUnauthorized());
    }
    @Test void shouldRejectRotatedOrRevokedCredentialsOnTheNextRequest() throws Exception {
        var rotated = service.rotate(actor.getId(), firmId, issued.credential().id());
        mvc.perform(get(route).secure(true).header("Authorization", "Bearer " + issued.secret())).andExpect(status().isUnauthorized());
        mvc.perform(get(route).secure(true).header("Authorization", "Bearer " + rotated.secret())).andExpect(status().isOk());
        service.revoke(actor.getId(), firmId, issued.credential().id()); entities.flush();
        mvc.perform(get(route).secure(true).header("Authorization", "Bearer " + rotated.secret())).andExpect(status().isUnauthorized());
    }
}
