package com.finemp.accounting.v2read;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
import com.finemp.accounting.source.AccountingSourceRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura dos datasets contábeis v2 (#474). A fonte sempre é resolvida contra a
 * Company da rota; a permissão por Company é conferida antes, na cadeia de segurança.
 * Cobertura, página e contagem rodam no mesmo snapshot (REPEATABLE_READ).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AccountingV2ReadService {

    static final int MAX_CHART_PAGE_SIZE = 100;
    static final int MAX_TOTALS_PAGE_SIZE = 200;

    private final AccountingSourceRepository sources;
    private final AccountingDatasetCoverageService coverage;
    private final AccountingChartReadRepository chart;
    private final AccountingDebitCreditTotalsReadRepository totals;
    private final AccountingOriginCatalogReadRepository originCatalog;

    public AccountingV2ReadService(
            AccountingSourceRepository sources,
            AccountingDatasetCoverageService coverage,
            AccountingChartReadRepository chart,
            AccountingDebitCreditTotalsReadRepository totals,
            AccountingOriginCatalogReadRepository originCatalog) {
        this.sources = sources;
        this.coverage = coverage;
        this.chart = chart;
        this.totals = totals;
        this.originCatalog = originCatalog;
    }

    public AccountingChartPageResponse chart(UUID companyId, UUID accountingSourceId, String search, int page, int size) {
        requirePage(page, size, MAX_CHART_PAGE_SIZE);
        requireSourceOfCompany(companyId, accountingSourceId);

        var chartCoverage = coverage.read(accountingSourceId, AccountingDataset.CHART);
        var responseCoverage = new AccountingChartPageResponse.ChartCoverage(
                AccountingDataset.CHART.name(), chartCoverage.publishedAt());
        if (chartCoverage.publishedAt() == null) {
            return new AccountingChartPageResponse(accountingSourceId,
                    AccountingChartPageResponse.Availability.IMPORTED_ACCOUNTS_ONLY, responseCoverage,
                    List.of(), page, size, 0, 0);
        }

        String normalizedSearch = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        var result = chart.search(accountingSourceId, normalizedSearch, PageRequest.of(page, size));
        return new AccountingChartPageResponse(accountingSourceId,
                AccountingChartPageResponse.Availability.FULL_CHART, responseCoverage,
                result.getContent().stream().map(AccountingChartPageResponse.Item::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public AccountingDebitCreditTotalsPageResponse debitCreditTotals(
            UUID companyId,
            UUID accountingSourceId,
            DebitCreditTotalsGranularity granularity,
            LocalDate startDate,
            LocalDate endDate,
            String establishmentCode,
            String sourceAccountId,
            int page,
            int size) {
        requirePage(page, size, MAX_TOTALS_PAGE_SIZE);
        if (startDate.isAfter(endDate)) {
            throw new InvalidAccountingV2ReadRequestException("Invalid accounting period.");
        }
        if (granularity == DebitCreditTotalsGranularity.MONTHLY
                && (startDate.getDayOfMonth() != 1 || !endDate.equals(YearMonth.from(endDate).atEndOfMonth()))) {
            throw new InvalidAccountingV2ReadRequestException("Monthly totals require whole months.");
        }
        requireOptionalCode(establishmentCode, "Invalid establishment code.");
        requireOptionalCode(sourceAccountId, "Invalid source account id.");
        requireSourceOfCompany(companyId, accountingSourceId);

        boolean allEstablishments = establishmentCode == null;
        boolean allAccounts = sourceAccountId == null;
        String establishment = allEstablishments ? "" : establishmentCode;
        String account = allAccounts ? "" : sourceAccountId;
        var pageable = PageRequest.of(page, size);
        var result = granularity == DebitCreditTotalsGranularity.MONTHLY
                ? totals.searchMonthly(accountingSourceId, startDate, endDate, allEstablishments, establishment,
                        allAccounts, account, pageable)
                : totals.searchDaily(accountingSourceId, startDate, endDate, allEstablishments, establishment,
                        allAccounts, account, pageable);

        return new AccountingDebitCreditTotalsPageResponse(accountingSourceId, granularity, startDate, endDate,
                result.getContent().stream()
                        .map(row -> AccountingDebitCreditTotalsPageResponse.Item.from(granularity, row))
                        .toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public AccountingDatasetCoverageResponse datasetCoverage(
            UUID companyId,
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode) {
        requireOptionalCode(establishmentCode, "Invalid establishment code.");
        requireSourceOfCompany(companyId, accountingSourceId);
        return AccountingDatasetCoverageResponse.from(coverage.read(accountingSourceId, dataset, establishmentCode));
    }

    public AccountingOriginCatalogResponse originCatalog(UUID companyId, UUID accountingSourceId) {
        requireSourceOfCompany(companyId, accountingSourceId);
        var catalogCoverage = coverage.read(accountingSourceId, AccountingDataset.ORIGIN_CATALOG);
        if (catalogCoverage.publishedAt() == null) {
            return new AccountingOriginCatalogResponse(accountingSourceId,
                    AccountingOriginCatalogResponse.Availability.NOT_PUBLISHED, null, List.of());
        }
        return new AccountingOriginCatalogResponse(accountingSourceId,
                AccountingOriginCatalogResponse.Availability.AVAILABLE, catalogCoverage.publishedAt(),
                originCatalog.findAllByAccountingSourceIdOrderByOriginCodeAsc(accountingSourceId).stream()
                        .map(entry -> new AccountingOriginCatalogResponse.Item(
                                entry.getOriginCode(), entry.getDescription()))
                        .toList());
    }

    private void requireSourceOfCompany(UUID companyId, UUID accountingSourceId) {
        boolean owned = sources.findCompanyIdById(accountingSourceId)
                .map(companyId::equals)
                .orElse(false);
        if (!owned) {
            throw new AccountingSourceNotInCompanyException();
        }
    }

    private static void requirePage(int page, int size, int maxSize) {
        if (page < 0 || size < 1 || size > maxSize) {
            throw new InvalidAccountingV2ReadRequestException("Invalid page.");
        }
    }

    private static void requireOptionalCode(String value, String message) {
        if (value != null && value.isBlank()) {
            throw new InvalidAccountingV2ReadRequestException(message);
        }
    }
}
