package com.finemp.administration.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.security.UserAccountPrincipal;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PlatformUserAdministrationHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldRequireAuthenticationAndGlobalUserAdministrationPermission() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        var actor = account(Set.of());
        mvc.perform(get("/api/admin/users").with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldSearchAndPageWithoutReturningCredentialsOrLoadingPermissionCollectionsInTheList() throws Exception {
        var admin = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        var target = account(Set.of());
        mvc.perform(get("/api/admin/users").with(user(new UserAccountPrincipal(admin)))
                        .param("search", target.getEmail().toUpperCase(java.util.Locale.ROOT)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(target.getId().toString()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].platformPermissions").doesNotExist());
        mvc.perform(get("/api/admin/users").with(user(new UserAccountPrincipal(admin))).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldProtectTheLastAdministratorAndPendingInvitationFromAdministrativeActivation() throws Exception {
        var admin = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        mvc.perform(patch("/api/admin/users/{id}/status", admin.getId()).with(user(new UserAccountPrincipal(admin)))
                        .with(csrf()).contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/{id}/platform-permissions", admin.getId())
                        .with(user(new UserAccountPrincipal(admin))).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isConflict());
        var pending = users.saveAndFlush(UserAccount.invited("Synthetic Pending", "platform-pending-" + UUID.randomUUID()
                + "@example.invalid"));
        mvc.perform(patch("/api/admin/users/{id}/status", pending.getId()).with(user(new UserAccountPrincipal(admin)))
                        .with(csrf()).contentType(APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldChangeOnlyTheRequestedGlobalPermissionsAndAuditThem() throws Exception {
        var admin = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        var target = account(Set.of());
        mvc.perform(put("/api/admin/users/{id}/platform-permissions", target.getId())
                        .with(user(new UserAccountPrincipal(admin))).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"permissions\":[\"ACCOUNTING_FIRM_VIEW\"]}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(target.getPlatformPermissions()).containsExactly(PlatformPermission.ACCOUNTING_FIRM_VIEW);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_access_grants WHERE user_account_id = ?",
                Long.class, target.getId())).isZero();
        var event = jdbc.queryForMap("SELECT * FROM access_audit_events WHERE subject_user_account_id = ?", target.getId());
        assertThat(event).containsEntry("action", "PLATFORM_PERMISSIONS_REPLACED")
                .containsEntry("actor_user_account_id", admin.getId());
        assertThat(jdbc.queryForList("SELECT permission FROM access_audit_event_permissions WHERE event_id = ?",
                String.class, event.get("id"))).containsExactly("ACCOUNTING_FIRM_VIEW");
    }

    @Test
    void shouldDeactivateAndReactivateAnEstablishedIdentityWithoutRestoringItsCredentialVersion() throws Exception {
        var admin = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        var target = account(Set.of());
        mvc.perform(patch("/api/admin/users/{id}/status", target.getId()).with(user(new UserAccountPrincipal(admin)))
                        .with(csrf()).contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        long disabledVersion = target.getCredentialsVersion();
        mvc.perform(patch("/api/admin/users/{id}/status", target.getId()).with(user(new UserAccountPrincipal(admin)))
                        .with(csrf()).contentType(APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        entityManager.flush();
        assertThat(target.getCredentialsVersion()).isGreaterThan(disabledVersion);
        assertThat(target.getPasswordHash()).isEqualTo("synthetic-existing-hash");
        assertThat(jdbc.queryForList("SELECT action FROM access_audit_events WHERE subject_user_account_id = ?",
                String.class, target.getId())).containsExactlyInAnyOrder("USER_DEACTIVATED", "USER_REACTIVATED");
        mvc.perform(patch("/api/admin/users/{id}/status", target.getId()).with(user(new UserAccountPrincipal(admin)))
                        .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldNotMassAssignCredentialsIdentityOrPermissionsThroughTheStatusDto() throws Exception {
        var admin = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        var target = account(Set.of());
        String originalEmail = target.getEmail();
        UUID targetId = target.getId();
        var response = mvc.perform(patch("/api/admin/users/{id}/status", targetId)
                        .with(user(new UserAccountPrincipal(admin))).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"active\":false,\"passwordHash\":\"tampered\",\"email\":\"tampered@example.invalid\","
                                + "\"platformPermissions\":[\"PLATFORM_USER_MANAGE\"]}"))
                .andReturn().getResponse();
        // Either reject unknown input or ignore it; never bind it to the entity.
        assertThat(response.getStatus()).isIn(200, 400);
        entityManager.flush();
        entityManager.clear();
        var unchanged = users.findById(targetId).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
        assertThat(unchanged.getPasswordHash()).isEqualTo("synthetic-existing-hash");
        assertThat(unchanged.getPlatformPermissions()).isEmpty();
    }

    private UserAccount account(Set<PlatformPermission> permissions) {
        return users.saveAndFlush(new UserAccount("Synthetic Platform", "platform-" + UUID.randomUUID()
                + "@example.invalid", "synthetic-existing-hash", permissions));
    }
}
