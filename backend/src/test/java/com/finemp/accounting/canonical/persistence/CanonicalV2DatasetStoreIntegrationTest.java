package com.finemp.accounting.canonical.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.canonical.v2.CanonicalAccountDailyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalAccountMonthlyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalAccountingAccount;
import com.finemp.accounting.canonical.v2.CanonicalEntrySourceMetadata;
import com.finemp.accounting.canonical.v2.CanonicalOriginCatalogEntry;
import com.finemp.accounting.canonical.v2.SourceAccountCodes;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CanonicalV2DatasetStoreIntegrationTest {

    @Autowired private CanonicalV2DatasetStore store;
    @Autowired private AccountingAccountSourceAttributesRepository accountAttributes;
    @Autowired private AccountingEntrySourceMetadataRepository entryMetadata;
    @Autowired private AccountingDailyDebitCreditTotalsRepository dailyTotals;
    @Autowired private AccountingMonthlyDebitCreditTotalsRepository monthlyTotals;
    @Autowired private AccountingOriginCatalogEntryRepository originCatalog;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;

    @Test
    void shouldPersistCanonicalV2RowsIdempotentlyWithExactDecimals() {
        var fixture = fixture("A");
        var account = canonicalAccount("100", "1.1.01", "Synthetic revenue");
        var metadata = metadata("entry-1", "ORIG");
        var daily = new CanonicalAccountDailyDebitCreditTotals(
                "1", "100", LocalDate.of(2026, 7, 10), "NORMAL",
                new BigDecimal("10.1000"), new BigDecimal("0.0001"));
        var monthly = new CanonicalAccountMonthlyDebitCreditTotals(
                "1", "100", YearMonth.of(2026, 7), "NORMAL",
                new BigDecimal("100.1000"), new BigDecimal("20.0001"));
        var origin = new CanonicalOriginCatalogEntry("ORIG", "Synthetic origin");

        store.persistAccountSourceAttributes(fixture.source().getId(), account);
        store.persistEntrySourceMetadata(fixture.source().getId(), metadata);
        store.persistDailyTotals(fixture.source().getId(), daily);
        store.persistMonthlyTotals(fixture.source().getId(), monthly);
        store.persistOriginCatalogEntry(fixture.source().getId(), origin);

        store.persistAccountSourceAttributes(fixture.source().getId(), account);
        store.persistEntrySourceMetadata(fixture.source().getId(), metadata);
        store.persistDailyTotals(fixture.source().getId(), daily);
        store.persistMonthlyTotals(fixture.source().getId(), monthly);
        store.persistOriginCatalogEntry(fixture.source().getId(), origin);

        assertThat(accountAttributes.count()).isEqualTo(1);
        assertThat(entryMetadata.count()).isEqualTo(1);
        assertThat(dailyTotals.count()).isEqualTo(1);
        assertThat(monthlyTotals.count()).isEqualTo(1);
        assertThat(originCatalog.count()).isEqualTo(1);

        var storedDaily = dailyTotals.findByAccountingSourceIdAndEstablishmentCodeAndSourceAccountIdAndDayAndEntryTypeCode(
                fixture.source().getId(), "1", "100", LocalDate.of(2026, 7, 10), "NORMAL").orElseThrow();
        assertThat(storedDaily.getDebitTotal()).isEqualByComparingTo("10.1000");
        assertThat(storedDaily.getCreditTotal()).isEqualByComparingTo("0.0001");

        var storedMonthly = monthlyTotals
                .findByAccountingSourceIdAndEstablishmentCodeAndSourceAccountIdAndMonthAndEntryTypeCode(
                        fixture.source().getId(), "1", "100", LocalDate.of(2026, 7, 1), "NORMAL")
                .orElseThrow();
        assertThat(storedMonthly.getDebitTotal()).isEqualByComparingTo("100.1000");
        assertThat(storedMonthly.getCreditTotal()).isEqualByComparingTo("20.0001");
    }

    @Test
    void shouldRejectDivergentContentForTheSameNaturalIdentity() {
        var fixture = fixture("B");

        store.persistAccountSourceAttributes(
                fixture.source().getId(),
                canonicalAccount("100", "1.1.01", "Original"));
        store.persistEntrySourceMetadata(
                fixture.source().getId(),
                metadata("entry-1", "ORIG"));
        store.persistDailyTotals(
                fixture.source().getId(),
                new CanonicalAccountDailyDebitCreditTotals(
                        "1", "100", LocalDate.of(2026, 7, 10), "NORMAL",
                        BigDecimal.ONE, BigDecimal.ZERO));
        store.persistMonthlyTotals(
                fixture.source().getId(),
                new CanonicalAccountMonthlyDebitCreditTotals(
                        "1", "100", YearMonth.of(2026, 7), "NORMAL",
                        BigDecimal.TEN, BigDecimal.ZERO));
        store.persistOriginCatalogEntry(
                fixture.source().getId(),
                new CanonicalOriginCatalogEntry("ORIG", "Original"));

        assertThatThrownBy(() -> store.persistAccountSourceAttributes(
                fixture.source().getId(),
                canonicalAccount("100", "9.9", "Changed")))
                .isInstanceOf(CanonicalPersistenceConflictException.class);
        assertThatThrownBy(() -> store.persistEntrySourceMetadata(
                fixture.source().getId(),
                metadata("entry-1", "OTHER")))
                .isInstanceOf(CanonicalPersistenceConflictException.class);
        assertThatThrownBy(() -> store.persistDailyTotals(
                fixture.source().getId(),
                new CanonicalAccountDailyDebitCreditTotals(
                        "1", "100", LocalDate.of(2026, 7, 10), "NORMAL",
                        new BigDecimal("2"), BigDecimal.ZERO)))
                .isInstanceOf(CanonicalPersistenceConflictException.class);
        assertThatThrownBy(() -> store.persistMonthlyTotals(
                fixture.source().getId(),
                new CanonicalAccountMonthlyDebitCreditTotals(
                        "1", "100", YearMonth.of(2026, 7), "NORMAL",
                        new BigDecimal("20"), BigDecimal.ZERO)))
                .isInstanceOf(CanonicalPersistenceConflictException.class);
        assertThatThrownBy(() -> store.persistOriginCatalogEntry(
                fixture.source().getId(),
                new CanonicalOriginCatalogEntry("ORIG", "Changed")))
                .isInstanceOf(CanonicalPersistenceConflictException.class);
    }

    @Test
    void shouldScopeTheSameCanonicalIdentitiesByAccountingSource() {
        var first = fixture("C1");
        var second = fixture("C2");

        store.persistAccountSourceAttributes(first.source().getId(), canonicalAccount("100", "1", "First"));
        store.persistAccountSourceAttributes(second.source().getId(), canonicalAccount("100", "1", "Second"));
        store.persistEntrySourceMetadata(first.source().getId(), metadata("entry-1", "ORIG"));
        store.persistEntrySourceMetadata(second.source().getId(), metadata("entry-1", "ORIG"));

        assertThat(accountAttributes.count()).isEqualTo(2);
        assertThat(entryMetadata.count()).isEqualTo(2);
        assertThat(accountAttributes.findByAccountingSourceIdAndSourceAccountId(first.source().getId(), "100"))
                .get().extracting(AccountingAccountSourceAttributes::getDescription).isEqualTo("First");
        assertThat(accountAttributes.findByAccountingSourceIdAndSourceAccountId(second.source().getId(), "100"))
                .get().extracting(AccountingAccountSourceAttributes::getDescription).isEqualTo("Second");
    }

    private Fixture fixture(String suffix) {
        var company = companies.saveAndFlush(
                new Company("Synthetic C2.2 " + suffix, syntheticCnpj(suffix)));
        var source = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic-" + suffix, true));
        var account = accounts.saveAndFlush(
                new AccountingAccount(source, "100", "1.100", "Synthetic account " + suffix));
        return new Fixture(source, account);
    }

    private CanonicalAccountingAccount canonicalAccount(
            String sourceAccountId,
            String classificationCode,
            String description) {
        return new CanonicalAccountingAccount(
                sourceAccountId,
                classificationCode,
                description,
                new SourceAccountCodes(
                        "ANALYTIC",
                        "NORMAL",
                        "CREDIT",
                        "STANDARD",
                        "YES",
                        null,
                        null));
    }

    private CanonicalEntrySourceMetadata metadata(String sourceEntryKey, String originCode) {
        return new CanonicalEntrySourceMetadata(
                sourceEntryKey,
                Instant.parse("2026-07-10T12:00:00Z"),
                "H1",
                originCode,
                "NORMAL",
                "QUESTOR",
                "B1",
                "DOC1",
                "OG1",
                "TX1",
                "1");
    }

    private String syntheticCnpj(String suffix) {
        int value = Math.abs(suffix.hashCode() % 900000) + 100000;
        return String.format("77%06d0001%02d", value, Math.abs(suffix.hashCode() % 90) + 10);
    }

    private record Fixture(AccountingSource source, AccountingAccount account) {
    }
}
