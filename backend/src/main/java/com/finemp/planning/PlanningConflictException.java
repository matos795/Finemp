package com.finemp.planning;

public class PlanningConflictException extends RuntimeException {

    public enum Reason {
        STALE_VERSION
    }

    private final Reason reason;

    public PlanningConflictException(Reason reason) {
        super("Conflito de planejamento: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
