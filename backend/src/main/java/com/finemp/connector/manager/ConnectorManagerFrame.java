package com.finemp.connector.manager;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

final class ConnectorManagerFrame extends JFrame {
    private final ConnectorManagerService service;
    private final JLabel stateValue = new JLabel("Carregando…");
    private final JLabel lastRunValue = new JLabel("—");
    private final JLabel lastResultValue = new JLabel("—");
    private final JLabel message = new JLabel(" ");
    private final JButton refresh = new JButton("Atualizar status");
    private final JButton validate = new JButton("Validar configuração");
    private final JButton start = new JButton("Iniciar");
    private final JButton stop = new JButton("Parar");
    private final JButton restart = new JButton("Reiniciar");
    private ConnectorManagerStatus lastStatus;
    private boolean lifecycleInFlight;

    ConnectorManagerFrame(ConnectorManagerService service) {
        super("FINEMP Connector Manager");
        this.service = service;

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(620, 330);
        setLocationByPlatform(true);
        setLayout(new BorderLayout(12, 12));

        var title = new JLabel("FINEMP Connector Manager");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        var header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        header.add(title, BorderLayout.NORTH);
        header.add(new JLabel("Administra o runtime local; fechar esta janela não interrompe o Connector."),
                BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        var status = new JPanel();
        status.setLayout(new javax.swing.BoxLayout(status, javax.swing.BoxLayout.Y_AXIS));
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Estado local"),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        status.add(row("Runtime", stateValue));
        status.add(row("Última execução", lastRunValue));
        status.add(row("Último resultado", lastResultValue));
        status.add(row("Diretório", new JLabel(service.runtimeRoot().toString())));
        add(status, BorderLayout.CENTER);

        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        refresh.addActionListener(event -> refreshStatus());
        validate.addActionListener(event -> validateConfiguration());
        start.addActionListener(event -> runLifecycle(ConnectorManagerLifecycleAction.START));
        stop.addActionListener(event -> {
            if (confirmLifecycle("Parar o FINEMP Connector?")) {
                runLifecycle(ConnectorManagerLifecycleAction.STOP);
            }
        });
        restart.addActionListener(event -> {
            if (confirmLifecycle("Reiniciar o FINEMP Connector?")) {
                runLifecycle(ConnectorManagerLifecycleAction.RESTART);
            }
        });
        actions.add(refresh);
        actions.add(validate);
        actions.add(start);
        actions.add(stop);
        actions.add(restart);
        actions.add(message);
        add(actions, BorderLayout.SOUTH);

        refreshStatus();
    }

    private static JPanel row(String label, JLabel value) {
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        var key = new JLabel(label + ":");
        key.setFont(key.getFont().deriveFont(Font.BOLD));
        row.add(key);
        row.add(value);
        return row;
    }

    private void refreshStatus() {
        busy(true, "Consultando o runtime…");
        new SwingWorker<ConnectorManagerStatus, Void>() {
            @Override
            protected ConnectorManagerStatus doInBackground() {
                return service.status();
            }

            @Override
            protected void done() {
                try {
                    applyStatus(get());
                    message.setText("Status atualizado.");
                } catch (Exception unavailable) {
                    lastStatus = null;
                    stateValue.setText("Indisponível");
                    message.setText("Não foi possível consultar o runtime local.");
                } finally {
                    busy(false, null);
                }
            }
        }.execute();
    }

    private void runLifecycle(ConnectorManagerLifecycleAction action) {
        if (lifecycleInFlight) {
            return;
        }

        lifecycleInFlight = true;
        busy(true, switch (action) {
            case START -> "Iniciando o Connector…";
            case STOP -> "Parando o Connector…";
            case RESTART -> "Reiniciando o Connector…";
        });

        new SwingWorker<ConnectorManagerLifecycleResult, Void>() {
            @Override
            protected ConnectorManagerLifecycleResult doInBackground() {
                return service.changeRuntimeState(action);
            }

            @Override
            protected void done() {
                try {
                    var result = get();
                    applyStatus(result.status());
                    message.setText(result.message());
                } catch (Exception unavailable) {
                    lastStatus = null;
                    stateValue.setText("Indisponível");
                    message.setText("Não foi possível alterar o estado do Connector.");
                } finally {
                    lifecycleInFlight = false;
                    busy(false, null);
                }
            }
        }.execute();
    }

    private boolean confirmLifecycle(String question) {
        return JOptionPane.showConfirmDialog(
                this,
                question,
                "FINEMP Connector Manager",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void validateConfiguration() {
        busy(true, "Validando a configuração…");
        new SwingWorker<ConnectorManagerActionResult, Void>() {
            @Override
            protected ConnectorManagerActionResult doInBackground() {
                return service.validateInstalledConfiguration();
            }

            @Override
            protected void done() {
                try {
                    message.setText(get().message());
                } catch (Exception unavailable) {
                    message.setText("Não foi possível validar a configuração instalada do Connector.");
                } finally {
                    busy(false, null);
                }
            }
        }.execute();
    }

    private void applyStatus(ConnectorManagerStatus status) {
        lastStatus = status;
        stateValue.setText(switch (status.state()) {
            case NOT_INSTALLED -> "Não instalado";
            case RUNNING -> "Executando";
            case STOPPED -> "Parado";
            case FAILED -> "Falhou";
            case UNKNOWN -> "Indeterminado";
        });
        lastRunValue.setText(status.lastRunTime() == null ? "—" : status.lastRunTime());
        lastResultValue.setText(status.lastTaskResult() == null ? "—" : status.lastTaskResult().toString());
        applyControlAvailability();
    }

    private void applyControlAvailability() {
        var controls = ConnectorManagerControlAvailability.forStatus(lastStatus);
        start.setVisible(controls.controlsVisible());
        stop.setVisible(controls.controlsVisible());
        restart.setVisible(controls.controlsVisible());
        start.setEnabled(controls.startEnabled());
        stop.setEnabled(controls.stopEnabled());
        restart.setEnabled(controls.restartEnabled());
    }

    private void busy(boolean busy, String text) {
        refresh.setEnabled(!busy);
        validate.setEnabled(!busy);
        if (busy) {
            start.setEnabled(false);
            stop.setEnabled(false);
            restart.setEnabled(false);
        } else {
            applyControlAvailability();
        }
        if (text != null) {
            message.setText(text);
        }
    }
}
