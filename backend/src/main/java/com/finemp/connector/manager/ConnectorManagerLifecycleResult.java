package com.finemp.connector.manager;

record ConnectorManagerLifecycleResult(
        boolean success,
        String message,
        ConnectorManagerStatus status) {

    @Override
    public String toString() {
        return "ConnectorManagerLifecycleResult[success=" + success
                + ", state=" + (status == null ? "UNKNOWN" : status.state()) + "]";
    }
}
