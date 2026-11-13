package com.finemp.finance.reporting;

import static org.assertj.core.api.Assertions.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkingCapitalCycleIntegrationTest {

    @Autowired private WorkingCapitalCycleService reports;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private BankAccountRepository banks;
    @Autowired private FinancialCategoryRepository categories;
    @Autowired private FinancialEntryRepository entries;

    @Test
    void reportKeepsFinancialRowsInsideTheRequestedCompany() {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cycle Isolation",
                "cycle-isolation@example.invalid",
                "hash"));
        var owner = companies.saveAndFlush(new Company(
                "Synthetic Cycle Owner",
                "58585858000158"));
        var other = companies.saveAndFlush(new Company(
                "Synthetic Cycle Other",
                "59595959000159"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                owner,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));

        UUID ownerBank = bank(owner, "Synthetic Cycle Owner Bank");
        UUID otherBank = bank(other, "Synthetic Cycle Other Bank");
        UUID ownerCategory = incomeCategory(owner, 1);
        UUID otherCategory = incomeCategory(other, 1);
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate documentDate = today.withDayOfMonth(1);

        saveOpenReceivable(
                owner,
                actor,
                ownerBank,
                ownerCategory,
                "100.00",
                documentDate,
                documentDate.plusDays(10));
        saveOpenReceivable(
                other,
                actor,
                otherBank,
                otherCategory,
                "999.00",
                documentDate,
                documentDate.plusDays(20));

        var report = reports.report(actor.getId(), owner.getId(), 1, 0);

        assertThat(report.receivables().volume()).isEqualByComparingTo("100.00");
        assertThat(report.receivables().openAmount()).isEqualByComparingTo("100.00");
        assertThat(report.receivables().grantedDays()).isEqualByComparingTo("10.00");
    }

    private UUID bank(Company company, String name) {
        return banks.saveAndFlush(new BankAccount(
                company,
                name,
                BankAccountType.CASH,
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                true)).getId();
    }

    private UUID incomeCategory(Company company, long sequence) {
        var group = categories.saveAndFlush(
                FinancialCategory.group(company, FinancialDirection.INCOME, sequence, "Synthetic Revenue"));
        return categories.saveAndFlush(
                FinancialCategory.subcategory(group, 1, "Synthetic Revenue Child")).getId();
    }

    private void saveOpenReceivable(
            Company company,
            UserAccount actor,
            UUID bankAccountId,
            UUID categoryId,
            String nominal,
            LocalDate documentDate,
            LocalDate dueDate) {
        var values = new FinancialEntryValues(
                FinancialDirection.INCOME,
                "Synthetic cycle receivable",
                new BigDecimal(nominal),
                documentDate,
                documentDate,
                YearMonth.from(documentDate),
                dueDate,
                null,
                null,
                bankAccountId,
                categoryId,
                null,
                null,
                null,
                "",
                "SYNTHETIC",
                "");

        entries.saveAndFlush(new FinancialEntry(company, values, actor.getId()));
    }
}
