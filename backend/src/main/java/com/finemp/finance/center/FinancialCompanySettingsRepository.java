package com.finemp.finance.center;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialCompanySettingsRepository extends JpaRepository<FinancialCompanySettings,UUID> { }
