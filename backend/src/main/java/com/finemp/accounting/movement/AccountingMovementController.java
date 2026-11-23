package com.finemp.accounting.movement;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies/{companyId}/accounting-movements")
public class AccountingMovementController {

    private final AccountingMovementQueryService accountingMovementQueryService;

    public AccountingMovementController(
            AccountingMovementQueryService accountingMovementQueryService) {
        this.accountingMovementQueryService = accountingMovementQueryService;
    }

    @GetMapping
    public List<AccountingMovementResponse> findByCompanyAndPeriod(
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return accountingMovementQueryService.findByCompanyAndPeriod(
                companyId,
                startDate,
                endDate);
    }

    /** Página determinística com filtros e totais exatos (#444). */
    @GetMapping("/search")
    public AccountingMovementSearchResponse search(
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID accountingSourceId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "100") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(200) int size) {
        return accountingMovementQueryService.search(companyId, startDate, endDate, accountingSourceId, accountId,
                page, size);
    }
}
