package com.finemp.finance.reporting;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.billing.*;
import com.finemp.company.*;
import com.finemp.contracts.*;
import com.finemp.finance.partner.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsistencyDiagnosticsIntegrationTest {

    @Autowired ConsistencyDiagnosticsService diagnostics;
    @Autowired CompanyRepository companies;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired BusinessPartnerRepository partners;
    @Autowired ContractRepository contracts;
    @Autowired ServiceInvoiceRepository invoices;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void consistentLinksReturnZeroIssuesAndExplicitPartialCoverage() {
        var fixture = fixture();

        var report = diagnostics.report(
                fixture.allowed().getId(),
                fixture.owner().getId());

        assertThat(report.partial()).isTrue();
        assertThat(report.coverage())
                .containsExactly(
                        "CONTRACTS",
                        "SERVICE_INVOICES",
                        "REFERENTIAL_LINKS");
        assertThat(report.issueCount()).isZero();
        assertThat(report.checks())
                .allSatisfy(check -> {
                    assertThat(check.status()).isEqualTo("OK");
                    assertThat(check.occurrenceCount()).isZero();
                    assertThat(check.occurrenceIds()).isEmpty();
                });
    }

    @Test
    void detectsOnlyCompanyScopedReferentialInconsistenciesAndReturnsTechnicalIds() {
        var fixture = fixture();

        var crossPartnerContract = contract(
                fixture.owner(),
                fixture.ownerPartner(),
                "Owner cross-partner contract");
        jdbc.update(
                "UPDATE contracts SET business_partner_id=? WHERE id=?",
                fixture.foreignPartner().getId(),
                crossPartnerContract.getId());

        var foreignPartnerInvoice = invoice(
                fixture.owner(),
                fixture.ownerPartner(),
                null,
                "Owner invoice foreign partner");
        jdbc.update(
                "UPDATE service_invoice_drafts SET business_partner_id=? WHERE id=?",
                fixture.foreignPartner().getId(),
                foreignPartnerInvoice.getId());

        var mismatchContract = contract(
                fixture.owner(),
                fixture.ownerPartner(),
                "Owner mismatch contract");
        var mismatchInvoice = invoice(
                fixture.owner(),
                fixture.ownerPartner(),
                mismatchContract,
                "Owner invoice mismatched partner");
        jdbc.update(
                "UPDATE service_invoice_drafts SET business_partner_id=? WHERE id=?",
                fixture.foreignPartner().getId(),
                mismatchInvoice.getId());

        var foreignContract = contract(
                fixture.other(),
                fixture.foreignPartner(),
                "Foreign company contract");
        jdbc.update(
                "UPDATE contracts SET business_partner_id=? WHERE id=?",
                fixture.ownerPartner().getId(),
                foreignContract.getId());
        var crossContractInvoice = invoice(
                fixture.owner(),
                fixture.ownerPartner(),
                null,
                "Owner invoice foreign contract");
        jdbc.update(
                "UPDATE service_invoice_drafts SET contract_id=? WHERE id=?",
                foreignContract.getId(),
                crossContractInvoice.getId());

        var foreignOnly = contract(
                fixture.other(),
                fixture.foreignPartner(),
                "Foreign-only inconsistent contract");
        jdbc.update(
                "UPDATE contracts SET business_partner_id=? WHERE id=?",
                fixture.ownerPartner().getId(),
                foreignOnly.getId());

        var report = diagnostics.report(
                fixture.allowed().getId(),
                fixture.owner().getId());

        assertThat(report.issueCount()).isEqualTo(5);
        assertCheck(
                report,
                "CONTRACT_PARTNER_COMPANY",
                crossPartnerContract.getId());
        assertCheck(
                report,
                "SERVICE_INVOICE_PARTNER_COMPANY",
                foreignPartnerInvoice.getId(),
                mismatchInvoice.getId());
        assertCheck(
                report,
                "SERVICE_INVOICE_CONTRACT_COMPANY",
                crossContractInvoice.getId());
        assertCheck(
                report,
                "SERVICE_INVOICE_CONTRACT_PARTNER",
                mismatchInvoice.getId());

        assertThat(report.checks().stream()
                .flatMap(check -> check.occurrenceIds().stream()))
                .doesNotContain(foreignOnly.getId());
    }

    @Test
    void httpRequiresBothContractAndBillingReadFamiliesAndDoesNotExposePartnerData()
            throws Exception {
        var fixture = fixture();

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/diagnostics",
                        fixture.owner().getId())
                        .with(authentication(auth(fixture.allowed()))))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        containsString("no-store")))
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.issueCount").value(0))
                .andExpect(jsonPath("$.coverage.length()").value(3))
                .andExpect(content().string(not(containsString(
                        fixture.ownerPartner().getProfile().name()))))
                .andExpect(content().string(not(containsString(
                        fixture.ownerPartner().getProfile().document()))));

        for (var denied : List.of(
                fixture.contractOnly(),
                fixture.billingOnly())) {
            mvc.perform(get(
                            "/api/companies/{companyId}/financial-reports/diagnostics",
                            fixture.owner().getId())
                            .with(authentication(auth(denied))))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/diagnostics",
                        fixture.other().getId())
                        .with(authentication(auth(fixture.allowed()))))
                .andExpect(status().isForbidden());
    }

    private Fixture fixture() {
        var owner = companies.saveAndFlush(
                new Company("Synthetic diagnostics owner", "71717171000171"));
        var other = companies.saveAndFlush(
                new Company("Synthetic diagnostics other", "72727272000172"));

        var allowed = user("allowed");
        var contractOnly = user("contract-only");
        var billingOnly = user("billing-only");

        grant(
                allowed,
                owner,
                Set.of(
                        CompanyPermission.CONTRACT_VIEW,
                        CompanyPermission.BILLING_VIEW));
        grant(
                contractOnly,
                owner,
                Set.of(CompanyPermission.CONTRACT_VIEW));
        grant(
                billingOnly,
                owner,
                Set.of(CompanyPermission.BILLING_VIEW));

        var ownerPartner = partner(
                owner,
                "12ABC34501DE35");
        var foreignPartner = partner(
                other,
                "98ZYX76543WV39");

        var ownerContract = contract(
                owner,
                ownerPartner,
                "Consistent owner contract");
        invoice(
                owner,
                ownerPartner,
                ownerContract,
                "Consistent owner invoice");

        return new Fixture(
                owner,
                other,
                allowed,
                contractOnly,
                billingOnly,
                ownerPartner,
                foreignPartner);
    }

    private UserAccount user(String suffix) {
        return users.saveAndFlush(new UserAccount(
                "Synthetic diagnostics " + suffix,
                UUID.randomUUID() + "@example.invalid",
                "hash"));
    }

    private void grant(
            UserAccount user,
            Company company,
            Set<CompanyPermission> permissions) {
        grants.saveAndFlush(new CompanyAccessGrant(
                user,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                permissions));
    }

    private BusinessPartner partner(
            Company company,
            String document) {
        return partners.saveAndFlush(new BusinessPartner(
                company,
                new BusinessPartnerProfile(
                        PartnerPersonType.LEGAL_ENTITY,
                        document,
                        "Synthetic Diagnostics Partner",
                        "Synthetic Diagnostics",
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

    private Contract contract(
            Company company,
            BusinessPartner partner,
            String description) {
        return contracts.saveAndFlush(new Contract(
                company,
                partner,
                new ContractValues(
                        ContractDirection.INCOME,
                        description,
                        LocalDate.of(2026, 1, 1),
                        null,
                        10,
                        new BigDecimal("100.00"),
                        true)));
    }

    private ServiceInvoiceDraft invoice(
            Company company,
            BusinessPartner partner,
            Contract contract,
            String serviceText) {
        return invoices.saveAndFlush(new ServiceInvoiceDraft(
                company,
                partner,
                contract,
                new ServiceInvoiceDraftValues(
                        LocalDate.of(2026, 11, 1),
                        serviceText,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        false,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO)));
    }

    private static UsernamePasswordAuthenticationToken auth(
            UserAccount user) {
        return new UsernamePasswordAuthenticationToken(
                new UserAccountPrincipal(user),
                null,
                List.of());
    }

    private static void assertCheck(
            ConsistencyDiagnosticsService.Report report,
            String code,
            UUID... ids) {
        var check = report.checks().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow();

        assertThat(check.status()).isEqualTo("ISSUE");
        assertThat(check.occurrenceCount()).isEqualTo(ids.length);
        assertThat(check.occurrenceIds())
                .containsExactlyInAnyOrder(ids);
    }

    private record Fixture(
            Company owner,
            Company other,
            UserAccount allowed,
            UserAccount contractOnly,
            UserAccount billingOnly,
            BusinessPartner ownerPartner,
            BusinessPartner foreignPartner) {
    }
}
