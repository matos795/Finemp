package com.finemp.accounting.sync;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {
    @org.springframework.data.jpa.repository.Query("select run.accountingSource.id from SyncRun run where run.id = :id")
    Optional<UUID> findSourceIdByRunId(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<SyncRun> findByIdAndAccountingSourceId(UUID id, UUID accountingSourceId);
    Optional<SyncRun> findByAccountingSourceIdAndIdempotencyKey(UUID accountingSourceId, UUID idempotencyKey);
    boolean existsByAccountingSourceIdAndStatusIn(UUID accountingSourceId, Collection<SyncRunStatus> statuses);
    Page<SyncRun> findAllByAccountingSourceId(UUID accountingSourceId, Pageable pageable);
    Page<SyncRun> findAllByAccountingSourceIdAndStatus(UUID accountingSourceId, SyncRunStatus status, Pageable pageable);
}
