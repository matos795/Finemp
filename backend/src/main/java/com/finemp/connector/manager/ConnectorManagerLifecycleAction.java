package com.finemp.connector.manager;

enum ConnectorManagerLifecycleAction {
    START("Start"),
    STOP("Stop"),
    RESTART("Restart");

    private final String scriptValue;

    ConnectorManagerLifecycleAction(String scriptValue) {
        this.scriptValue = scriptValue;
    }

    String scriptValue() {
        return scriptValue;
    }
}
