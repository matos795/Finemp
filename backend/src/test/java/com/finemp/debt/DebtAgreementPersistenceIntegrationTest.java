package com.finemp.debt;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DebtAgreementPersistenceIntegrationTest {

    @Autowired private DebtAgreementRepository debts;
    @Autowired private CompanyRepository companies;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void plannedSchedulePersistsExactlyAndCannotCrossCompany() {
        var company = companies.saveAndFlush(new Company(
                "Synthetic Debt Persistence",
                "55555555000188"));
        var otherCompany = companies.saveAndFlush(new Company(
                "Synthetic Debt Other",
                "55555555000189"));

        var agreement = debts.saveAndFlush(new DebtAgreement(
                company,
                null,
                sac(DebtOrigin.TAX, "Parcelamento fiscal", "1000.00", 3, "3.00")));

        var id = agreement.getId();
        entityManager.clear();

        var loaded = debts.findDetailed(company.getId(), id).orElseThrow();

        assertThat(loaded.getInstallments()).hasSize(3);
        assertThat(loaded.getInstallments().getFirst().getAmortizationAmount())
                .isEqualByComparingTo("333.33");
        assertThat(loaded.getInstallments().getFirst().getInterestAmount())
                .isEqualByComparingTo("30.00");
        assertThat(loaded.getInstallments().getLast().getBalanceAfter())
                .isEqualByComparingTo("0.00");
        assertThat(debts.findDetailed(otherCompany.getId(), id)).isEmpty();
    }

    @Test
    void replacingPlannedScheduleDeletesOldDerivedRows() {
        var company = companies.saveAndFlush(new Company(
                "Synthetic Debt Replace",
                "55555555000190"));

        var agreement = debts.saveAndFlush(new DebtAgreement(
                company,
                null,
                sac(DebtOrigin.TAX, "Original", "1000.00", 3, "3.00")));
        var id = agreement.getId();

        agreement.replace(
                null,
                sac(DebtOrigin.TAX, "Renegociada", "600.00", 2, "0"));
        debts.saveAndFlush(agreement);
        entityManager.clear();

        var loaded = debts.findDetailed(company.getId(), id).orElseThrow();

        assertThat(loaded.getInstallments()).hasSize(2);
        assertThat(loaded.getInstallments().getFirst().getAmortizationAmount())
                .isEqualByComparingTo("300.00");
        assertThat(loaded.getInstallments().getLast().getBalanceAfter())
                .isEqualByComparingTo("0.00");

        Integer rowCount = jdbc.queryForObject(
                "select count(*) from debt_installments where debt_agreement_id = ?",
                Integer.class,
                id);
        assertThat(rowCount).isEqualTo(2);
    }

    private static DebtAgreementValues sac(
            DebtOrigin origin,
            String description,
            String principal,
            int count,
            String monthlyRate) {
        return new DebtAgreementValues(
                origin,
                description,
                DebtAmortizationSystem.SAC,
                new BigDecimal(principal),
                count,
                new BigDecimal(monthlyRate),
                DebtRateInputPeriod.MONTHLY,
                null,
                LocalDate.of(2026, 1, 31));
    }
}
