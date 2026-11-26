package com.finemp.connector.manager;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class ConnectorManagerFooter extends JPanel {

    ConnectorManagerFooter(
            JButton refresh,
            JButton validate,
            JButton start,
            JButton stop,
            JButton restart,
            JLabel message) {
        setLayout(new GridLayout(0, 1, 0, 4));
        setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));

        var generalActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        generalActions.add(refresh);
        generalActions.add(validate);

        var lifecycleActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lifecycleActions.add(start);
        lifecycleActions.add(stop);
        lifecycleActions.add(restart);

        var feedback = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        feedback.add(message);

        add(generalActions);
        add(lifecycleActions);
        add(feedback);
    }
}
