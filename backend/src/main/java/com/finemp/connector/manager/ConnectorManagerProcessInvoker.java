package com.finemp.connector.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

interface ConnectorManagerProcessInvoker {
    record Result(int exitCode, String stdout, String stderr) {}

    Result invoke(List<String> command);

    static ConnectorManagerProcessInvoker system() {
        return new SystemProcessInvoker(Duration.ofSeconds(60), 64 * 1024);
    }

    final class SystemProcessInvoker implements ConnectorManagerProcessInvoker {
        private final Duration timeout;
        private final int outputLimitBytes;

        SystemProcessInvoker(Duration timeout, int outputLimitBytes) {
            this.timeout = timeout;
            this.outputLimitBytes = outputLimitBytes;
        }

        @Override
        public Result invoke(List<String> command) {
            if (command == null || command.isEmpty()) {
                return new Result(3, "", "");
            }

            try {
                var process = new ProcessBuilder(command).start();
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var stdoutFuture = executor.submit(
                            () -> process.getInputStream().readNBytes(outputLimitBytes + 1));
                    var stderrFuture = executor.submit(
                            () -> process.getErrorStream().readNBytes(outputLimitBytes + 1));

                    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(5, TimeUnit.SECONDS);
                        return new Result(3, "", "");
                    }

                    var stdout = boundedText(stdoutFuture.get(5, TimeUnit.SECONDS));
                    var stderr = boundedText(stderrFuture.get(5, TimeUnit.SECONDS));
                    return new Result(process.exitValue(), stdout, stderr);
                }
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException unavailable) {
                if (unavailable instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return new Result(3, "", "");
            }
        }

        private String boundedText(byte[] bytes) {
            if (bytes.length > outputLimitBytes) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
