package com.finemp.connector;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConnectorSourceOwnership {
    private final JdbcTemplate jdbc;
    public ConnectorSourceOwnership(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<UUID> activeCompany(UUID firm, UUID source, boolean connectorRoute) {
        return jdbc.query("""
                SELECT company.id FROM accounting_sources source
                JOIN companies company ON company.id = source.company_id
                JOIN clients client ON client.id = company.client_id
                JOIN accounting_firms firm ON firm.id = client.accounting_firm_id
                WHERE source.id = ? AND firm.id = ? AND source.active = TRUE AND client.active = TRUE AND firm.active = TRUE
                  AND source.type = 'QUESTOR' AND (? = FALSE OR source.connection_mode = 'CONNECTOR')
                """, (rows, index) -> rows.getObject(1, UUID.class), source, firm, connectorRoute).stream().findFirst();
    }
}
