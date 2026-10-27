package com.finemp.connector;

import java.util.UUID;

/** No ERP secret or authorization beyond this single immutable source binding. */
public record ConnectorIdentity(UUID credentialId, UUID accountingFirmId, UUID accountingSourceId, UUID issuedByUserId, long version) { }
