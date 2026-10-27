package com.finemp.accounting.sync.worker;

import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface SyncCandidateSource {
    List<UUID> due(int limit, Set<UUID> excluded);
    default List<UUID> due(int limit) { return due(limit, Set.of()); }
}
