package com.finemp.finance.bank;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/** Operational account, separate from AccountingAccount and external ERP configuration. */
@Entity
@Table(name="financial_bank_accounts")
public class BankAccount {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Column(nullable=false,length=255) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private BankAccountType type;
    @Column(name="opening_balance",nullable=false,precision=19,scale=2) private BigDecimal openingBalance;
    @Column(name="opening_date",nullable=false) private LocalDate openingDate;
    @Column(nullable=false) private boolean active;
    @Version private long version;
    protected BankAccount(){ }
    public BankAccount(Company company,String name,BankAccountType type,BigDecimal openingBalance,LocalDate openingDate,boolean active){
        if(company==null)throw invalid();this.company=company;replace(name,type,openingBalance,openingDate,active);
    }
    public void replace(String name,BankAccountType type,BigDecimal openingBalance,LocalDate openingDate,boolean active){
        if(name==null||name.isBlank()||name.trim().length()>255||type==null||openingDate==null||openingDate.getYear()<1||openingDate.getYear()>9999)throw invalid();
        BigDecimal money=money(openingBalance);
        this.name=name.trim();this.type=type;this.openingBalance=money;this.openingDate=openingDate;this.active=active;
    }
    private BigDecimal money(BigDecimal value){
        if(value==null)throw invalid();
        try{
            var compact=value.signum()==0?BigDecimal.ZERO:value.stripTrailingZeros();
            if(compact.scale()>2||(long)compact.precision()-compact.scale()>17)throw invalid();
            return compact.setScale(2,RoundingMode.UNNECESSARY);
        }catch(ArithmeticException invalid){throw invalid();}
    }
    public void deactivate(){active=false;}
    public UUID getId(){return id;}
    public Company getCompany(){return company;}
    public String getName(){return name;}
    public BankAccountType getType(){return type;}
    public BigDecimal getOpeningBalance(){return openingBalance;}
    public LocalDate getOpeningDate(){return openingDate;}
    public boolean isActive(){return active;}
    public long getVersion(){return version;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Dados da conta financeira inválidos.");}
    @Override public String toString(){return "BankAccount[id="+id+"]";}
}
