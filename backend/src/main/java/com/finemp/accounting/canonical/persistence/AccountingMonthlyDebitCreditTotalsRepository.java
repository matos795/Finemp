package com.finemp.accounting.canonical.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingMonthlyDebitCreditTotalsRepository
        extends JpaRepository<AccountingMonthlyDebitCreditTotals, UUID> {
    Optional<AccountingMonthlyDebitCreditTotals>
            findByAccountingSourceIdAndEstablishmentCodeAndSourceAccountIdAndMonthAndEntryTypeCode(
                    UUID accountingSourceId,
                    String establishmentCode,
                    String sourceAccountId,
                    LocalDate month,
                    String entryTypeCode);
}
