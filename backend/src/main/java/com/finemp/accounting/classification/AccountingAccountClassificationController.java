package com.finemp.accounting.classification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies/{companyId}/accounting-accounts")
public class AccountingAccountClassificationController {

    private final AccountingAccountClassificationQueryService queryService;
    private final AccountingAccountClassificationService classificationService;

    public AccountingAccountClassificationController(
            AccountingAccountClassificationQueryService queryService,
            AccountingAccountClassificationService classificationService) {

        this.queryService = queryService;
        this.classificationService = classificationService;
    }

    /** Universo importado, paginado e filtrado no servidor (#442). */
    @GetMapping
    public ImportedAccountsPageResponse searchImported(
            @PathVariable UUID companyId,
            @RequestParam(required = false) UUID accountingSourceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ImportedAccountStatusFilter status,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "50") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(100) int size) {
        return queryService.searchImported(companyId, accountingSourceId, search, status, page, size);
    }

    @GetMapping("/classifications")
    public List<AccountingAccountClassificationResponse> findAll(
            @PathVariable UUID companyId) {

        return queryService.findAllByCompanyId(companyId);
    }

    @PutMapping("/{accountId}/classification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void classifyAccount(
            @PathVariable UUID companyId,
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountingAccountClassificationRequest request) {

        classificationService.classifyAccount(
                companyId,
                accountId,
                request.classificationId());
    }
}