package com.finemp.finance.transfer;

import java.util.UUID;

/** Immutable command result; deletion reports the last persisted version, not a current resource. */
public record BankTransferCommandReceipt(UUID companyId,UUID commandId,UUID transferId,BankTransferCommandAction action,long version) { }
