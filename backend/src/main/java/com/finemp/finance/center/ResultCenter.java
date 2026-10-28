package com.finemp.finance.center;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="operational_result_centers")
public class ResultCenter {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Column(name="parent_id",updatable=false) private UUID parentId;
    @Column(nullable=false,length=20,updatable=false) private String code;
    @Column(nullable=false,length=80) private String name;
    @Column(name="search_name",nullable=false,length=160) private String searchName;
    @Column(name="sort_key",nullable=false,length=300,updatable=false) private String sortKey;
    @Column(nullable=false) private boolean active;
    @Version private long version;
    protected ResultCenter() { }
    public ResultCenter(Company company,String code,UUID parentId,String name,boolean active) {
        if(company==null)throw invalid();
        var parsed=new ResultCenterCode(code);
        if((parsed.parentCode()==null)!=(parentId==null))throw invalid();
        this.company=company;this.code=parsed.value();this.sortKey=parsed.sortKey();this.parentId=parentId;replace(name,active);
    }
    public void replace(String name,boolean active) {
        if(name==null || name.trim().length()<2 || name.length()>80)throw invalid();
        this.name=name.trim();this.active=active;this.searchName=searchKey(this.name);
    }
    public void deactivate() {active=false;}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public UUID getParentId() {return parentId;}
    public String getCode() {return code;}
    public String getName() {return name;}
    public boolean isActive() {return active;}
    public long getVersion() {return version;}
    static String searchKey(String value) {
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT).trim();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Centro de resultado inválido.");}
    @Override public String toString() {return "ResultCenter[id="+id+"]";}
}
