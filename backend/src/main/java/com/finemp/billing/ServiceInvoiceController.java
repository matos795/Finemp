package com.finemp.billing;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/service-invoices")
public class ServiceInvoiceController {

    private final ServiceInvoiceService service;

    public ServiceInvoiceController(ServiceInvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ServiceInvoiceResponse>> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam String month,
            @RequestParam(required = false) ServiceInvoiceStatus status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(
                        actor.getUserAccountId(),
                        companyId,
                        month,
                        status,
                        search,
                        page,
                        size));
    }

    @GetMapping("/partners")
    public ResponseEntity<PagedResponse<ServiceInvoicePartnerOptionResponse>> partnerOptions(
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

    @GetMapping("/{invoiceId}")
    public ResponseEntity<ServiceInvoiceResponse> find(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.find(actor.getUserAccountId(), companyId, invoiceId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceInvoiceResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestBody ServiceInvoiceRequest request) {

        return service.create(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{invoiceId}")
    public ServiceInvoiceResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId,
            @RequestBody ServiceInvoiceRequest request) {

        return service.update(actor.getUserAccountId(), companyId, invoiceId, request);
    }

    @PostMapping("/{invoiceId}/issuance")
    public ServiceInvoiceResponse markIssued(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId,
            @RequestBody ServiceInvoiceStatusRequest request) {

        return service.markIssued(actor.getUserAccountId(), companyId, invoiceId, request);
    }

    @PostMapping("/{invoiceId}/cancellation")
    public ServiceInvoiceResponse cancel(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID invoiceId,
            @RequestBody ServiceInvoiceStatusRequest request) {

        return service.cancel(actor.getUserAccountId(), companyId, invoiceId, request);
    }
}
