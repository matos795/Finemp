package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.source.AccountingConnectionMode;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountingFileImportUiSupportServiceIntegrationTest {

    @Autowired private AccountingFileImportUiSupportService support;
    @Autowired private AccountingFileImportReceiptRepository receipts;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;

    private UserAccount actor;
    private Company company;

    @BeforeEach
    void prepare() {
        actor = users.saveAndFlush(new UserAccount(
                "Synthetic File UI",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        company = companies.saveAndFlush(new Company(
                "Synthetic File UI Company",
                "92929292000192"));
        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.ACCOUNTING_IMPORT_MANAGE)));
    }

    @Test
    void prepareSourceIsIdempotentAndCreatesUploadFileImportSource() {
        var first = support.prepareSource(actor.getId(), company.getId());
        var second = support.prepareSource(actor.getId(), company.getId());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.type()).isEqualTo(AccountingSourceType.FILE_IMPORT);
        assertThat(first.connectionMode()).isEqualTo(AccountingConnectionMode.UPLOAD);
        assertThat(first.active()).isTrue();

        assertThat(sources.findAllByCompanyId(
                company.getId(),
                org.springframework.data.domain.Sort.by("id")).stream()
                .filter(source -> source.getType() == AccountingSourceType.FILE_IMPORT)
                .filter(source -> source.isActive()))
                .hasSize(1);
    }

    @Test
    void prepareSourceRechecksFreshPermission() {
        var access = grants.findByUserAccountIdAndCompanyId(actor.getId(), company.getId()).orElseThrow();
        jdbc.update(
                "DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?",
                access.getId());

        assertThatThrownBy(() -> support.prepareSource(actor.getId(), company.getId()))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void historyIsNewestFirstAndOmitsTechnicalFingerprintFromItsContract() {
        var source = support.prepareSource(actor.getId(), company.getId());
        sources.flush();
        Instant older = Instant.parse("2026-01-31T10:00:00Z");
        Instant newer = Instant.parse("2026-02-28T10:00:00Z");

        receipts.insert(receipt(
                source.id(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                10,
                older));
        receipts.insert(receipt(
                source.id(),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                20,
                newer));

        var history = support.history(actor.getId(), source.id());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).coveredFrom()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(history.get(0).rowCount()).isEqualTo(20);
        assertThat(history.get(1).coveredFrom()).isEqualTo(LocalDate.of(2026, 1, 1));

        assertThat(java.util.Arrays.stream(AccountingFileImportHistoryItemResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("fileSha256", "fingerprint", "idempotencyKey");
    }

    private AccountingFileImportReceipt receipt(
            UUID sourceId,
            LocalDate from,
            LocalDate to,
            long rows,
            Instant committedAt) {
        return new AccountingFileImportReceipt(
                sourceId,
                UUID.randomUUID(),
                AccountingFileDataset.GENERAL_LEDGER,
                "a".repeat(64),
                "b".repeat(64),
                AnalyticalLedgerHtmlLayoutProfile.PROFILE_ID,
                from,
                to,
                rows,
                rows,
                0,
                actor.getId(),
                committedAt);
    }
}
