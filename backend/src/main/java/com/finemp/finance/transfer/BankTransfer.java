package com.finemp.finance.transfer;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_bank_transfers")
public class BankTransfer {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Embedded private BankTransferValues values;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Version private long version;
    protected BankTransfer() { }
    public BankTransfer(Company company,BankTransferValues values,UUID actor) {
        if(company==null || values==null || actor==null)throw new IllegalArgumentException("Transferência inválida.");
        this.company=company;this.values=values;this.createdBy=actor;
    }
    @PrePersist void created() {createdAt=Instant.now();}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public BankTransferValues getValues() {return values;}
    public UUID getCreatedBy() {return createdBy;}
    public Instant getCreatedAt() {return createdAt;}
    public long getVersion() {return version;}
    @Override public String toString() {return "BankTransfer[id="+id+"]";}
}
