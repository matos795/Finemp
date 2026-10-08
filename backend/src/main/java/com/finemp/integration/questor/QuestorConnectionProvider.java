package com.finemp.integration.questor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class QuestorConnectionProvider {

    private final QuestorConnectionProperties properties;

    public QuestorConnectionProvider(
            QuestorConnectionProperties properties) {
        this.properties = properties;
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                properties.jdbcUrl(),
                properties.username(),
                properties.password());
    }
}