package com.finemp.connector;

import com.finemp.access.*;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.administration.*;
import com.finemp.identity.OpaqueAccountToken;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.SyncControlTransactionGuard;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix = "finemp.connector", name = "enabled", havingValue = "true")
public class ConnectorCredentialService {
    private final ConnectorCredentialRepository credentials;
    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;
    private final ConnectorSourceOwnership ownership;
    private final AccountingFirmPermissionAuthorizationService firms;
    private final CompanyPermissionAuthorizationService companies;
    private final SyncControlTransactionGuard transactions;
    private final EntityManager entities;
    private final Clock clock;
    private final int lifetimeDays;
    public ConnectorCredentialService(ConnectorCredentialRepository credentials, AccountingSourceRepository sources,
            SyncRunRepository runs, ConnectorSourceOwnership ownership, AccountingFirmPermissionAuthorizationService firms,
            CompanyPermissionAuthorizationService companies, SyncControlTransactionGuard transactions,
            EntityManager entities, Clock clock, @Value("${finemp.connector.credential-lifetime-days:90}") int lifetimeDays) {
        if (lifetimeDays < 1 || lifetimeDays > 365) throw new IllegalArgumentException("Invalid connector credential lifetime.");
        this.credentials = credentials; this.sources = sources; this.runs = runs; this.ownership = ownership;
        this.firms = firms; this.companies = companies; this.transactions = transactions; this.entities = entities;
        this.clock = clock; this.lifetimeDays = lifetimeDays;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConnectorSecretResponse issue(UUID actor, UUID firm, CreateConnectorCredentialRequest request) {
        transactions.requireReadCommitted(); requireManage(actor, firm);
        if (request == null || request.accountingSourceId() == null) throw new IllegalArgumentException("Invalid connector request.");
        var source = sources.findLockedForSyncById(request.accountingSourceId()).orElseThrow(() -> new SaasResourceNotFoundException("Accounting source"));
        requireManage(actor, firm); requireSourcePermission(actor, firm, source.getId());
        if (credentials.existsByAccountingSourceIdAndRevokedAtIsNull(source.getId())
                || runs.existsByAccountingSourceIdAndStatusIn(source.getId(), Set.of(SyncRunStatus.REQUESTED, SyncRunStatus.RUNNING)))
            throw new ConnectorCredentialConflictException();
        var secret = OpaqueAccountToken.generate(); Instant now = clock.instant();
        var credential = credentials.save(new ConnectorCredential(firm, source.getId(), request.label(), secret.hash(), actor,
                now, now.plus(Duration.ofDays(lifetimeDays))));
        source.reserveConnectorRoute();
        entities.persist(new ConnectorCredentialEvent(credential, ConnectorCredentialEvent.Action.ISSUED, actor, now));
        credentials.flush();
        return new ConnectorSecretResponse(ConnectorCredentialResponse.from(credential), secret.rawValue());
    }
    @Transactional(readOnly = true)
    public PagedResponse<ConnectorCredentialResponse> list(UUID actor, UUID firm, String search, int page, int size) {
        requireManage(actor, firm);
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 200)) throw new IllegalArgumentException("Invalid connector page.");
        return PagedResponse.from(credentials.search(firm, search == null ? "" : search.trim(),
                PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")))), ConnectorCredentialResponse::from);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConnectorSecretResponse rotate(UUID actor, UUID firm, UUID id) {
        transactions.requireReadCommitted(); requireManage(actor, firm);
        var credential = lock(firm, id); requireManage(actor, firm);
        requireSourcePermission(actor, firm, credential.getAccountingSourceId());
        if (credential.getRevokedAt() != null) throw new ConnectorCredentialConflictException();
        var secret = OpaqueAccountToken.generate(); Instant now = clock.instant();
        credential.rotate(secret.hash(), actor, now, now.plus(Duration.ofDays(lifetimeDays)));
        entities.persist(new ConnectorCredentialEvent(credential, ConnectorCredentialEvent.Action.ROTATED, actor, now));
        credentials.flush();
        return new ConnectorSecretResponse(ConnectorCredentialResponse.from(credential), secret.rawValue());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(UUID actor, UUID firm, UUID id) {
        transactions.requireReadCommitted(); requireManage(actor, firm);
        var credential = lock(firm, id); requireManage(actor, firm);
        if (credential.revoke(clock.instant()))
            entities.persist(new ConnectorCredentialEvent(credential, ConnectorCredentialEvent.Action.REVOKED, actor, clock.instant()));
    }
    private ConnectorCredential lock(UUID firm, UUID id) {
        var existing = credentials.findById(id).filter(value -> value.getAccountingFirmId().equals(firm))
                .orElseThrow(() -> new SaasResourceNotFoundException("Connector credential"));
        sources.findLockedForSyncById(existing.getAccountingSourceId()).orElseThrow(() -> new SaasResourceNotFoundException("Accounting source"));
        var credential = credentials.findLocked(id, firm).orElseThrow(() -> new SaasResourceNotFoundException("Connector credential"));
        entities.refresh(credential); // Discard a pre-lock managed snapshot before rotation/revocation.
        return credential;
    }
    private void requireManage(UUID actor, UUID firm) {
        if (!firms.hasPermission(actor, firm, AccountingFirmPermission.CONNECTOR_MANAGE)) throw new OrganizationAccessDeniedException();
    }
    private void requireSourcePermission(UUID actor, UUID firm, UUID source) {
        var company = ownership.activeCompany(firm, source, false).orElseThrow(() -> new SaasResourceNotFoundException("Accounting source"));
        if (!companies.hasFreshPermission(actor, company, CompanyPermission.QUESTOR_SYNC)) throw new OrganizationAccessDeniedException();
    }
}
