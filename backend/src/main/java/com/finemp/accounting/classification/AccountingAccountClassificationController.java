package com.finemp.accounting.classification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

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

    @GetMapping("/classifications")
    public AccountingAccountClassificationPageResponse findAll(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) AccountingAccountClassificationStatus status,
            @RequestParam(required = false) String search) {

        return queryService.findPageByCompanyId(
                companyId,
                page,
                size,
                status,
                search);
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

    @DeleteMapping("/{accountId}/classification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unclassifyAccount(
            @PathVariable UUID companyId,
            @PathVariable UUID accountId) {

        classificationService.unclassifyAccount(
                companyId,
                accountId);
    }
}