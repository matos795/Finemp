package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.source.*;
import com.finemp.company.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = "finemp.integration.questor.enabled=true")
@Import(TestcontainersConfiguration.class)
class QuestorImportDetachedContextIntegrationTest {
    @Autowired private QuestorAccountingImportService service;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private QuestorAccountingReader reader;

    @Test
    void shouldMaterializeCompanyContextBeforeReadingOutsidePostgresTransaction() {
        var company = companies.saveAndFlush(new Company("Synthetic Detached Context", "21212121000121"));
        var source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic-detached", true));
        var start = LocalDate.of(2026, 7, 1);
        var end = LocalDate.of(2026, 7, 31);
        try {
            when(reader.readMovements("synthetic-detached", start, end)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return List.of(new ImportedAccountingMovement("synthetic-detached-entry", start, "synthetic-account",
                        "synthetic.account", "Synthetic Account", AccountingMovementSide.DEBIT, new BigDecimal("1.2500"), "Synthetic"));
            });
            service.importMovements(source.getId(), start, end);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounting_movements WHERE account_id IN"
                    + " (SELECT id FROM accounting_accounts WHERE accounting_source_id = ?)", Long.class, source.getId())).isEqualTo(1L);
        } finally {
            // Only the UUID-scoped synthetic rows created in this test.
            jdbc.update("DELETE FROM accounting_movements WHERE account_id IN (SELECT id FROM accounting_accounts WHERE accounting_source_id = ?)", source.getId());
            jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id = ?", source.getId());
            jdbc.update("DELETE FROM accounting_sources WHERE id = ?", source.getId());
            jdbc.update("DELETE FROM companies WHERE id = ?", company.getId());
        }
    }
}
