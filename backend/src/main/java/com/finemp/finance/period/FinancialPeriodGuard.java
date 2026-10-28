package com.finemp.finance.period;

import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Always active: disabled producers do not erase an existing period lock. Call under the Company write lock. */
@Component
public class FinancialPeriodGuard {
    private final NamedParameterJdbcTemplate jdbc;
    public FinancialPeriodGuard(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireOpen(UUID company,Collection<YearMonth> periods) {
        if(company==null || periods==null || periods.size()>240)throw new IllegalArgumentException("Períodos financeiros inválidos.");
        var names=periods.stream().map(FinancialPeriodGuard::valid).map(YearMonth::toString).distinct().toList();
        if(names.isEmpty())return;
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_periods WHERE company_id=:company AND closed AND period IN (:periods))",
                Map.of("company",company,"periods",names),Boolean.class)))throw new FinancialPeriodClosedException();
    }
    static YearMonth valid(YearMonth period) {
        if(period==null || period.getYear()<1 || period.getYear()>9999)throw new IllegalArgumentException("Período financeiro inválido.");return period;
    }
}
