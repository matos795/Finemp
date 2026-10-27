package com.finemp.connector;

import com.finemp.access.*;
import com.finemp.accounting.sync.execution.*;
import com.finemp.identity.OpaqueAccountToken;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "finemp.connector", name = "enabled", havingValue = "true")
public class ConnectorCredentialVerifier implements SyncAgentAuthorization {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConnectorSourceOwnership ownership;
    private final AccountingFirmPermissionAuthorizationService firms;
    private final CompanyPermissionAuthorizationService companies;
    public ConnectorCredentialVerifier(JdbcTemplate jdbc, Clock clock, ConnectorSourceOwnership ownership,
            AccountingFirmPermissionAuthorizationService firms, CompanyPermissionAuthorizationService companies) {
        this.jdbc = jdbc; this.clock = clock; this.ownership = ownership; this.firms = firms; this.companies = companies;
    }
    @Transactional(readOnly = true)
    public Optional<ConnectorIdentity> authenticate(String raw) {
        if (raw == null || raw.length() != 43 || !raw.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return lookup("credential.token_hash = ?", List.of(OpaqueAccountToken.hash(raw)));
    }
    /** Caller must hold the source lock for an atomic write, including against rotation/revocation. */
    @Transactional(readOnly = true)
    public boolean isCurrent(ConnectorIdentity identity) {
        if (identity == null) return false;
        return lookup("credential.id = ? AND credential.version = ?", List.of(identity.credentialId(), identity.version()))
                .filter(identity::equals).isPresent();
    }
    @Override
    @Transactional(readOnly = true)
    public boolean isCurrent(SyncClaimAgent agent, UUID sourceId) {
        if (agent == null || !agent.isConnector()) return false;
        return lookup("credential.id = ? AND credential.version = ?", List.of(agent.credentialId(), agent.credentialVersion()))
                .filter(identity -> identity.accountingSourceId().equals(sourceId)).isPresent();
    }
    private Optional<ConnectorIdentity> lookup(String predicate, List<Object> arguments) {
        var parameters = new ArrayList<Object>(arguments);
        var now = Timestamp.from(clock.instant()); parameters.add(now); parameters.add(now);
        var identity = jdbc.query("SELECT credential.id, credential.accounting_firm_id, credential.accounting_source_id, credential.issued_by_user_id, credential.version"
                + " FROM connector_credentials credential WHERE " + predicate
                + " AND credential.revoked_at IS NULL AND credential.created_at <= ? AND credential.expires_at > ?",
                (rows, index) -> new ConnectorIdentity(rows.getObject(1, UUID.class), rows.getObject(2, UUID.class),
                        rows.getObject(3, UUID.class), rows.getObject(4, UUID.class), rows.getLong(5)), parameters.toArray()).stream().findFirst();
        return identity.filter(value -> ownership.activeCompany(value.accountingFirmId(), value.accountingSourceId(), true)
                .filter(company -> firms.hasPermission(value.issuedByUserId(), value.accountingFirmId(), AccountingFirmPermission.CONNECTOR_MANAGE)
                        && companies.hasFreshPermission(value.issuedByUserId(), company, CompanyPermission.QUESTOR_SYNC)).isPresent());
    }
}
