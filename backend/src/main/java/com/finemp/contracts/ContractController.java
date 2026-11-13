package com.finemp.contracts;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/contracts")
public class ContractController {

    private final ContractService service;
    private final MonthlyContractBillingPreviewService billingPreviewService;

    public ContractController(
            ContractService service,
            MonthlyContractBillingPreviewService billingPreviewService) {
        this.service = service;
        this.billingPreviewService = billingPreviewService;
    }

    @GetMapping
    public PagedResponse<ContractResponse> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.list(
                actor.getUserAccountId(),
                companyId,
                search,
                active,
                page,
                size);
    }

    @GetMapping("/billing-preview")
    public ResponseEntity<MonthlyContractBillingPreviewService.Report> billingPreview(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var report = billingPreviewService.report(
                actor.getUserAccountId(),
                companyId,
                YearMonth.parse(month),
                page,
                size);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(report);
    }

    @GetMapping("/partners")
    public PagedResponse<ContractPartnerOptionResponse> partnerOptions(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.listPartnerOptions(
                actor.getUserAccountId(),
                companyId,
                search,
                page,
                size);
    }

    @GetMapping("/{contractId}")
    public ContractResponse find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID contractId) {

        return service.find(actor.getUserAccountId(), companyId, contractId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @Valid @RequestBody ContractRequest request) {

        return service.create(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{contractId}")
    public ContractResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID contractId,
            @Valid @RequestBody ContractRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                contractId,
                request);
    }
}
