package com.finemp.finance.center;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="financial_company_settings")
public class FinancialCompanySettings {
    @Id @Column(name="company_id",nullable=false,updatable=false) private UUID companyId;
    @Column(name="require_result_center",nullable=false) private boolean requireResultCenter;
    @Version private Long version;
    protected FinancialCompanySettings() { }
    public FinancialCompanySettings(UUID companyId,boolean requireResultCenter) {
        if(companyId==null)throw new IllegalArgumentException("Empresa inválida.");
        this.companyId=companyId;this.requireResultCenter=requireResultCenter;
    }
    public void requireResultCenter(boolean value) {requireResultCenter=value;}
    public UUID getCompanyId() {return companyId;}
    public boolean isRequireResultCenter() {return requireResultCenter;}
    public Long getVersion() {return version;}
}
