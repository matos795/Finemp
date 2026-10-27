package com.finemp.accounting.sync.worker;

import java.util.List;
import java.util.UUID;

public interface SyncCandidateSource { List<UUID> due(int limit); }
