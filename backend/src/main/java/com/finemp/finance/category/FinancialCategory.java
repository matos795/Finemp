package com.finemp.finance.category;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="financial_categories")
public class FinancialCategory {
    public enum Kind { GROUP, SUBCATEGORY }
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Column(name="parent_id",updatable=false) private UUID parentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private FinancialDirection direction;
    @Column(nullable=false,length=64,updatable=false) private String code;
    @Column(name="sequence_number",nullable=false,updatable=false) private long sequenceNumber;
    @Column(name="sort_key",nullable=false,length=64,updatable=false) private String sortKey;
    @Column(nullable=false,length=255) private String name;
    @Column(name="search_name",nullable=false,length=510) private String searchName;
    @Enumerated(EnumType.STRING) @Column(name="cash_activity",length=20) private CashActivity cashActivity;
    @Enumerated(EnumType.STRING) @Column(name="dre_line",length=10) private OperationalDreLine dreLine;
    @Enumerated(EnumType.STRING) @Column(name="cost_behavior",length=20) private CostBehavior costBehavior;
    @Version private long version;
    protected FinancialCategory() { }
    public static FinancialCategory group(Company company,FinancialDirection direction,long sequence,String name) {
        if(company==null || direction==null || sequence<1)throw invalid();
        var row=new FinancialCategory();row.company=company;row.direction=direction;row.kind=Kind.GROUP;row.sequenceNumber=sequence;
        row.code=(direction==FinancialDirection.INCOME?"1.":"2.")+sequence;
        row.sortKey=(direction==FinancialDirection.INCOME?"1/":"2/")+padded(sequence);
        row.rename(name);row.cashActivity=CashActivity.OPERATING;
        row.dreLine=direction==FinancialDirection.INCOME?OperationalDreLine.REC:OperationalDreLine.DADM;
        return row;
    }
    public static FinancialCategory subcategory(FinancialCategory parent,long sequence,String name) {
        if(parent==null || parent.kind!=Kind.GROUP || parent.id==null || sequence<1)throw invalid();
        var row=new FinancialCategory();row.company=parent.company;row.parentId=parent.id;row.direction=parent.direction;
        row.kind=Kind.SUBCATEGORY;row.sequenceNumber=sequence;row.code=parent.code+"."+sequence;
        row.sortKey=parent.sortKey+"/"+padded(sequence);row.rename(name);return row;
    }
    public void updateGroup(String name,CashActivity activity,OperationalDreLine dre,CostBehavior cost) {
        if(kind!=Kind.GROUP || activity==null || dre==null)throw invalid();
        rename(name);cashActivity=activity;dreLine=dre;costBehavior=cost;
    }
    public void updateSubcategory(FinancialCategory parent,String name,CashActivity activity,OperationalDreLine dre,CostBehavior cost) {
        requireParent(parent);rename(name);cashActivity=activity;
        // The homologated edit handler stores the group's current DRE when "Do grupo" is selected.
        dreLine=dre==null?parent.dreLine:dre;costBehavior=cost;
    }
    /** Só nome (e busca); a classificação, inclusive o DRE da subcategoria, fica como está. */
    public void renameOnly(String name) {rename(name);}
    public EffectiveClassification effective(FinancialCategory parent) {
        if(kind==Kind.GROUP && parent!=null)throw invalid();
        if(kind==Kind.SUBCATEGORY)requireParent(parent);
        return FinancialCategoryClassification.resolve(direction,cashActivity,dreLine,costBehavior,
                parent==null?null:parent.cashActivity,parent==null?null:parent.dreLine,parent==null?null:parent.costBehavior);
    }
    private void requireParent(FinancialCategory parent) {
        if(kind!=Kind.SUBCATEGORY || parent==null || parent.kind!=Kind.GROUP || !parentId.equals(parent.id)
                || !company.getId().equals(parent.company.getId()))throw invalid();
    }
    private void rename(String name) {
        if(name==null || name.isBlank() || name.length()>255)throw invalid();
        this.name=name.trim();this.searchName=searchKey(this.name);
    }
    private static String padded(long value) {String text=Long.toString(value);return "0".repeat(19-text.length())+text;}
    static String searchKey(String value) {return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT).trim();}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Categoria financeira inválida.");}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public UUID getParentId() {return parentId;}
    public Kind getKind() {return kind;}
    public FinancialDirection getDirection() {return direction;}
    public String getCode() {return code;}
    public long getSequenceNumber() {return sequenceNumber;}
    public String getName() {return name;}
    public CashActivity getCashActivity() {return cashActivity;}
    public OperationalDreLine getDreLine() {return dreLine;}
    public CostBehavior getCostBehavior() {return costBehavior;}
    public long getVersion() {return version;}
    public record EffectiveClassification(CashActivity cashActivity,OperationalDreLine dreLine,CostBehavior costBehavior) { }
    @Override public String toString() {return "FinancialCategory[id="+id+"]";}
}
