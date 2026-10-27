package com.finemp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmPermission;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccessAuditRecorderIntegrationTest {

    @Autowired private AccessAuditRecorder recorder;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    private final UUID subject = UUID.randomUUID();

    @AfterEach
    void cleanupOwnSyntheticJournal() {
        jdbc.update("DELETE FROM access_audit_events WHERE subject_user_account_id = ?", subject);
    }

    @Test
    void shouldCommitTypedMetadataAndPermissionNamesWithoutFreeTextOrSecrets() {
        UUID actor = UUID.randomUUID();
        UUID firm = UUID.randomUUID();
        UUID id = new TransactionTemplate(transactions).execute(status -> recorder.record(
                AccessAuditAction.FIRM_ACCESS_REPLACED, actor, subject, firm, null,
                Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        var event = jdbc.queryForMap("SELECT * FROM access_audit_events WHERE id = ?", id);
        assertThat(event).containsEntry("actor_user_account_id", actor).containsEntry("subject_user_account_id", subject)
                .containsEntry("accounting_firm_id", firm).containsEntry("action", "FIRM_ACCESS_REPLACED");
        assertThat(event.get("occurred_at")).isNotNull();
        assertThat(jdbc.queryForList("SELECT permission FROM access_audit_event_permissions WHERE event_id = ?",
                String.class, id)).containsExactly("HIERARCHY_MANAGE");
    }

    @Test
    void shouldRollBackTheEventWithTheCallerTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            recorder.record(AccessAuditAction.PASSWORD_CHANGED, subject, subject, null, null, Set.of());
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id = ?",
                Long.class, subject)).isZero();
    }

    @Test
    void shouldRefuseToWriteOutsideAnExistingTransaction() {
        assertThatThrownBy(() -> recorder.record(AccessAuditAction.PASSWORD_RESET_ISSUED, null, subject,
                null, null, Set.of())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id = ?",
                Long.class, subject)).isZero();
    }
}
