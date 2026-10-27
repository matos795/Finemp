package com.finemp.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.source.*;
import com.finemp.company.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "finemp.sync.control.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SyncControlHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private SyncRunRepository runs;
    @Autowired private JdbcTemplate jdbc;
    private UserAccount actor;
    private AccountingSource source;

    @BeforeEach void prepare() {
        actor = users.saveAndFlush(new UserAccount("Synthetic Sync Actor", UUID.randomUUID() + "@example.invalid", "hash"));
        var company = companies.saveAndFlush(new Company("Synthetic Sync Control", "22222222000122"));
        source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true));
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
    }

    @Test void shouldRequireLoginExplicitPermissionAndCsrfWithoutAdministrativeBypass() throws Exception {
        mvc.perform(get(route(source))).andExpect(status().isUnauthorized());
        var stranger = users.saveAndFlush(new UserAccount("Synthetic Admin", UUID.randomUUID() + "@example.invalid", "hash",
                Set.of(PlatformPermission.PLATFORM_USER_MANAGE)));
        mvc.perform(get(route(source)).with(user(new UserAccountPrincipal(stranger)))).andExpect(status().isForbidden());
        var otherCompany = companies.saveAndFlush(new Company("Other Synthetic Scope", "25252525000125"));
        var otherSource = sources.saveAndFlush(new AccountingSource(otherCompany, AccountingSourceType.QUESTOR, "other-scope", true));
        mvc.perform(get(route(otherSource)).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).header("Idempotency-Key", UUID.randomUUID())
                .contentType(APPLICATION_JSON).content(payload())).andExpect(status().isForbidden());
    }

    @Test void shouldQueueIntentIdempotentlyWithoutReadingErpAndRefuseChangedReplayOrAnotherActiveRun() throws Exception {
        var key = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                    .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(payload()))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.readCount").isEmpty()).andExpect(jsonPath("$.failureCode").isEmpty());
        }
        assertThat(runs.findAllByAccountingSourceId(source.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", Long.class, source.getId())).isEqualTo(1L);
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(payload().replace("INITIAL", "INCREMENTAL")))
                .andExpect(status().isConflict());
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON).content(payload()))
                .andExpect(status().isConflict());
    }

    @Test void shouldKeepHistoryAndCancellationScopedToSourceAndIdempotent() throws Exception {
        var key = UUID.randomUUID();
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(payload())).andExpect(status().isAccepted());
        var run = runs.findByAccountingSourceIdAndIdempotencyKey(source.getId(), key).orElseThrow();
        var foreign = sources.saveAndFlush(new AccountingSource(source.getCompany(), AccountingSourceType.QUESTOR, "other-synthetic", true));
        mvc.perform(get(route(foreign) + "/" + run.getId()).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++)
            mvc.perform(delete(route(source) + "/" + run.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(route(source)).with(user(new UserAccountPrincipal(actor))).param("status", "CANCELLED").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route(source) + "/" + run.getId() + "/events").with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        assertThat(jdbc.queryForObject("SELECT actor_id FROM sync_run_events WHERE sync_run_id = ? AND status = 'CANCELLED'",
                UUID.class, run.getId())).isEqualTo(actor.getId());
    }

    @Test void shouldRejectOversizedAndReversedPeriodsAndRequireTheIdempotencyHeader() throws Exception {
        for (var body : new String[]{payload().replace("2026-07-01", "2026-08-01"), payload().replace("2026-07-01", "2024-01-01")})
            mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .contentType(APPLICATION_JSON).content(payload())).andExpect(status().isBadRequest());
    }

    @Test void shouldIgnoreMassAssignmentAndNeverAllowClientToMarkWorkSucceededOrInventCounters() throws Exception {
        var body = payload().replace("}", ",\"status\":\"SUCCEEDED\",\"readCount\":999,\"requestedByUserId\":\"" + UUID.randomUUID() + "\"}");
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.readCount").isEmpty()).andExpect(jsonPath("$.requestedByUserId").value(actor.getId().toString()));
    }

    @Test void shouldRejectRestrictedScopeInactiveSourceAndOutOfBoundsPagination() throws Exception {
        mvc.perform(get(route(source)).with(user(new UserAccountPrincipal(actor))).param("size", "101"))
                .andExpect(status().isBadRequest());
        var inactive = sources.saveAndFlush(new AccountingSource(source.getCompany(), AccountingSourceType.QUESTOR, "inactive-synthetic", false));
        mvc.perform(post(route(inactive)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON).content(payload()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOURCE_NOT_AVAILABLE"));
        var grant = grants.findByUserAccountIdAndCompanyId(actor.getId(), source.getCompany().getId()).orElseThrow();
        grant.replaceAccess(CompanyAccessRole.ADMIN, CostCenterScope.RESTRICTED, Set.of(CompanyPermission.QUESTOR_SYNC));
        grants.flush();
        mvc.perform(get(route(source)).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
    }

    @Test void shouldNeverCancelRunningWorkThroughTheQueuedCancellationEndpoint() throws Exception {
        var now = java.time.Instant.now().minusSeconds(1);
        var running = new SyncRun(source, actor.getId(), UUID.randomUUID(), SyncRunMode.INITIAL,
                java.time.LocalDate.of(2026,7,1), java.time.LocalDate.of(2026,7,31), now);
        running.start(now);
        runs.saveAndFlush(running);
        mvc.perform(delete(route(source) + "/" + running.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RUN_NOT_CANCELABLE"));
    }

    @Test void shouldRecheckDatabasePermissionEvenWhenJpaAlreadyCachedTheOldGrant() throws Exception {
        var grant = grants.findByUserAccountIdAndCompanyId(actor.getId(), source.getCompany().getId()).orElseThrow();
        assertThat(grant.hasPermission(CompanyPermission.QUESTOR_SYNC)).isTrue();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ? AND permission = 'QUESTOR_SYNC'", grant.getId());
        assertThat(grant.hasPermission(CompanyPermission.QUESTOR_SYNC)).isTrue();
        mvc.perform(post(route(source)).with(user(new UserAccountPrincipal(actor))).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON).content(payload()))
                .andExpect(status().isForbidden());
        assertThat(runs.findAllByAccountingSourceId(source.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    private String route(AccountingSource source) { return "/api/accounting-sources/" + source.getId() + "/sync-runs"; }
    private String payload() { return "{\"mode\":\"INITIAL\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-31\"}"; }
}

