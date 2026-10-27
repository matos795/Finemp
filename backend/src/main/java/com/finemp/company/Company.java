package com.finemp.company;

import com.finemp.client.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;
import java.util.Objects;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    protected Company() {
    }

    public Company(String legalName, String cnpj) {
        this(null, legalName, cnpj);
    }

    public Company(
            Client client,
            String legalName,
            String cnpj) {
        this.client = client;
        this.legalName = legalName;
        this.cnpj = cnpj;
    }

    public UUID getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public void assignToClient(Client client) {
        if (this.client != null) {
            throw new IllegalStateException("Company is already assigned to a client.");
        }
        this.client = Objects.requireNonNull(client);
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCnpj() {
        return cnpj;
    }
}
