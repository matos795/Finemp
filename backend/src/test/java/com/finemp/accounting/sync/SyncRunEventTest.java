package com.finemp.accounting.sync;

import static org.assertj.core.api.Assertions.*;
import com.finemp.accounting.source.*;
import com.finemp.company.Company;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncRunEventTest {
    @Test void shouldKeepImmutableStatusSnapshotAndRejectUnattributedUserOrForgedSystemActor() {
        var now = Instant.parse("2026-07-01T12:00:00Z");
        var run = new SyncRun(new AccountingSource(new Company("Synthetic", "23232323000123"), AccountingSourceType.QUESTOR, "synthetic", true),
                UUID.randomUUID(), UUID.randomUUID(), SyncRunMode.INITIAL, LocalDate.of(2026,7,1), LocalDate.of(2026,7,31), now);
        var actor = UUID.randomUUID();
        var event = new SyncRunEvent(run, SyncRunActorType.USER, actor, now);
        run.cancel(now);
        assertThat(event.getStatus()).isEqualTo(SyncRunStatus.REQUESTED);
        assertThat(event.getActorId()).isEqualTo(actor);
        assertThatThrownBy(() -> new SyncRunEvent(run, SyncRunActorType.USER, null, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncRunEvent(run, SyncRunActorType.SYSTEM, actor, now)).isInstanceOf(IllegalArgumentException.class);
    }
}

