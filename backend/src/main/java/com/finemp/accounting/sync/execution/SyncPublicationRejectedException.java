package com.finemp.accounting.sync.execution;

import com.finemp.accounting.sync.SyncFailureCode;
import java.util.Objects;

public class SyncPublicationRejectedException extends RuntimeException {
    private final SyncFailureCode code;
    public SyncPublicationRejectedException(SyncFailureCode code) {
        super("Accounting publication rejected without changing previously published data.");
        this.code = Objects.requireNonNull(code);
    }
    public SyncFailureCode getCode() { return code; }
}

