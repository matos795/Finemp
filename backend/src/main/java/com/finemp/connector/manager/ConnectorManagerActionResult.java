package com.finemp.connector.manager;

record ConnectorManagerActionResult(boolean success, String message) {
    @Override
    public String toString() {
        return "ConnectorManagerActionResult[success=" + success + "]";
    }
}
