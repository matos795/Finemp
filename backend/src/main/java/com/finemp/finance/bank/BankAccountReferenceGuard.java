package com.finemp.finance.bank;

import java.util.UUID;

/** Operational modules implement tenant-scoped reference checks. Never use AccountingMovement as a substitute. */
public interface BankAccountReferenceGuard { boolean hasReferences(UUID companyId,UUID bankAccountId); }
