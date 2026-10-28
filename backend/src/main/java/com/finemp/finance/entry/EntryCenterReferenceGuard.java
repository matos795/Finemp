package com.finemp.finance.entry;

import com.finemp.finance.center.ResultCenterUsageGuard;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Historical references remain protected even when entry capture is disabled. */
@Component("financialEntryCenterReferences")
public class EntryCenterReferenceGuard implements ResultCenterUsageGuard {
    private final JdbcTemplate jdbc;
    public EntryCenterReferenceGuard(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override public boolean hasEntries(UUID company,UUID center) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_entries WHERE company_id=? AND result_center_id=?)",Boolean.class,company,center));
    }
    @Override public boolean hasReferences(UUID company,UUID center) {return hasEntries(company,center);}
}
