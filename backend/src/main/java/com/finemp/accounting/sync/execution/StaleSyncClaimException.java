package com.finemp.accounting.sync.execution;

public class StaleSyncClaimException extends RuntimeException {
    public StaleSyncClaimException() { super("Sync execution context is no longer current."); }
}

