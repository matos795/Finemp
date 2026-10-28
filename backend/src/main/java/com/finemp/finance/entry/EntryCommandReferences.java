package com.finemp.finance.entry;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Scalar reads deliberately bypass stale entities already present in a caller's persistence context. */
@Component
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
public class EntryCommandReferences {
    private final JdbcTemplate jdbc;
    public EntryCommandReferences(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    void validateBank(UUID company,UUID bank) {
        require(bank!=null && exists("SELECT EXISTS(SELECT 1 FROM financial_bank_accounts WHERE company_id=? AND id=?)",company,bank));
    }
    void validate(UUID company,FinancialEntryCommandData data,FinancialEntryValues previous) {
        validateBank(company,data.bankAccountId());
        require(exists("SELECT EXISTS(SELECT 1 FROM financial_categories WHERE company_id=? AND id=? AND kind='SUBCATEGORY' AND direction=?)",
                company,data.categoryId(),data.direction().name()));
        if(data.partnerId()!=null)require(exists("SELECT EXISTS(SELECT 1 FROM business_partners WHERE company_id=? AND id=? AND role IN ('BOTH',?))",
                company,data.partnerId(),data.direction()==com.finemp.finance.category.FinancialDirection.INCOME?"CUSTOMER":"SUPPLIER"));
        if(previous==null && data.resultCenterId()==null)
            require(!exists("SELECT EXISTS(SELECT 1 FROM financial_company_settings WHERE company_id=? AND require_result_center)",company));
        if(data.resultCenterId()!=null) {
            boolean unchanged=previous!=null && data.resultCenterId().equals(previous.resultCenterId());
            require(exists("""
                    SELECT EXISTS(SELECT 1 FROM operational_result_centers c WHERE c.company_id=? AND c.id=?
                      AND (? OR c.active) AND NOT EXISTS(SELECT 1 FROM operational_result_centers child WHERE child.company_id=c.company_id AND child.parent_id=c.id))
                    """,company,data.resultCenterId(),unchanged));
        }
    }
    private boolean exists(String sql,Object... args) {return Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,args));}
    private void require(boolean valid) {if(!valid)throw new IllegalArgumentException("Referências financeiras inválidas para esta empresa.");}
}
