package com.finemp.connector.manager;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorManagerServiceTest {
    @TempDir Path directory;

    @Test
    void missingRuntimeIsReportedAsNotInstalledWithoutStartingAnyProcess() {
        var invoker = new FakeInvoker();
        var service = new ConnectorManagerService(directory, "powershell.exe", invoker);

        var status = service.status();

        assertThat(status.state()).isEqualTo(ConnectorManagerStatus.State.NOT_INSTALLED);
        assertThat(status.lastRunTime()).isNull();
        assertThat(status.lastTaskResult()).isNull();
        assertThat(invoker.commands).isEmpty();
    }

    @Test
    void statusParsesOnlyKnownFieldsAndNeverEchoesUnexpectedOutput() throws Exception {
        installedRuntime();
        var invoker = new FakeInvoker();
        invoker.next = new ConnectorManagerProcessInvoker.Result(
                0,
                """
                TaskName : FINEMP Connector
                State : Running
                LastRunTime : 26/11/2026 14:20:00
                LastTaskResult : 0
                PRIVATE_TOKEN : should-never-appear
                """,
                "synthetic private stderr");
        var service = new ConnectorManagerService(directory, "powershell.exe", invoker);

        var status = service.status();

        assertThat(status.state()).isEqualTo(ConnectorManagerStatus.State.RUNNING);
        assertThat(status.lastRunTime()).isEqualTo("26/11/2026 14:20:00");
        assertThat(status.lastTaskResult()).isZero();
        assertThat(status.toString()).doesNotContain("should-never-appear", "synthetic private stderr", "PRIVATE_TOKEN");
        assertThat(invoker.commands).singleElement().satisfies(command -> {
            assertThat(command).contains("-Action", "Status");
            assertThat(command).doesNotContain("Stop", "Restart", "Uninstall");
        });
    }

    @Test
    void nonZeroLastTaskResultIsReportedAsFailedWhenTaskIsNotRunning() throws Exception {
        installedRuntime();
        var invoker = new FakeInvoker();
        invoker.next = new ConnectorManagerProcessInvoker.Result(
                0,
                "State : Ready\nLastRunTime : 26/11/2026 14:20:00\nLastTaskResult : 3\n",
                "");
        var service = new ConnectorManagerService(directory, "powershell.exe", invoker);

        assertThat(service.status().state()).isEqualTo(ConnectorManagerStatus.State.FAILED);
    }

    @Test
    void unsignedWindowsTaskResultIsPreservedAndClassifiedAsFailed() throws Exception {
        installedRuntime();
        var invoker = new FakeInvoker();
        invoker.next = new ConnectorManagerProcessInvoker.Result(
                0,
                "State : Ready\nLastRunTime : 26/11/2026 14:20:00\nLastTaskResult : 2147942402\n",
                "");
        var service = new ConnectorManagerService(directory, "powershell.exe", invoker);

        var status = service.status();

        assertThat(status.state()).isEqualTo(ConnectorManagerStatus.State.FAILED);
        assertThat(status.lastTaskResult()).isEqualTo(2147942402L);
    }

    @Test
    void validationDelegatesToInstalledRunnerWithoutSecretValuesInArguments() throws Exception {
        installedRuntime();
        var invoker = new FakeInvoker();
        invoker.next = new ConnectorManagerProcessInvoker.Result(0, "Configuração local válida.", "");
        var service = new ConnectorManagerService(directory, "powershell.exe", invoker);

        var result = service.validateInstalledConfiguration();

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Configuração local válida.");
        assertThat(invoker.commands).singleElement().satisfies(command -> {
            assertThat(command).contains("-ValidateOnly");
            assertThat(command).contains(directory.resolve("connector-windows-run.ps1").toString());
            assertThat(String.join(" ", command)).doesNotContain(
                    "FINEMP_CONNECTOR_", "password", "token=", "jdbc:firebird", "private");
        });
    }

    @Test
    void failedValidationReturnsAConstantActionableMessageInsteadOfProcessOutput() throws Exception {
        installedRuntime();
        var invoker = new FakeInvoker();
        invoker.next = new ConnectorManagerProcessInvoker.Result(
                3,
                "private stdout",
                "password=private-value");

        var result = new ConnectorManagerService(directory, "powershell.exe", invoker)
                .validateInstalledConfiguration();

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(
                "Não foi possível validar a configuração instalada do Connector.");
        assertThat(result.toString()).doesNotContain("private", "password");
    }

    private void installedRuntime() throws Exception {
        Files.writeString(directory.resolve("connector-windows-control.ps1"), "# synthetic");
        Files.writeString(directory.resolve("connector-windows-run.ps1"), "# synthetic");
        Files.writeString(directory.resolve("connector-windows-runtime.json"), "{}");
    }

    private static final class FakeInvoker implements ConnectorManagerProcessInvoker {
        final List<List<String>> commands = new ArrayList<>();
        Result next = new Result(0, "", "");

        @Override
        public Result invoke(List<String> command) {
            commands.add(List.copyOf(command));
            return next;
        }
    }
}
