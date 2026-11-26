package com.finemp.connector.manager;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorManagerControlAvailabilityTest {

    @Test
    void runningRuntimeAllowsStopAndRestartButNotStart() {
        var controls = ConnectorManagerControlAvailability.forStatus(status(ConnectorManagerStatus.State.RUNNING));

        assertThat(controls.startEnabled()).isFalse();
        assertThat(controls.stopEnabled()).isTrue();
        assertThat(controls.restartEnabled()).isTrue();
    }

    @Test
    void stoppedOrFailedRuntimeAllowsStartAndRestartButNotStop() {
        for (var state : new ConnectorManagerStatus.State[] {
                ConnectorManagerStatus.State.STOPPED,
                ConnectorManagerStatus.State.FAILED,
                ConnectorManagerStatus.State.UNKNOWN
        }) {
            var controls = ConnectorManagerControlAvailability.forStatus(status(state));

            assertThat(controls.startEnabled()).isTrue();
            assertThat(controls.stopEnabled()).isFalse();
            assertThat(controls.restartEnabled()).isTrue();
        }
    }

    @Test
    void notInstalledRuntimeOffersNoLifecycleControl() {
        var controls = ConnectorManagerControlAvailability.forStatus(
                status(ConnectorManagerStatus.State.NOT_INSTALLED));

        assertThat(controls.startEnabled()).isFalse();
        assertThat(controls.stopEnabled()).isFalse();
        assertThat(controls.restartEnabled()).isFalse();
    }

    private static ConnectorManagerStatus status(ConnectorManagerStatus.State state) {
        return new ConnectorManagerStatus(state, null, null, Path.of("."));
    }
}
