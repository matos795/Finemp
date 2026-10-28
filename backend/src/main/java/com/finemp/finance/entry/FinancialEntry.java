package com.finemp.finance.entry;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_entries")
public class FinancialEntry {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Embedded private FinancialEntryValues values;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected FinancialEntry() { }
    public FinancialEntry(Company company,FinancialEntryValues values,UUID actor) {
        if(company==null)throw invalid();this.company=company;this.createdBy=actor;replace(values,actor);
    }
    public void replace(FinancialEntryValues values,UUID actor) {
        if(values==null || actor==null)throw invalid();this.values=values;this.updatedBy=actor;
    }
    @PrePersist void created() {createdAt=Instant.now();updatedAt=createdAt;}
    @PreUpdate void updated() {updatedAt=Instant.now();}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public FinancialEntryValues getValues() {return values;}
    public UUID getCreatedBy() {return createdBy;}
    public UUID getUpdatedBy() {return updatedBy;}
    public Instant getCreatedAt() {return createdAt;}
    public Instant getUpdatedAt() {return updatedAt;}
    public long getVersion() {return version;}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Lançamento financeiro inválido.");}
    @Override public String toString() {return "FinancialEntry[id="+id+"]";}
}
