package com.finemp.accounting.sync.execution;

import java.util.UUID;

/** Machine authority is revalidated against the immutable source binding, never inferred from a browser role. */
public interface SyncAgentAuthorization { boolean isCurrent(SyncClaimAgent agent, UUID accountingSourceId); }
