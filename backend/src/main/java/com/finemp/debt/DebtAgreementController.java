package com.finemp.debt;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/debts")
public class DebtAgreementController {

    private final DebtAgreementService service;

    public DebtAgreementController(DebtAgreementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<DebtAgreementResponse>> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(required = false) DebtOrigin origin,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(
                        actor.getUserAccountId(),
                        companyId,
                        origin,
                        search,
                        page,
                        size));
    }

    @GetMapping("/partners")
    public ResponseEntity<PagedResponse<DebtPartnerOptionResponse>> partnerOptions(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.partnerOptions(
                        actor.getUserAccountId(),
                        companyId,
                        search,
                        page,
                        size));
    }

    @GetMapping("/{debtId}")
    public ResponseEntity<DebtAgreementResponse> find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID debtId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.find(actor.getUserAccountId(), companyId, debtId));
    }

    @GetMapping("/{debtId}/schedule")
    public ResponseEntity<List<DebtInstallmentResponse>> schedule(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID debtId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.schedule(actor.getUserAccountId(), companyId, debtId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DebtAgreementResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestBody DebtAgreementRequest request) {

        return service.create(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{debtId}")
    public DebtAgreementResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID debtId,
            @RequestBody DebtAgreementRequest request) {

        return service.update(
                actor.getUserAccountId(),
                companyId,
                debtId,
                request);
    }
}
