package com.finemp.integration.questor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class QuestorConnectionProvider {

    private final QuestorConnectionProperties properties;

    public QuestorConnectionProvider(
            QuestorConnectionProperties properties) {
        this.properties = properties;
    }

    public Connection openConnection() throws SQLException {

        Properties connectionProperties = new Properties();

        connectionProperties.setProperty(
                "user",
                properties.username());

        connectionProperties.setProperty(
                "password",
                properties.password());

        if (properties.encoding() != null
                && !properties.encoding().isBlank()) {

            connectionProperties.setProperty(
                    "encoding",
                    properties.encoding());
        }

        if (properties.charSet() != null
                && !properties.charSet().isBlank()) {

            connectionProperties.setProperty(
                    "charSet",
                    properties.charSet());
        }

        return DriverManager.getConnection(
                properties.jdbcUrl(),
                connectionProperties);
    }
}