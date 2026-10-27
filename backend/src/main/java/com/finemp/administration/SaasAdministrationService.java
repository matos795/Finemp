package com.finemp.administration;

import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.AccountingFirmPermissionAuthorizationService;
import com.finemp.access.PlatformPermission;
import com.finemp.access.PlatformPermissionAuthorizationService;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SaasAdministrationService {

    private final AccountingFirmRepository accountingFirmRepository;
    private final ClientRepository clientRepository;
    private final CompanyRepository companyRepository;
    private final AccountingSourceRepository accountingSourceRepository;
    private final UserAccountRepository userAccountRepository;
    private final CompanyClientAssignmentEventRepository assignmentEventRepository;
    private final PlatformPermissionAuthorizationService platformAuthorizationService;
    private final AccountingFirmPermissionAuthorizationService accountingFirmAuthorizationService;

    public SaasAdministrationService(
            AccountingFirmRepository accountingFirmRepository,
            ClientRepository clientRepository,
            CompanyRepository companyRepository,
            AccountingSourceRepository accountingSourceRepository,
            UserAccountRepository userAccountRepository,
            CompanyClientAssignmentEventRepository assignmentEventRepository,
            PlatformPermissionAuthorizationService platformAuthorizationService,
            AccountingFirmPermissionAuthorizationService accountingFirmAuthorizationService) {
        this.accountingFirmRepository = accountingFirmRepository;
        this.clientRepository = clientRepository;
        this.companyRepository = companyRepository;
        this.accountingSourceRepository = accountingSourceRepository;
        this.userAccountRepository = userAccountRepository;
        this.assignmentEventRepository = assignmentEventRepository;
        this.platformAuthorizationService = platformAuthorizationService;
        this.accountingFirmAuthorizationService = accountingFirmAuthorizationService;
    }

    @Transactional(readOnly = true)
    public Page<AccountingFirm> listAccountingFirms(
            UUID userAccountId,
            String search,
            int page,
            int size) {
        requirePlatformPermission(
                userAccountId,
                PlatformPermission.ACCOUNTING_FIRM_VIEW);

        return accountingFirmRepository.findAllByNameContainingIgnoreCase(
                normalizedSearch(search),
                pageRequest(page, size, "name"));
    }

    @Transactional
    public AccountingFirm createAccountingFirm(
            UUID userAccountId,
            CreateAccountingFirmRequest request) {
        requirePlatformPermission(
                userAccountId,
                PlatformPermission.ACCOUNTING_FIRM_CREATE);

        return accountingFirmRepository.save(
                new AccountingFirm(request.name().trim(), true));
    }

    @Transactional(readOnly = true)
    public Page<Client> listClients(
            UUID userAccountId,
            UUID accountingFirmId,
            String search,
            int page,
            int size) {
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE);

        return clientRepository.findAllByAccountingFirmIdAndNameContainingIgnoreCase(
                accountingFirmId,
                normalizedSearch(search),
                pageRequest(page, size, "name"));
    }

    @Transactional
    public Client createClient(
            UUID userAccountId,
            UUID accountingFirmId,
            CreateClientRequest request) {
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE);

        AccountingFirm accountingFirm = accountingFirmRepository
                .findById(accountingFirmId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Accounting firm"));

        return clientRepository.save(
                new Client(accountingFirm, request.name().trim(), true));
    }

    @Transactional(readOnly = true)
    public Page<Company> listCompanies(
            UUID userAccountId,
            UUID accountingFirmId,
            UUID clientId,
            String search,
            int page,
            int size) {
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE);
        requireClient(accountingFirmId, clientId);

        return companyRepository.findAllByClientIdAndLegalNameContainingIgnoreCase(
                clientId,
                normalizedSearch(search),
                pageRequest(page, size, "legalName"));
    }

    @Transactional
    public Company createCompany(
            UUID userAccountId,
            UUID accountingFirmId,
            UUID clientId,
            CreateCompanyRequest request) {
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE);

        Client client = requireClient(accountingFirmId, clientId);

        return companyRepository.save(
                new Company(
                        client,
                        request.legalName().trim(),
                        request.cnpj()));
    }

    @Transactional
    public Company assignExistingCompany(
            UUID userAccountId,
            UUID accountingFirmId,
            UUID clientId,
            UUID companyId) {
        requirePlatformPermission(
                userAccountId,
                PlatformPermission.UNASSIGNED_COMPANY_ASSIGN);
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE);

        Client client = requireClient(accountingFirmId, clientId);
        Company company = companyRepository
                .findLockedById(companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Company"));

        if (company.getClient() != null) {
            if (company.getClient().getId().equals(clientId)) {
                return company;
            }
            throw new CompanyAssignmentConflictException();
        }

        company.assignToClient(client);
        assignmentEventRepository.save(
                new CompanyClientAssignmentEvent(
                        company,
                        client,
                        userAccountRepository.getReferenceById(userAccountId)));

        return company;
    }

    @Transactional(readOnly = true)
    public Page<AccountingSource> listAccountingSources(
            UUID userAccountId,
            UUID accountingFirmId,
            UUID clientId,
            UUID companyId,
            String search,
            int page,
            int size) {
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE);
        requireClient(accountingFirmId, clientId);
        requireCompany(clientId, companyId);

        return accountingSourceRepository.searchByCompany(
                companyId,
                normalizedSearch(search),
                pageRequest(page, size, "type"));
    }

    @Transactional
    public AccountingSource createAccountingSource(
            UUID userAccountId,
            UUID accountingFirmId,
            UUID clientId,
            UUID companyId,
            CreateAccountingSourceRequest request) {
        requireAccountingFirmPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE);
        requireClient(accountingFirmId, clientId);
        Company company = requireCompany(clientId, companyId);

        String externalReference = request.externalReference() == null
                ? null
                : request.externalReference().trim();

        return accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        request.type(),
                        externalReference,
                        true));
    }

    private Client requireClient(UUID accountingFirmId, UUID clientId) {
        return clientRepository
                .findByIdAndAccountingFirmId(clientId, accountingFirmId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Client"));
    }

    private Company requireCompany(UUID clientId, UUID companyId) {
        return companyRepository
                .findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Company"));
    }

    private void requirePlatformPermission(
            UUID userAccountId,
            PlatformPermission permission) {
        if (!platformAuthorizationService.hasPermission(userAccountId, permission)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void requireAccountingFirmPermission(
            UUID userAccountId,
            UUID accountingFirmId,
            AccountingFirmPermission permission) {
        if (!accountingFirmAuthorizationService.hasPermission(
                userAccountId,
                accountingFirmId,
                permission)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private PageRequest pageRequest(
            int page,
            int size,
            String sortProperty) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size between 1 and 100.");
        }
        return PageRequest.of(
                page,
                size,
                Sort.by(sortProperty).ascending().and(Sort.by("id").ascending()));
    }

    private String normalizedSearch(String search) {
        return search == null ? "" : search.trim();
    }
}
