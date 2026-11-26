package com.finemp.connector.manager;

record ConnectorManagerControlAvailability(
        boolean startEnabled,
        boolean stopEnabled,
        boolean restartEnabled) {

    static ConnectorManagerControlAvailability forStatus(ConnectorManagerStatus status) {
        if (status == null || status.state() == ConnectorManagerStatus.State.NOT_INSTALLED) {
            return new ConnectorManagerControlAvailability(false, false, false);
        }

        if (status.state() == ConnectorManagerStatus.State.RUNNING) {
            return new ConnectorManagerControlAvailability(false, true, true);
        }

        return new ConnectorManagerControlAvailability(true, false, true);
    }
}
