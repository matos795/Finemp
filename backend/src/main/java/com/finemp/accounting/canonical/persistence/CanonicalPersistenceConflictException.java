package com.finemp.accounting.canonical.persistence;

public final class CanonicalPersistenceConflictException extends RuntimeException {
    public CanonicalPersistenceConflictException() {
        super("Canonical persistence identity conflicts with existing content.");
    }
}
