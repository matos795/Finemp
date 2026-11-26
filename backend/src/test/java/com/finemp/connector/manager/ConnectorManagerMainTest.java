package com.finemp.connector.manager;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class ConnectorManagerMainTest {
    @Test
    void helpUsesTheStandaloneManagerEntryPointWithoutStartingTheGui() {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();

        int code = ConnectorManagerMain.execute(
                new String[] {"--help"},
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertThat(code).isZero();
        assertThat(stdout.toString()).contains("FINEMP Connector Manager");
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void unsupportedArgumentsFailEarlyWithAConstantDiagnostic() {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();

        int code = ConnectorManagerMain.execute(
                new String[] {"--run"},
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertThat(code).isEqualTo(2);
        assertThat(stderr.toString()).contains("argumentos inválidos");
        assertThat(stdout.toString()).isEmpty();
    }
}
