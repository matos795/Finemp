package com.finemp.accounting.v2read;

import com.finemp.accounting.canonical.persistence.AccountingDailyDebitCreditTotals;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Totais D/C exatamente como publicados; nenhuma soma ou saldo é derivado aqui. */
interface AccountingDebitCreditTotalsReadRepository extends Repository<AccountingDailyDebitCreditTotals, UUID> {

    @Query(value = """
            select new com.finemp.accounting.v2read.AccountingDebitCreditTotalsRow(
                totals.establishmentCode, totals.sourceAccountId, account.id, totals.day,
                totals.entryTypeCode, totals.debitTotal, totals.creditTotal)
            from AccountingDailyDebitCreditTotals totals
            join AccountingAccount account
              on account.accountingSource.id = totals.accountingSourceId
             and account.externalAccountId = totals.sourceAccountId
            where totals.accountingSourceId = :sourceId
              and totals.day between :startDate and :endDate
              and (:allEstablishments = true or totals.establishmentCode = :establishmentCode)
              and (:allAccounts = true or totals.sourceAccountId = :sourceAccountId)
            order by totals.day asc, totals.establishmentCode asc, totals.sourceAccountId asc,
                     totals.entryTypeCode asc
            """,
            countQuery = """
            select count(totals)
            from AccountingDailyDebitCreditTotals totals
            where totals.accountingSourceId = :sourceId
              and totals.day between :startDate and :endDate
              and (:allEstablishments = true or totals.establishmentCode = :establishmentCode)
              and (:allAccounts = true or totals.sourceAccountId = :sourceAccountId)
            """)
    Page<AccountingDebitCreditTotalsRow> searchDaily(
            @Param("sourceId") UUID sourceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("allEstablishments") boolean allEstablishments,
            @Param("establishmentCode") String establishmentCode,
            @Param("allAccounts") boolean allAccounts,
            @Param("sourceAccountId") String sourceAccountId,
            Pageable pageable);

    @Query(value = """
            select new com.finemp.accounting.v2read.AccountingDebitCreditTotalsRow(
                totals.establishmentCode, totals.sourceAccountId, account.id, totals.month,
                totals.entryTypeCode, totals.debitTotal, totals.creditTotal)
            from AccountingMonthlyDebitCreditTotals totals
            join AccountingAccount account
              on account.accountingSource.id = totals.accountingSourceId
             and account.externalAccountId = totals.sourceAccountId
            where totals.accountingSourceId = :sourceId
              and totals.month between :startDate and :endDate
              and (:allEstablishments = true or totals.establishmentCode = :establishmentCode)
              and (:allAccounts = true or totals.sourceAccountId = :sourceAccountId)
            order by totals.month asc, totals.establishmentCode asc, totals.sourceAccountId asc,
                     totals.entryTypeCode asc
            """,
            countQuery = """
            select count(totals)
            from AccountingMonthlyDebitCreditTotals totals
            where totals.accountingSourceId = :sourceId
              and totals.month between :startDate and :endDate
              and (:allEstablishments = true or totals.establishmentCode = :establishmentCode)
              and (:allAccounts = true or totals.sourceAccountId = :sourceAccountId)
            """)
    Page<AccountingDebitCreditTotalsRow> searchMonthly(
            @Param("sourceId") UUID sourceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("allEstablishments") boolean allEstablishments,
            @Param("establishmentCode") String establishmentCode,
            @Param("allAccounts") boolean allAccounts,
            @Param("sourceAccountId") String sourceAccountId,
            Pageable pageable);
}
