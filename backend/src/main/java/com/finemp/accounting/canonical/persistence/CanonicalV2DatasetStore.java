package com.finemp.accounting.canonical.persistence;

import com.finemp.accounting.canonical.v2.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CanonicalV2DatasetStore {
    private final AccountingAccountSourceAttributesRepository accountAttributes;
    private final AccountingEntrySourceMetadataRepository entryMetadata;
    private final AccountingDailyDebitCreditTotalsRepository dailyTotals;
    private final AccountingMonthlyDebitCreditTotalsRepository monthlyTotals;
    private final AccountingOriginCatalogEntryRepository originCatalog;

    public CanonicalV2DatasetStore(
            AccountingAccountSourceAttributesRepository accountAttributes,
            AccountingEntrySourceMetadataRepository entryMetadata,
            AccountingDailyDebitCreditTotalsRepository dailyTotals,
            AccountingMonthlyDebitCreditTotalsRepository monthlyTotals,
            AccountingOriginCatalogEntryRepository originCatalog) {
        this.accountAttributes = accountAttributes;
        this.entryMetadata = entryMetadata;
        this.dailyTotals = dailyTotals;
        this.monthlyTotals = monthlyTotals;
        this.originCatalog = originCatalog;
    }

    public AccountingAccountSourceAttributes persistAccountSourceAttributes(
            UUID accountingSourceId,
            CanonicalAccountingAccount account) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(account.codes(), "account.codes");

        var existing = accountAttributes.findByAccountingSourceIdAndSourceAccountId(
                accountingSourceId,
                account.sourceAccountId());
        if (existing.isPresent()) {
            if (!sameAccountAttributes(existing.get(), account)) {
                throw new CanonicalPersistenceConflictException();
            }
            return existing.get();
        }

        var codes = account.codes();
        return accountAttributes.saveAndFlush(new AccountingAccountSourceAttributes(
                accountingSourceId,
                account.sourceAccountId(),
                account.classificationCode(),
                codes.accountType(),
                codes.calculationType(),
                codes.balanceNature(),
                codes.balanceControl(),
                codes.showInBalanceSheet(),
                codes.nativeDreCode(),
                codes.nativeDreAccount()));
    }

    public AccountingEntrySourceMetadata persistEntrySourceMetadata(
            UUID accountingSourceId,
            CanonicalEntrySourceMetadata metadata) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(metadata, "metadata");

        var existing = entryMetadata.findByAccountingSourceIdAndSourceEntryKey(
                accountingSourceId,
                metadata.sourceEntryKey());
        if (existing.isPresent()) {
            if (!sameEntryMetadata(existing.get(), metadata)) {
                throw new CanonicalPersistenceConflictException();
            }
            return existing.get();
        }

        return entryMetadata.saveAndFlush(new AccountingEntrySourceMetadata(
                accountingSourceId,
                metadata.sourceEntryKey(),
                metadata.sourceRecordedAt(),
                metadata.historyCode(),
                metadata.originCode(),
                metadata.entryTypeCode(),
                metadata.dataOriginCode(),
                metadata.batchCode(),
                metadata.documentNumber(),
                metadata.operationGroupKey(),
                metadata.transactionKey(),
                metadata.originEstablishmentCode()));
    }

    public AccountingDailyDebitCreditTotals persistDailyTotals(
            UUID accountingSourceId,
            CanonicalAccountDailyDebitCreditTotals total) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(total, "total");

        var existing = dailyTotals
                .findByAccountingSourceIdAndEstablishmentCodeAndSourceAccountIdAndDayAndEntryTypeCode(
                        accountingSourceId,
                        total.establishmentCode(),
                        total.sourceAccountId(),
                        total.day(),
                        total.entryTypeCode());
        if (existing.isPresent()) {
            if (!sameDailyTotal(existing.get(), total)) {
                throw new CanonicalPersistenceConflictException();
            }
            return existing.get();
        }

        return dailyTotals.saveAndFlush(new AccountingDailyDebitCreditTotals(
                accountingSourceId,
                total.establishmentCode(),
                total.sourceAccountId(),
                total.day(),
                total.entryTypeCode(),
                total.debitTotal(),
                total.creditTotal()));
    }

    public AccountingMonthlyDebitCreditTotals persistMonthlyTotals(
            UUID accountingSourceId,
            CanonicalAccountMonthlyDebitCreditTotals total) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(total, "total");
        var month = total.month().atDay(1);

        var existing = monthlyTotals
                .findByAccountingSourceIdAndEstablishmentCodeAndSourceAccountIdAndMonthAndEntryTypeCode(
                        accountingSourceId,
                        total.establishmentCode(),
                        total.sourceAccountId(),
                        month,
                        total.entryTypeCode());
        if (existing.isPresent()) {
            if (!sameMonthlyTotal(existing.get(), total)) {
                throw new CanonicalPersistenceConflictException();
            }
            return existing.get();
        }

        return monthlyTotals.saveAndFlush(new AccountingMonthlyDebitCreditTotals(
                accountingSourceId,
                total.establishmentCode(),
                total.sourceAccountId(),
                month,
                total.entryTypeCode(),
                total.debitTotal(),
                total.creditTotal()));
    }

    public AccountingOriginCatalogEntry persistOriginCatalogEntry(
            UUID accountingSourceId,
            CanonicalOriginCatalogEntry entry) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(entry, "entry");

        var existing = originCatalog.findByAccountingSourceIdAndOriginCode(
                accountingSourceId,
                entry.originCode());
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getDescription(), entry.description())) {
                throw new CanonicalPersistenceConflictException();
            }
            return existing.get();
        }

        return originCatalog.saveAndFlush(new AccountingOriginCatalogEntry(
                accountingSourceId,
                entry.originCode(),
                entry.description()));
    }

    private boolean sameAccountAttributes(
            AccountingAccountSourceAttributes stored,
            CanonicalAccountingAccount incoming) {
        var codes = incoming.codes();
        return Objects.equals(stored.getClassificationCode(), incoming.classificationCode())
                && Objects.equals(stored.getAccountType(), codes.accountType())
                && Objects.equals(stored.getCalculationType(), codes.calculationType())
                && Objects.equals(stored.getBalanceNature(), codes.balanceNature())
                && Objects.equals(stored.getBalanceControl(), codes.balanceControl())
                && Objects.equals(stored.getShowInBalanceSheet(), codes.showInBalanceSheet())
                && Objects.equals(stored.getNativeDreCode(), codes.nativeDreCode())
                && Objects.equals(stored.getNativeDreAccount(), codes.nativeDreAccount());
    }

    private boolean sameEntryMetadata(
            AccountingEntrySourceMetadata stored,
            CanonicalEntrySourceMetadata incoming) {
        return Objects.equals(stored.getSourceRecordedAt(), incoming.sourceRecordedAt())
                && Objects.equals(stored.getHistoryCode(), incoming.historyCode())
                && Objects.equals(stored.getOriginCode(), incoming.originCode())
                && Objects.equals(stored.getEntryTypeCode(), incoming.entryTypeCode())
                && Objects.equals(stored.getDataOriginCode(), incoming.dataOriginCode())
                && Objects.equals(stored.getBatchCode(), incoming.batchCode())
                && Objects.equals(stored.getDocumentNumber(), incoming.documentNumber())
                && Objects.equals(stored.getOperationGroupKey(), incoming.operationGroupKey())
                && Objects.equals(stored.getTransactionKey(), incoming.transactionKey())
                && Objects.equals(stored.getOriginEstablishmentCode(), incoming.originEstablishmentCode());
    }

    private boolean sameDailyTotal(
            AccountingDailyDebitCreditTotals stored,
            CanonicalAccountDailyDebitCreditTotals incoming) {
        return sameMoney(stored.getDebitTotal(), incoming.debitTotal())
                && sameMoney(stored.getCreditTotal(), incoming.creditTotal());
    }

    private boolean sameMonthlyTotal(
            AccountingMonthlyDebitCreditTotals stored,
            CanonicalAccountMonthlyDebitCreditTotals incoming) {
        return sameMoney(stored.getDebitTotal(), incoming.debitTotal())
                && sameMoney(stored.getCreditTotal(), incoming.creditTotal());
    }

    private boolean sameMoney(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }
}
