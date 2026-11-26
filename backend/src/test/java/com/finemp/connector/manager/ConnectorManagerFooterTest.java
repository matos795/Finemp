package com.finemp.connector.manager;

import static org.assertj.core.api.Assertions.*;

import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class ConnectorManagerFooterTest {

    @Test
    void defaultManagerWidthKeepsLifecycleControlsAndFeedbackInsideDedicatedRows() {
        var refresh = new JButton("Atualizar status");
        var validate = new JButton("Validar configuração");
        var start = new JButton("Iniciar");
        var stop = new JButton("Parar");
        var restart = new JButton("Reiniciar");
        var message = new JLabel("Mensagem de resultado da operação");

        var footer = new ConnectorManagerFooter(refresh, validate, start, stop, restart, message);
        var preferred = footer.getPreferredSize();
        footer.setSize(588, preferred.height);
        footer.doLayout();

        for (var component : footer.getComponents()) {
            assertThat(component.getY()).isGreaterThanOrEqualTo(0);
            assertThat(component.getY() + component.getHeight()).isLessThanOrEqualTo(footer.getHeight());
            if (component instanceof Container container) {
                container.doLayout();
            }
        }

        assertThat(refresh.getParent()).isNotSameAs(start.getParent());
        assertThat(restart.getParent()).isSameAs(start.getParent());
        assertThat(message.getParent()).isNotSameAs(restart.getParent());
        assertThat(restart.getX() + restart.getWidth()).isLessThanOrEqualTo(restart.getParent().getWidth());
        assertThat(message.getY() + message.getHeight()).isLessThanOrEqualTo(message.getParent().getHeight());
        assertThat(footer.getPreferredSize().height)
                .isGreaterThan(refresh.getParent().getPreferredSize().height * 2);
    }
}
