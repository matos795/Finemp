package com.finemp.administration;

import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class SaasAdministrationController {

    private final SaasAdministrationService administrationService;

    public SaasAdministrationController(
            SaasAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/admin/accounting-firms")
    public PagedResponse<AccountingFirmResponse> listAccountingFirms(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PagedResponse.from(
                administrationService.listAccountingFirms(
                        principal.getUserAccountId(),
                        search,
                        page,
                        size),
                AccountingFirmResponse::from);
    }

    @PostMapping("/admin/accounting-firms")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountingFirmResponse createAccountingFirm(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @Valid @RequestBody CreateAccountingFirmRequest request) {
        return AccountingFirmResponse.from(
                administrationService.createAccountingFirm(
                        principal.getUserAccountId(),
                        request));
    }

    @DeleteMapping("/admin/accounting-firms/{accountingFirmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccountingFirm(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId) {
        administrationService.deleteAccountingFirm(
                principal.getUserAccountId(),
                accountingFirmId);
    }

    @GetMapping("/accounting-firms/{accountingFirmId}/clients")
    public PagedResponse<ClientResponse> listClients(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PagedResponse.from(
                administrationService.listClients(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        search,
                        page,
                        size),
                ClientResponse::from);
    }

    @PostMapping("/accounting-firms/{accountingFirmId}/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse createClient(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @Valid @RequestBody CreateClientRequest request) {
        return ClientResponse.from(
                administrationService.createClient(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        request));
    }

    @DeleteMapping("/accounting-firms/{accountingFirmId}/clients/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClient(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @PathVariable UUID clientId) {
        administrationService.deleteClient(
                principal.getUserAccountId(),
                accountingFirmId,
                clientId);
    }

    @GetMapping("/accounting-firms/{accountingFirmId}/clients/{clientId}/companies")
    public PagedResponse<CompanyResponse> listCompanies(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @PathVariable UUID clientId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PagedResponse.from(
                administrationService.listCompanies(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        clientId,
                        search,
                        page,
                        size),
                CompanyResponse::from);
    }

    @PostMapping("/accounting-firms/{accountingFirmId}/clients/{clientId}/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @PathVariable UUID clientId,
            @Valid @RequestBody CreateCompanyRequest request) {
        return CompanyResponse.from(
                administrationService.createCompany(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        clientId,
                        request));
    }

    @PutMapping("/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/assignment")
    public CompanyResponse assignExistingCompany(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @PathVariable UUID clientId,
            @PathVariable UUID companyId) {
        return CompanyResponse.from(
                administrationService.assignExistingCompany(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        clientId,
                        companyId));
    }

    @GetMapping("/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/accounting-sources")
    public PagedResponse<AccountingSourceResponse> listAccountingSources(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @PathVariable UUID clientId,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PagedResponse.from(
                administrationService.listAccountingSources(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        clientId,
                        companyId,
                        search,
                        page,
                        size),
                AccountingSourceResponse::from);
    }

    @PostMapping("/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/accounting-sources")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountingSourceResponse createAccountingSource(
            @AuthenticationPrincipal UserAccountPrincipal principal,
            @PathVariable UUID accountingFirmId,
            @PathVariable UUID clientId,
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateAccountingSourceRequest request) {
        return AccountingSourceResponse.from(
                administrationService.createAccountingSource(
                        principal.getUserAccountId(),
                        accountingFirmId,
                        clientId,
                        companyId,
                        request));
    }
}
