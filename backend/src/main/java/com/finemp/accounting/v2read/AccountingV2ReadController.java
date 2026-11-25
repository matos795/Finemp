package com.finemp.accounting.v2read;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leitura HTTP do domínio contábil v2 (#474). Somente GET; autorização na cadeia de segurança. */
@RestController
@RequestMapping("/api/companies/{companyId}")
public class AccountingV2ReadController {

    private final AccountingV2ReadService service;

    public AccountingV2ReadController(AccountingV2ReadService service) {
        this.service = service;
    }

    @GetMapping("/accounting-chart")
    public AccountingChartPageResponse chart(
            @PathVariable UUID companyId,
            @RequestParam UUID accountingSourceId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(AccountingV2ReadService.MAX_CHART_PAGE_SIZE) int size) {
        return service.chart(companyId, accountingSourceId, search, page, size);
    }

    @GetMapping("/accounting-debit-credit-totals")
    public AccountingDebitCreditTotalsPageResponse debitCreditTotals(
            @PathVariable UUID companyId,
            @RequestParam UUID accountingSourceId,
            @RequestParam DebitCreditTotalsGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String establishmentCode,
            @RequestParam(required = false) String sourceAccountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(AccountingV2ReadService.MAX_TOTALS_PAGE_SIZE) int size) {
        return service.debitCreditTotals(companyId, accountingSourceId, granularity, startDate, endDate,
                establishmentCode, sourceAccountId, page, size);
    }

    @GetMapping("/accounting-dataset-coverage")
    public AccountingDatasetCoverageResponse datasetCoverage(
            @PathVariable UUID companyId,
            @RequestParam UUID accountingSourceId,
            @RequestParam AccountingDataset dataset,
            @RequestParam(required = false) String establishmentCode) {
        return service.datasetCoverage(companyId, accountingSourceId, dataset, establishmentCode);
    }

    @GetMapping("/accounting-origin-catalog")
    public AccountingOriginCatalogResponse originCatalog(
            @PathVariable UUID companyId,
            @RequestParam UUID accountingSourceId) {
        return service.originCatalog(companyId, accountingSourceId);
    }
}
