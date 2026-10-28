package com.finemp.finance.entry;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_entry_series")
public class FinancialEntrySeries {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Embedded private EntrySeriesDefinition definition;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Version private long version;
    protected FinancialEntrySeries() { }
    public FinancialEntrySeries(Company company,EntrySeriesDefinition definition,UUID actor) {
        if(company==null || definition==null || actor==null)throw new IllegalArgumentException("Série financeira inválida.");
        this.company=company;this.definition=definition;this.createdBy=actor;
    }
    @PrePersist void created() {createdAt=Instant.now();}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public EntrySeriesDefinition getDefinition() {return definition;}
    public UUID getCreatedBy() {return createdBy;}
    public Instant getCreatedAt() {return createdAt;}
    public long getVersion() {return version;}
    @Override public String toString() {return "FinancialEntrySeries[id="+id+"]";}
}
