package com.finemp.connector.manager;

import java.nio.file.Path;

record ConnectorManagerStatus(
        State state,
        String lastRunTime,
        Long lastTaskResult,
        Path runtimeRoot) {

    enum State {
        NOT_INSTALLED,
        RUNNING,
        STOPPED,
        FAILED,
        STATUS_UNAVAILABLE,
        UNKNOWN
    }

    @Override
    public String toString() {
        return "ConnectorManagerStatus[state=" + state + "]";
    }
}
