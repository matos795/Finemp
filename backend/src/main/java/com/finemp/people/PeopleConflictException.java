package com.finemp.people;

public class PeopleConflictException extends RuntimeException {
    public enum Reason {
        STALE_VERSION,
        DUPLICATE_POSITION
    }

    private final Reason reason;

    public PeopleConflictException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
