package com.finemp.accounting.sync;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunEventRepository extends JpaRepository<SyncRunEvent, UUID> {
    Page<SyncRunEvent> findAllBySyncRunIdAndSyncRunAccountingSourceId(UUID syncRunId, UUID accountingSourceId, Pageable pageable);
}

