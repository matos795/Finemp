package com.finemp.contracts;

import java.util.UUID;

/**
 * Modules that retain a semantic link to a contract can veto partner reassignment.
 * This keeps Contract independent from those modules while preserving cross-module invariants.
 */
public interface ContractPartnerChangeGuard {
    boolean blocksPartnerChange(UUID companyId, UUID contractId);
}
