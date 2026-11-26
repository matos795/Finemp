package com.finemp.connector.manager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class ConnectorManagerService {
    private static final String CONTROL = "connector-windows-control.ps1";
    private static final String RUNNER = "connector-windows-run.ps1";
    private static final String RUNTIME = "connector-windows-runtime.json";

    private final Path runtimeRoot;
    private final String powershellExecutable;
    private final ConnectorManagerProcessInvoker invoker;

    ConnectorManagerService(Path runtimeRoot, String powershellExecutable, ConnectorManagerProcessInvoker invoker) {
        if (runtimeRoot == null || powershellExecutable == null || powershellExecutable.isBlank() || invoker == null) {
            throw new IllegalArgumentException("Invalid Connector Manager local configuration.");
        }
        this.runtimeRoot = runtimeRoot.toAbsolutePath().normalize();
        this.powershellExecutable = powershellExecutable;
        this.invoker = invoker;
    }

    static ConnectorManagerService system(Path runtimeRoot) {
        return new ConnectorManagerService(runtimeRoot, "powershell.exe", ConnectorManagerProcessInvoker.system());
    }

    Path runtimeRoot() {
        return runtimeRoot;
    }

    ConnectorManagerStatus status() {
        if (!runtimeFilesPresent()) {
            return new ConnectorManagerStatus(
                    ConnectorManagerStatus.State.NOT_INSTALLED, null, null, runtimeRoot);
        }

        var result = invoker.invoke(List.of(
                powershellExecutable,
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", runtimeRoot.resolve(CONTROL).toString(),
                "-Action", "Status"));

        if (result.exitCode() != 0) {
            return new ConnectorManagerStatus(
                    ConnectorManagerStatus.State.NOT_INSTALLED, null, null, runtimeRoot);
        }

        String taskState = knownField(result.stdout(), "State");
        String lastRunTime = knownField(result.stdout(), "LastRunTime");
        Integer lastTaskResult = integerField(result.stdout(), "LastTaskResult");

        var state = classify(taskState, lastTaskResult);
        return new ConnectorManagerStatus(state, lastRunTime, lastTaskResult, runtimeRoot);
    }

    ConnectorManagerActionResult validateInstalledConfiguration() {
        if (!runtimeFilesPresent()) {
            return new ConnectorManagerActionResult(
                    false,
                    "O runtime do FINEMP Connector não está instalado neste computador.");
        }

        var result = invoker.invoke(List.of(
                powershellExecutable,
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", runtimeRoot.resolve(RUNNER).toString(),
                "-RuntimeConfigPath", runtimeRoot.resolve(RUNTIME).toString(),
                "-ValidateOnly"));

        if (result.exitCode() == 0) {
            return new ConnectorManagerActionResult(true, "Configuração local válida.");
        }

        return new ConnectorManagerActionResult(
                false,
                "Não foi possível validar a configuração instalada do Connector.");
    }

    private boolean runtimeFilesPresent() {
        return Files.isRegularFile(runtimeRoot.resolve(CONTROL))
                && Files.isRegularFile(runtimeRoot.resolve(RUNNER))
                && Files.isRegularFile(runtimeRoot.resolve(RUNTIME));
    }

    private static ConnectorManagerStatus.State classify(String rawState, Integer lastTaskResult) {
        if (rawState == null) {
            return ConnectorManagerStatus.State.UNKNOWN;
        }

        return switch (rawState.trim().toLowerCase(Locale.ROOT)) {
            case "running" -> ConnectorManagerStatus.State.RUNNING;
            case "ready", "disabled" ->
                    lastTaskResult != null && lastTaskResult != 0
                            ? ConnectorManagerStatus.State.FAILED
                            : ConnectorManagerStatus.State.STOPPED;
            default -> ConnectorManagerStatus.State.UNKNOWN;
        };
    }

    private static String knownField(String output, String field) {
        if (output == null || output.isBlank()) {
            return null;
        }

        for (String line : output.lines().toList()) {
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }

            String name = line.substring(0, separator).trim();
            if (!field.equals(name)) {
                continue;
            }

            String value = line.substring(separator + 1).trim();
            if (value.length() > 128 || value.indexOf('\0') >= 0) {
                return null;
            }
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private static Integer integerField(String output, String field) {
        String value = knownField(output, field);
        if (value == null || !value.matches("[+-]?\\d{1,11}")) {
            return null;
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
