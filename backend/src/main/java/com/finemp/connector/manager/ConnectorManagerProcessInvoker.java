package com.finemp.connector.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

            var executor = Executors.newVirtualThreadPerTaskExecutor();
            Process process = null;
            Future<byte[]> stdoutFuture = null;
            Future<byte[]> stderrFuture = null;
            try {
                process = new ProcessBuilder(command).start();
                Process started = process;
                stdoutFuture = executor.submit(
                        () -> started.getInputStream().readNBytes(outputLimitBytes + 1));
                stderrFuture = executor.submit(
                        () -> started.getErrorStream().readNBytes(outputLimitBytes + 1));

                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    terminateTree(process);
                    closeProcessStreams(process);
                    cancel(stdoutFuture);
                    cancel(stderrFuture);
                    return new Result(3, "", "");
                }

                var stdout = boundedText(stdoutFuture.get(5, TimeUnit.SECONDS));
                var stderr = boundedText(stderrFuture.get(5, TimeUnit.SECONDS));
                return new Result(process.exitValue(), stdout, stderr);
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException unavailable) {
                if (unavailable instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (process != null) {
                    terminateTree(process);
                    closeProcessStreams(process);
                }
                cancel(stdoutFuture);
                cancel(stderrFuture);
                return new Result(3, "", "");
            } finally {
                executor.shutdownNow();
            }
        }

        private static void terminateTree(Process process) {
            process.descendants().forEach(descendant -> {
                try {
                    descendant.destroyForcibly();
                } catch (RuntimeException ignored) {
                    // Best effort: the parent is still terminated below.
                }
            });
            process.destroyForcibly();
        }

        private static void closeProcessStreams(Process process) {
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
                // Best effort after timeout/failure.
            }
            try {
                process.getErrorStream().close();
            } catch (IOException ignored) {
                // Best effort after timeout/failure.
            }
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // Best effort after timeout/failure.
            }
        }

        private static void cancel(Future<?> future) {
            if (future != null) {
                future.cancel(true);
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
