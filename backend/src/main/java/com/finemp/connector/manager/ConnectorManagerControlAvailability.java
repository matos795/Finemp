package com.finemp.connector.manager;

record ConnectorManagerControlAvailability(
        boolean controlsVisible,
        boolean startEnabled,
        boolean stopEnabled,
        boolean restartEnabled) {

    static ConnectorManagerControlAvailability forStatus(ConnectorManagerStatus status) {
        if (status == null || status.state() == ConnectorManagerStatus.State.NOT_INSTALLED) {
            return new ConnectorManagerControlAvailability(false, false, false, false);
        }

        return switch (status.state()) {
            case RUNNING -> new ConnectorManagerControlAvailability(true, false, true, true);
            case STOPPED, FAILED -> new ConnectorManagerControlAvailability(true, true, false, true);
            case UNKNOWN -> new ConnectorManagerControlAvailability(true, false, false, true);
            case NOT_INSTALLED -> new ConnectorManagerControlAvailability(false, false, false, false);
        };
    }
}
