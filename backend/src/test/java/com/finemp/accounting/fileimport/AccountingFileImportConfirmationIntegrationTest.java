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
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.nio.charset.StandardCharsets;
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
class AccountingFileImportConfirmationIntegrationTest {

    @Autowired private AccountingFileImportConfirmationService confirmations;
    @Autowired private AccountingFileImportReceiptRepository receipts;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private AccountingMovementRepository movements;
    @Autowired private AccountingDatasetCoverageService coverage;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;

    private UserAccount actor;
    private Company company;
    private AccountingSource source;

    @BeforeEach
    void prepare() {
        actor = users.saveAndFlush(new UserAccount(
                "Synthetic File Import",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        company = companies.saveAndFlush(new Company(
                "Synthetic File Import Company",
                "91919191000191"));
        source = sources.saveAndFlush(new AccountingSource(
                company,
                AccountingSourceType.FILE_IMPORT,
                null,
                true));
        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.ACCOUNTING_IMPORT_MANAGE)));
    }

    @Test
    void shouldPublishObservedAccountsMovementsCoverageAndReceiptAtomically() {
        UUID command = UUID.randomUUID();

        var receipt = confirm(command, validFixture("MOV A"));

        assertThat(receipt.accountingSourceId()).isEqualTo(source.getId());
        assertThat(receipt.idempotencyKey()).isEqualTo(command);
        assertThat(receipt.coveredFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(receipt.coveredTo()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(receipt.rowCount()).isEqualTo(2);
        assertThat(receipt.publishedCount()).isEqualTo(2);
        assertThat(receipt.skippedCount()).isZero();

        assertThat(accounts.findAllByAccountingSourceIdAndExternalAccountIdIn(
                source.getId(),
                Set.of("111010001", "211010001", "311010001")))
                .hasSize(3);
        assertThat(movements.findAllForPublication(source.getId(), Set.of("0/10", "0/11")))
                .hasSize(2);

        var imported = coverage.read(source.getId(), AccountingDataset.MOVEMENTS);
        assertThat(imported.intervals()).singleElement()
                .satisfies(interval -> {
                    assertThat(interval.importedFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
                    assertThat(interval.importedTo()).isEqualTo(LocalDate.of(2026, 1, 31));
                });
        assertThat(receipts.find(source.getId(), command)).contains(receipt);
    }

    @Test
    void exactReplayWithTheSameIdempotencyKeyReturnsTheReceiptWithoutDuplicates() {
        UUID command = UUID.randomUUID();
        byte[] file = validFixture("MOV A");

        var first = confirm(command, file);
        var replay = confirm(command, file);

        assertThat(replay).isEqualTo(first);
        assertThat(movements.findAllForPublication(source.getId(), Set.of("0/10", "0/11")))
                .hasSize(2);
    }

    @Test
    void confirmationRejectsAFileDifferentFromTheReviewedPreview() {
        byte[] reviewed = validFixture("MOV A");
        byte[] changed = validFixture("MOV ALTERADO");

        assertThatThrownBy(() -> confirmations.confirm(
                actor.getId(),
                source.getId(),
                UUID.randomUUID(),
                AccountingFileHash.sha256(reviewed),
                AnalyticalLedgerHtmlLayoutProfile.PROFILE_ID,
                AccountingFileFormat.HTML_XLS,
                changed))
                .isInstanceOf(AccountingFileImportConflictException.class)
                .extracting("reason")
                .isEqualTo(AccountingFileImportConflictException.Reason.PREVIEW_FILE_MISMATCH);

        assertThat(movements.findAllForPublication(source.getId(), Set.of("0/10", "0/11")))
                .isEmpty();
    }

    @Test
    void sameIdempotencyKeyWithDifferentFileIsAConflict() {
        UUID command = UUID.randomUUID();
        confirm(command, validFixture("MOV A"));

        assertThatThrownBy(() -> confirm(command, validFixture("MOV ALTERADO")))
                .isInstanceOf(AccountingFileImportConflictException.class)
                .extracting("reason")
                .isEqualTo(AccountingFileImportConflictException.Reason.IDEMPOTENCY_MISMATCH);
    }

    @Test
    void differentCommandOverlappingAnAlreadyImportedPeriodIsAConflict() {
        confirm(UUID.randomUUID(), validFixture("MOV A"));

        assertThatThrownBy(() -> confirm(UUID.randomUUID(), validFixture("MOV B")))
                .isInstanceOf(AccountingFileImportConflictException.class)
                .extracting("reason")
                .isEqualTo(AccountingFileImportConflictException.Reason.PERIOD_OVERLAP);
    }

    @Test
    void parseErrorsBlockTheWholePublication() {
        UUID command = UUID.randomUUID();

        assertThatThrownBy(() -> confirm(command, invalidFixture()))
                .isInstanceOf(AccountingFilePreviewException.class);

        assertThat(receipts.find(source.getId(), command)).isEmpty();
        assertThat(accounts.findAllByAccountingSourceIdAndExternalAccountIdIn(
                source.getId(),
                Set.of("111010001"))).isEmpty();
        assertThat(coverage.read(source.getId(), AccountingDataset.MOVEMENTS).intervals()).isEmpty();
    }

    @Test
    void freshPermissionIsRecheckedInsideConfirmation() {
        var access = grants.findByUserAccountIdAndCompanyId(actor.getId(), company.getId()).orElseThrow();
        jdbc.update(
                "DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?",
                access.getId());

        assertThatThrownBy(() -> confirm(UUID.randomUUID(), validFixture("MOV A")))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
    }

    private AccountingFileImportReceipt confirm(UUID command, byte[] file) {
        return confirmations.confirm(
                actor.getId(),
                source.getId(),
                command,
                AccountingFileHash.sha256(file),
                AnalyticalLedgerHtmlLayoutProfile.PROFILE_ID,
                AccountingFileFormat.HTML_XLS,
                file);
    }

    private byte[] validFixture(String firstHistory) {
        return ("""
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>10/01/2026</td><td>%s</td><td></td><td>0/10</td><td></td><td>10,00</td><td></td><td>10,00</td></tr>
                <tr><td>Conta: 211010001</td><td>Red.: 2-9  FORNECEDORES SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>Conta: 311010001</td><td>Red.: 3-9  RECEITA SINTETICA</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>11/01/2026</td><td>MOV CREDITO</td><td></td><td>0/11</td><td></td><td></td><td>20,00</td><td>-20,00</td></tr>
                </table></body></html>
                """).formatted(firstHistory).getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] invalidFixture() {
        return """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>10/01/2026</td><td>INVALIDA</td><td></td><td>0/10</td><td></td><td>1,00</td><td>1,00</td><td>0,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);
    }
}
