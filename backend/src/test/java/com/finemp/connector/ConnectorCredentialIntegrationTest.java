package com.finemp.connector;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.identity.OpaqueAccountToken;
import com.finemp.security.UserAccountPrincipal;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;

@SpringBootTest(properties = "finemp.connector.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional(isolation = Isolation.READ_COMMITTED)
class ConnectorCredentialIntegrationTest {
    @Autowired private ConnectorCredentialService service;
    @Autowired private ConnectorCredentialVerifier verifier;
    @Autowired private ConnectorCredentialRepository credentials;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private AccountingFirmAccessGrantRepository firmGrants;
    @Autowired private CompanyAccessGrantRepository companyGrants;
    @Autowired private SyncRunRepository runs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired private MockMvc mvc;
    private UserAccount actor;
    private UUID firmId, companyId, clientId, sourceId, companyGrantId;

    @BeforeEach void prepare() {
        actor = users.saveAndFlush(new UserAccount("Synthetic Connector Admin", UUID.randomUUID() + "@example.invalid", "hash"));
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Connector Firm", true)); firmId = firm.getId();
        var client = clients.saveAndFlush(new Client(firm, "Synthetic Connector Client", true)); clientId = client.getId();
        var company = companies.saveAndFlush(new Company(client, "Synthetic Connector Company", "44444444000144")); companyId = company.getId();
        var source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true)); sourceId = source.getId();
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        companyGrantId = companyGrants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL,
                Set.of(CompanyPermission.QUESTOR_SYNC))).getId();
    }
    @Test void shouldPersistOnlyTheHashAndReserveTheExplicitSourceRouteWithAtomicAudit() {
        var issued = issue();
        assertThat(verifier.authenticate(issued.secret())).get().extracting(ConnectorIdentity::accountingSourceId).isEqualTo(sourceId);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM connector_credentials WHERE id = ?", String.class, issued.credential().id()))
                .isEqualTo(OpaqueAccountToken.hash(issued.secret())).isNotEqualTo(issued.secret());
        assertThat(sources.findById(sourceId).orElseThrow().getConnectionMode()).isEqualTo(AccountingConnectionMode.CONNECTOR);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connector_credential_events WHERE credential_id = ? AND action = 'ISSUED'",
                Long.class, issued.credential().id())).isEqualTo(1);
        assertThat(service.list(actor.getId(), firmId, "Synthetic", 0, 20).items()).hasSize(1);
    }
    @Test void shouldInvalidateOldSecretsAndSnapshotsOnRotationAndNeverReturnTheHash() {
        var issued = issue(); var previous = verifier.authenticate(issued.secret()).orElseThrow();
        var rotated = service.rotate(actor.getId(), firmId, issued.credential().id());
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        assertThat(verifier.isCurrent(previous)).isFalse();
        assertThat(verifier.authenticate(rotated.secret())).isPresent();
        assertThat(rotated.credential().version()).isGreaterThan(issued.credential().version());
        assertThat(rotated.toString()).doesNotContain(rotated.secret());
    }
    @Test void shouldRevokeIdempotentlyWithoutFallingBackToDirectOrResurrectingThroughRotation() {
        var issued = issue();
        service.revoke(actor.getId(), firmId, issued.credential().id());
        service.revoke(actor.getId(), firmId, issued.credential().id()); entities.flush();
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connector_credential_events WHERE credential_id = ? AND action = 'REVOKED'",
                Long.class, issued.credential().id())).isEqualTo(1);
        assertThat(sources.findById(sourceId).orElseThrow().getConnectionMode()).isEqualTo(AccountingConnectionMode.CONNECTOR);
        assertThatThrownBy(() -> service.rotate(actor.getId(), firmId, issued.credential().id())).isInstanceOf(ConnectorCredentialConflictException.class);
    }
    @Test void shouldRevalidateIssuerPermissionsWithScalarQueriesEvenIfTheOldGrantIsManaged() {
        var issued = issue();
        var cached = companyGrants.findById(companyGrantId).orElseThrow(); assertThat(cached.hasPermission(CompanyPermission.QUESTOR_SYNC)).isTrue();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", companyGrantId);
        assertThat(cached.hasPermission(CompanyPermission.QUESTOR_SYNC)).isTrue();
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        assertThatThrownBy(() -> service.rotate(actor.getId(), firmId, issued.credential().id())).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void shouldRejectExpiredInactiveIssuerInactiveClientAndWrongRouteWithoutLeakingWhichCheckFailed() {
        var issued = issue();
        jdbc.update("UPDATE user_accounts SET active = false WHERE id = ?", actor.getId());
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        jdbc.update("UPDATE user_accounts SET active = true WHERE id = ?", actor.getId());
        jdbc.update("UPDATE clients SET active = false WHERE id = ?", clientId);
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        jdbc.update("UPDATE clients SET active = true WHERE id = ?", clientId);
        jdbc.update("UPDATE accounting_sources SET connection_mode = 'DIRECT' WHERE id = ?", sourceId);
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        jdbc.update("UPDATE accounting_sources SET connection_mode = 'CONNECTOR' WHERE id = ?", sourceId);
        jdbc.update("UPDATE connector_credentials SET expires_at = created_at + INTERVAL '1 microsecond' WHERE id = ?", issued.credential().id());
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        assertThat(verifier.authenticate("malformed")).isEmpty();
    }
    @Test void shouldRequireBothAdministrativeAndFinancialPermissionAndEnforceOwnerChain() {
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", companyGrantId);
        assertThatThrownBy(this::issue).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void shouldRejectRevokedFirmAuthorityAndRestrictedCompanyScopeEvenWithNominalAdminRole() {
        var issued = issue();
        jdbc.update("UPDATE company_access_grants SET cost_center_scope = 'RESTRICTED', access_role = 'ADMIN' WHERE id = ?", companyGrantId);
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        jdbc.update("UPDATE company_access_grants SET cost_center_scope = 'ALL' WHERE id = ?", companyGrantId);
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN"
                + " (SELECT id FROM accounting_firm_access_grants WHERE user_account_id = ? AND accounting_firm_id = ?)", actor.getId(), firmId);
        assertThat(verifier.authenticate(issued.secret())).isEmpty();
        assertThatThrownBy(() -> service.list(actor.getId(), firmId, null, 0, 20)).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void shouldNotGrantConnectorAuthorityToGlobalUserAdministration() {
        var admin = users.saveAndFlush(new UserAccount("Synthetic Global Admin", UUID.randomUUID() + "@example.invalid", "hash",
                Set.of(PlatformPermission.PLATFORM_USER_MANAGE)));
        assertThatThrownBy(() -> service.issue(admin.getId(), firmId, new CreateConnectorCredentialRequest(sourceId, "Synthetic")))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void shouldRejectACompanyFromAnotherFirmEvenWhenTheActorHasSyncOnThatCompany() {
        var otherFirm = firms.saveAndFlush(new AccountingFirm("Other Synthetic Firm", true));
        var otherClient = clients.saveAndFlush(new Client(otherFirm, "Other Synthetic Client", true));
        var otherCompany = companies.saveAndFlush(new Company(otherClient, "Other Synthetic Company", "45454545000145"));
        var otherSource = sources.saveAndFlush(new AccountingSource(otherCompany, AccountingSourceType.QUESTOR, "synthetic-other", true));
        companyGrants.saveAndFlush(new CompanyAccessGrant(actor, otherCompany, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        assertThatThrownBy(() -> service.issue(actor.getId(), firmId, new CreateConnectorCredentialRequest(otherSource.getId(), "Synthetic")))
                .isInstanceOf(SaasResourceNotFoundException.class);
    }
    @Test void shouldRefuseRouteChangesWhileAnActiveRunExists() {
        runs.saveAndFlush(new SyncRun(sources.getReferenceById(sourceId), actor.getId(), UUID.randomUUID(), SyncRunMode.INITIAL,
                LocalDate.of(2026,7,1), LocalDate.of(2026,7,31), Instant.now()));
        assertThatThrownBy(this::issue).isInstanceOf(ConnectorCredentialConflictException.class);
        assertThat(sources.findById(sourceId).orElseThrow().getConnectionMode()).isEqualTo(AccountingConnectionMode.DIRECT);
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollbackSecretRouteAndCredentialWhenTheAuditCannotBePersisted() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "f_connector_fail_" + suffix, trigger = "t_connector_fail_" + suffix;
        jdbc.execute("CREATE FUNCTION " + function + "() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF EXISTS"
                + " (SELECT 1 FROM connector_credentials WHERE id = NEW.credential_id AND accounting_source_id = '" + sourceId
                + "'::uuid) THEN RAISE EXCEPTION 'Synthetic connector audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON connector_credential_events FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        try {
            assertThatThrownBy(this::issue).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Synthetic connector audit failure");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connector_credentials WHERE accounting_source_id = ?", Long.class, sourceId)).isZero();
            assertThat(sources.findById(sourceId).orElseThrow().getConnectionMode()).isEqualTo(AccountingConnectionMode.DIRECT);
        } finally {
            jdbc.execute("DROP TRIGGER " + trigger + " ON connector_credential_events"); jdbc.execute("DROP FUNCTION " + function + "()");
            jdbc.update("DELETE FROM connector_credential_events WHERE credential_id IN (SELECT id FROM connector_credentials WHERE accounting_source_id = ?)", sourceId);
            jdbc.update("DELETE FROM connector_credentials WHERE accounting_source_id = ?", sourceId);
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", companyGrantId);
            jdbc.update("DELETE FROM company_access_grants WHERE id = ?", companyGrantId);
            jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN"
                    + " (SELECT id FROM accounting_firm_access_grants WHERE user_account_id = ? AND accounting_firm_id = ?)", actor.getId(), firmId);
            jdbc.update("DELETE FROM accounting_firm_access_grants WHERE user_account_id = ? AND accounting_firm_id = ?", actor.getId(), firmId);
            jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
            jdbc.update("DELETE FROM companies WHERE id = ?", companyId); jdbc.update("DELETE FROM clients WHERE id = ?", clientId);
            jdbc.update("DELETE FROM accounting_firms WHERE id = ?", firmId); jdbc.update("DELETE FROM user_accounts WHERE id = ?", actor.getId());
        }
    }
    @Test void shouldProtectHttpWithSessionCsrfAndNoStoreAndKeepSecretsOutOfLists() throws Exception {
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        String body = "{\"accountingSourceId\":\"" + sourceId + "\",\"label\":\"Synthetic HTTP Connector\"}";
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.secret").isString()).andExpect(jsonPath("$.credential.tokenHash").doesNotExist());
        mvc.perform(get(route()).with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].secret").doesNotExist())
                .andExpect(jsonPath("$.items[0].tokenHash").doesNotExist());
        mvc.perform(get(route()).with(user(new UserAccountPrincipal(actor))).param("size", "101")).andExpect(status().isBadRequest());
    }
    private ConnectorSecretResponse issue() { return service.issue(actor.getId(), firmId, new CreateConnectorCredentialRequest(sourceId, "Synthetic Connector")); }
    private String route() { return "/api/admin/accounting-firms/" + firmId + "/connectors"; }
}
