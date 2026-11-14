package com.finemp.debt;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.company.*;
import com.finemp.finance.partner.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DebtPortfolioReadIntegrationTest {

    @Autowired DebtPortfolioReadService portfolio;
    @Autowired DebtAgreementRepository debts;
    @Autowired BusinessPartnerRepository partners;
    @Autowired CompanyRepository companies;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired MockMvc mvc;

    @Test
    void overviewAggregatesExactContractualAmountsByOriginAndCompany() {
        var fixture = fixture();

        debt(
                fixture.owner(),
                null,
                DebtOrigin.TAX,
                "Parcelamento fiscal",
                "1000.00",
                3,
                "3.00");
        debt(
                fixture.owner(),
                fixture.ownerPartner(),
                DebtOrigin.BANK,
                "Capital de giro",
                "600.00",
                2,
                "0");
        debt(
                fixture.other(),
                fixture.otherPartner(),
                DebtOrigin.BANK,
                "Foreign debt",
                "9999.00",
                1,
                "0");

        var report = portfolio.overview(
                fixture.viewer().getId(),
                fixture.owner().getId());

        assertThat(report.agreementCount()).isEqualTo(2);
        assertThat(report.principalAmount()).isEqualByComparingTo("1600.00");
        assertThat(report.scheduledAmount()).isEqualByComparingTo("1660.00");
        assertThat(report.interestAmount()).isEqualByComparingTo("60.00");

        assertOrigin(report, DebtOrigin.BANK, 1, "600.00", "600.00", "0.00");
        assertOrigin(report, DebtOrigin.TAX, 1, "1000.00", "1060.00", "60.00");
        assertOrigin(report, DebtOrigin.SUPPLIER, 0, "0.00", "0.00", "0.00");
        assertOrigin(report, DebtOrigin.THIRD_PARTY, 0, "0.00", "0.00", "0.00");
    }

    @Test
    void monthlyScheduleIsServerFilteredOrderedAndPaged() {
        var fixture = fixture();

        var bank = debt(
                fixture.owner(),
                fixture.ownerPartner(),
                DebtOrigin.BANK,
                "Banco",
                "600.00",
                2,
                "0");
        var tax = debt(
                fixture.owner(),
                null,
                DebtOrigin.TAX,
                "Imposto",
                "1000.00",
                3,
                "3.00");
        debt(
                fixture.other(),
                fixture.otherPartner(),
                DebtOrigin.BANK,
                "Foreign",
                "5000.00",
                2,
                "0");

        var first = portfolio.schedule(
                fixture.viewer().getId(),
                fixture.owner().getId(),
                "2026-01",
                0,
                1);
        var second = portfolio.schedule(
                fixture.viewer().getId(),
                fixture.owner().getId(),
                "2026-01",
                1,
                1);

        assertThat(first.totalItems()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);

        assertThat(first.items().getFirst().debtId()).isEqualTo(bank.getId());
        assertThat(first.items().getFirst().origin()).isEqualTo(DebtOrigin.BANK);
        assertThat(first.items().getFirst().installmentAmount())
                .isEqualByComparingTo("300.00");

        assertThat(second.items().getFirst().debtId()).isEqualTo(tax.getId());
        assertThat(second.items().getFirst().origin()).isEqualTo(DebtOrigin.TAX);
        assertThat(second.items().getFirst().interestAmount())
                .isEqualByComparingTo("30.00");

        var february = portfolio.schedule(
                fixture.viewer().getId(),
                fixture.owner().getId(),
                "2026-02",
                0,
                50);
        assertThat(february.items()).hasSize(2);
        assertThat(february.items())
                .allSatisfy(item -> assertThat(item.dueDate().getMonthValue()).isEqualTo(2));
    }

    @Test
    void httpKeepsMoneyAsStringsNoStoreAndFreshDebtPermissionBoundary()
            throws Exception {
        var fixture = fixture();
        debt(
                fixture.owner(),
                null,
                DebtOrigin.TAX,
                "Parcelamento fiscal",
                "1000.00",
                3,
                "3.00");

        mvc.perform(get(
                        "/api/companies/{companyId}/debts/overview",
                        fixture.owner().getId())
                        .with(user(new UserAccountPrincipal(fixture.viewer()))))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        containsString("no-store")))
                .andExpect(jsonPath("$.agreementCount").value(1))
                .andExpect(jsonPath("$.principalAmount").value("1000.00"))
                .andExpect(jsonPath("$.scheduledAmount").value("1060.00"))
                .andExpect(jsonPath("$.interestAmount").value("60.00"));

        mvc.perform(get(
                        "/api/companies/{companyId}/debts/schedule",
                        fixture.owner().getId())
                        .with(user(new UserAccountPrincipal(fixture.viewer())))
                        .param("month", "2026-01")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        containsString("no-store")))
                .andExpect(jsonPath("$.items[0].installmentAmount")
                        .value("363.33"));

        mvc.perform(get(
                        "/api/companies/{companyId}/debts/overview",
                        fixture.owner().getId())
                        .with(user(new UserAccountPrincipal(fixture.denied()))))
                .andExpect(status().isForbidden());

        mvc.perform(get(
                        "/api/companies/{companyId}/debts/schedule",
                        fixture.owner().getId())
                        .with(user(new UserAccountPrincipal(fixture.viewer())))
                        .param("month", "invalid"))
                .andExpect(status().isBadRequest());
    }

    private Fixture fixture() {
        var owner = companies.saveAndFlush(new Company(
                "Synthetic Debt Portfolio Owner",
                "73737373000173"));
        var other = companies.saveAndFlush(new Company(
                "Synthetic Debt Portfolio Other",
                "74747474000174"));

        var viewer = users.saveAndFlush(new UserAccount(
                "Synthetic Debt Portfolio Viewer",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        var denied = users.saveAndFlush(new UserAccount(
                "Synthetic Debt Portfolio Denied",
                UUID.randomUUID() + "@example.invalid",
                "hash"));

        grants.saveAndFlush(new CompanyAccessGrant(
                viewer,
                owner,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.DEBT_VIEW)));

        var ownerPartner = partner(owner, "12ABC34501DE35", "Banco Sintético");
        var otherPartner = partner(other, "98ZYX76543WV39", "Banco Estrangeiro");

        return new Fixture(
                owner,
                other,
                viewer,
                denied,
                ownerPartner,
                otherPartner);
    }

    private BusinessPartner partner(
            Company company,
            String document,
            String name) {
        return partners.saveAndFlush(new BusinessPartner(
                company,
                new BusinessPartnerProfile(
                        PartnerPersonType.LEGAL_ENTITY,
                        document,
                        name,
                        name,
                        PartnerRole.BOTH,
                        "",
                        "",
                        "",
                        "São Paulo",
                        "SP",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "3550308",
                        "",
                        "",
                        "",
                        true)));
    }

    private DebtAgreement debt(
            Company company,
            BusinessPartner partner,
            DebtOrigin origin,
            String description,
            String principal,
            int installmentCount,
            String rate) {
        return debts.saveAndFlush(new DebtAgreement(
                company,
                partner,
                new DebtAgreementValues(
                        origin,
                        description,
                        DebtAmortizationSystem.SAC,
                        new BigDecimal(principal),
                        installmentCount,
                        new BigDecimal(rate),
                        DebtRateInputPeriod.MONTHLY,
                        null,
                        LocalDate.of(2026, 1, 31))));
    }

    private static void assertOrigin(
            DebtPortfolioReadService.Overview report,
            DebtOrigin origin,
            long count,
            String principal,
            String scheduled,
            String interest) {
        var row = report.byOrigin().stream()
                .filter(candidate -> candidate.origin() == origin)
                .findFirst()
                .orElseThrow();

        assertThat(row.agreementCount()).isEqualTo(count);
        assertThat(row.principalAmount()).isEqualByComparingTo(principal);
        assertThat(row.scheduledAmount()).isEqualByComparingTo(scheduled);
        assertThat(row.interestAmount()).isEqualByComparingTo(interest);
    }

    private record Fixture(
            Company owner,
            Company other,
            UserAccount viewer,
            UserAccount denied,
            BusinessPartner ownerPartner,
            BusinessPartner otherPartner) {
    }
}
