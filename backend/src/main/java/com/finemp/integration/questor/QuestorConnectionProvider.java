package com.finemp.integration.questor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

public class QuestorConnectionProvider {

    private final Supplier<QuestorConnectionProperties> properties;

    public QuestorConnectionProvider(
            QuestorConnectionProperties properties) {
        this(() -> Objects.requireNonNull(properties));
    }

    public QuestorConnectionProvider(Supplier<QuestorConnectionProperties> properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public Connection openConnection() throws SQLException {
        var current = Objects.requireNonNull(properties.get());
        return DriverManager.getConnection(
                current.jdbcUrl(),
                current.username(),
                current.password());
    }
}
