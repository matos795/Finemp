package com.finemp.assets;

public class AssetConflictException extends RuntimeException {
    public enum Reason {
        STALE_VERSION,
        DUPLICATE_TAG
    }

    private final Reason reason;

    public AssetConflictException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
