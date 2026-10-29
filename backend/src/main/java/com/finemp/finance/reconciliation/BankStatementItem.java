package com.finemp.finance.reconciliation;

import com.finemp.company.Company;
import com.finemp.finance.bank.BankAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_bank_statement_items")
public class BankStatementItem {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Column(name="bank_account_id",nullable=false,updatable=false) private UUID bankAccountId;
    @Embedded private StatementValues values;
    @Column(name="identity_key",nullable=false,length=64,updatable=false) private String identityKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private StatementStatus status;
    @Column(name="linked_entry_id") private UUID linkedEntryId;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Version private long version;
    protected BankStatementItem() { }
    public BankStatementItem(Company company,BankAccount bank,StatementValues values,UUID actor) {
        if(company==null || company.getId()==null || bank==null || bank.getId()==null || !company.getId().equals(bank.getCompany().getId()) || values==null || actor==null)throw new IllegalArgumentException("Vínculo de extrato inválido.");
        this.company=company;this.bankAccountId=bank.getId();this.values=values;this.identityKey=values.identityKey();this.status=StatementStatus.PENDING;this.createdBy=actor;
    }
    @PrePersist void created() {createdAt=Instant.now();}
    public UUID getId(){return id;} public Company getCompany(){return company;} public UUID getBankAccountId(){return bankAccountId;}
    public StatementValues getValues(){return values;} public String getIdentityKey(){return identityKey;} public StatementStatus getStatus(){return status;}
    public UUID getLinkedEntryId(){return linkedEntryId;} public UUID getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public long getVersion(){return version;}
    @Override public String toString(){return "BankStatementItem[id="+id+"]";}
}
