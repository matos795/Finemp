package com.finemp.finance.withholding;

import com.finemp.company.Company;
import com.finemp.finance.entry.FinancialEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_tax_withholdings")
public class FinancialWithholding {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Column(name="entry_id",nullable=false,updatable=false) private UUID entryId;
    @Embedded private WithholdingValues values;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected FinancialWithholding() { }
    public FinancialWithholding(Company company,FinancialEntry entry,WithholdingValues values,UUID actor) {
        if(company==null || company.getId()==null || entry==null || entry.getId()==null || !company.getId().equals(entry.getCompany().getId()))throw invalid();
        this.company=company;this.entryId=entry.getId();this.createdBy=actor;replace(values,actor);
    }
    public void replace(WithholdingValues values,UUID actor) {if(values==null || actor==null)throw invalid();this.values=values;this.updatedBy=actor;}
    @PrePersist void created() {createdAt=Instant.now();updatedAt=createdAt;}
    @PreUpdate void updated() {updatedAt=Instant.now();}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public UUID getEntryId() {return entryId;}
    public WithholdingValues getValues() {return values;}
    public UUID getCreatedBy() {return createdBy;}
    public UUID getUpdatedBy() {return updatedBy;}
    public Instant getCreatedAt() {return createdAt;}
    public Instant getUpdatedAt() {return updatedAt;}
    public long getVersion() {return version;}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Vínculo de retenção inválido.");}
    @Override public String toString() {return "FinancialWithholding[id="+id+"]";}
}
