package com.finemp.connector.manager;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorManagerProcessInvokerTest {

    @Test
    void timeoutTerminatesDescendantsThatKeepOutputPipesOpen() {
        var invoker = new ConnectorManagerProcessInvoker.SystemProcessInvoker(
                Duration.ofMillis(250), 64 * 1024);
        var started = Instant.now();

        var result = invoker.invoke(List.of(
                javaExecutable(),
                "-cp",
                System.getProperty("java.class.path"),
                PipeHoldingParent.class.getName()));

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(3));
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("windows")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    public static final class PipeHoldingParent {
        public static void main(String[] args) throws Exception {
            new ProcessBuilder(
                    javaExecutable(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    PipeHoldingChild.class.getName())
                    .inheritIO()
                    .start();
            Thread.sleep(10_000);
        }
    }

    public static final class PipeHoldingChild {
        public static void main(String[] args) throws Exception {
            Thread.sleep(10_000);
        }
    }
}
