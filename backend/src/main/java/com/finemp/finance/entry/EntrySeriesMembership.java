package com.finemp.finance.entry;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.util.UUID;

/** Membership retains the original position/count even when other occurrences are removed. */
@Entity
@Table(name="financial_entry_series_members")
public class EntrySeriesMembership {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Column(name="series_id",nullable=false,updatable=false) private UUID seriesId;
    @Column(name="entry_id",nullable=false,updatable=false) private UUID entryId;
    @Column(name="occurrence_number",nullable=false,updatable=false) private int number;
    @Column(nullable=false,updatable=false) private int quantity;
    @Version private long version;
    protected EntrySeriesMembership() { }
    public EntrySeriesMembership(Company company,FinancialEntrySeries series,FinancialEntry entry,int number) {
        if(company==null || company.getId()==null || series==null || series.getId()==null || series.getCompany()==null || series.getDefinition()==null
                || entry==null || entry.getId()==null || entry.getCompany()==null || !company.getId().equals(series.getCompany().getId())
                || !company.getId().equals(entry.getCompany().getId()) || number<1 || number>series.getDefinition().quantity())
            throw new IllegalArgumentException("Vínculo de série inválido.");
        this.company=company;this.seriesId=series.getId();this.entryId=entry.getId();this.number=number;this.quantity=series.getDefinition().quantity();
    }
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public UUID getSeriesId() {return seriesId;}
    public UUID getEntryId() {return entryId;}
    public int getNumber() {return number;}
    public int getQuantity() {return quantity;}
    public long getVersion() {return version;}
    @Override public String toString() {return "EntrySeriesMembership[id="+id+"]";}
}
