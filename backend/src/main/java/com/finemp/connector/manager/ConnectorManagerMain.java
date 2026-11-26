package com.finemp.connector.manager;

import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

public final class ConnectorManagerMain {
    private ConnectorManagerMain() {}

    public static void main(String[] args) {
        int code = execute(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int execute(String[] args, PrintStream output, PrintStream error) {
        if (args != null && args.length == 1 && "--help".equals(args[0])) {
            output.println("FINEMP Connector Manager: execute sem argumentos no Windows para abrir a interface local.");
            return 0;
        }

        if (args == null || args.length != 0) {
            error.println("FINEMP Connector Manager: argumentos inválidos. Use --help.");
            return 2;
        }

        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            error.println("FINEMP Connector Manager: a interface local exige Windows.");
            return 2;
        }

        if (GraphicsEnvironment.isHeadless()) {
            error.println("FINEMP Connector Manager: ambiente gráfico indisponível.");
            return 2;
        }

        String programData = System.getenv("ProgramData");
        if (programData == null || programData.isBlank()) {
            error.println("FINEMP Connector Manager: diretório ProgramData indisponível.");
            return 2;
        }

        Path runtimeRoot = Path.of(programData, "FINEMP", "Connector");
        SwingUtilities.invokeLater(() -> {
            var frame = new ConnectorManagerFrame(ConnectorManagerService.system(runtimeRoot));
            frame.setVisible(true);
        });
        return 0;
    }
}
