package com.finemp.accounting.sync.execution;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SyncAttemptRepository extends JpaRepository<SyncAttempt, UUID> {
    Optional<SyncAttempt> findBySyncRunIdAndFenceVersion(UUID runId, long version);
    Optional<SyncAttempt> findFirstBySyncRunIdOrderByAttemptNumberDesc(UUID runId);
    Page<SyncAttempt> findAllBySyncRunId(UUID runId, Pageable pageable);
}
