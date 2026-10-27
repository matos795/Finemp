package com.finemp.administration;

import com.finemp.access.UserAccount;
import com.finemp.client.Client;
import com.finemp.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_client_assignment_events")
public class CompanyClientAssignmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_user_account_id", nullable = false)
    private UserAccount assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected CompanyClientAssignmentEvent() {
    }

    public CompanyClientAssignmentEvent(
            Company company,
            Client client,
            UserAccount assignedBy) {
        this.company = company;
        this.client = client;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }
}
