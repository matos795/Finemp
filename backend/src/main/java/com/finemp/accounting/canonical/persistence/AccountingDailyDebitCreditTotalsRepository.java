package com.finemp.accounting.canonical.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingDailyDebitCreditTotalsRepository
        extends JpaRepository<AccountingDailyDebitCreditTotals, UUID> {
    Optional<AccountingDailyDebitCreditTotals>
            findByAccountingSourceIdAndEstablishmentCodeAndSourceAccountIdAndDayAndEntryTypeCode(
                    UUID accountingSourceId,
                    String establishmentCode,
                    String sourceAccountId,
                    LocalDate day,
                    String entryTypeCode);
}
