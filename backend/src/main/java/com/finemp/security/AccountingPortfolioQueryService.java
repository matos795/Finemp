package com.finemp.security;

import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.security.AccountingPortfolioResponse.AccountingFirmNode;
import com.finemp.security.AccountingPortfolioResponse.ClientNode;
import com.finemp.security.AccountingPortfolioResponse.CompanyNode;
import com.finemp.security.AccountingPortfolioResponse.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta a carteira operacional do usuário da sessão.
 *
 * <ul>
 *   <li>SYSTEM_ADMIN ativo: todas as hierarquias, sem AccountingFirmAccessGrant.</li>
 *   <li>HIERARCHY_VIEW ou HIERARCHY_MANAGE: todos os Clients e Companies da contabilidade.</li>
 *   <li>Demais casos: somente Companies com CompanyAccessGrant e seus Client/AccountingFirm.
 *       Client e contabilidade sem Company autorizada não aparecem.</li>
 * </ul>
 *
 * Companies sem Client (legado não saneado) não entram na carteira. Nada aqui
 * cria grants nem permissões: é leitura.
 */
@Service
public class AccountingPortfolioQueryService {

    private static final Comparator<AccountingFirm> FIRM_ORDER =
            Comparator.comparing(AccountingFirm::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AccountingFirm::getId);
    private static final Comparator<Client> CLIENT_ORDER =
            Comparator.comparing(Client::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Client::getId);
    private static final Comparator<Company> COMPANY_ORDER =
            Comparator.comparing(Company::getLegalName, String.CASE_INSENSITIVE_ORDER).thenComparing(Company::getId);

    private final UserAccountRepository users;
    private final AccountingFirmRepository firms;
    private final ClientRepository clients;
    private final CompanyRepository companies;
    private final AccountingFirmAccessGrantRepository firmGrants;

    public AccountingPortfolioQueryService(
            UserAccountRepository users,
            AccountingFirmRepository firms,
            ClientRepository clients,
            CompanyRepository companies,
            AccountingFirmAccessGrantRepository firmGrants) {
        this.users = users;
        this.firms = firms;
        this.clients = clients;
        this.companies = companies;
        this.firmGrants = firmGrants;
    }

    @Transactional(readOnly = true)
    public AccountingPortfolioResponse findForUser(UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow();

        if (!user.isActive()) {
            return new AccountingPortfolioResponse(List.of());
        }

        if (user.getPlatformPermissions().contains(PlatformPermission.SYSTEM_ADMIN)) {
            return build(firms.findAll(), clients.findAllWithAccountingFirm(), companies.findAllWithHierarchy(),
                    firmId -> Scope.FULL_HIERARCHY);
        }

        Set<UUID> fullFirmIds = firmGrants.findAllByUserAccountId(userId).stream()
                .filter(grant -> !Collections.disjoint(grant.getPermissions(), AccountingFirmPermission.HIERARCHY_READ))
                .map(AccountingFirmAccessGrant::getAccountingFirm)
                .map(AccountingFirm::getId)
                .collect(Collectors.toSet());

        List<Company> visibleCompanies = new ArrayList<>(companies.findGrantedWithHierarchy(userId));
        List<Client> visibleClients = new ArrayList<>();
        List<AccountingFirm> visibleFirms = new ArrayList<>();

        if (!fullFirmIds.isEmpty()) {
            visibleFirms.addAll(firms.findAllById(fullFirmIds));
            visibleClients.addAll(clients.findAllWithAccountingFirmByAccountingFirmIdIn(fullFirmIds));
            visibleCompanies.addAll(companies.findAllWithHierarchyByAccountingFirmIdIn(fullFirmIds));
        }

        for (Company company : visibleCompanies) {
            visibleClients.add(company.getClient());
            visibleFirms.add(company.getClient().getAccountingFirm());
        }

        return build(visibleFirms, visibleClients, visibleCompanies,
                firmId -> fullFirmIds.contains(firmId) ? Scope.FULL_HIERARCHY : Scope.GRANTED_COMPANIES);
    }

    private AccountingPortfolioResponse build(
            List<AccountingFirm> firmList,
            List<Client> clientList,
            List<Company> companyList,
            java.util.function.Function<UUID, Scope> scopeOf) {

        Map<UUID, List<Company>> companiesByClient = new HashMap<>();
        for (Company company : distinct(companyList, Company::getId)) {
            companiesByClient.computeIfAbsent(company.getClient().getId(), key -> new ArrayList<>()).add(company);
        }

        Map<UUID, List<Client>> clientsByFirm = new HashMap<>();
        for (Client client : distinct(clientList, Client::getId)) {
            clientsByFirm.computeIfAbsent(client.getAccountingFirm().getId(), key -> new ArrayList<>()).add(client);
        }

        List<AccountingFirmNode> nodes = distinct(firmList, AccountingFirm::getId).stream()
                .sorted(FIRM_ORDER)
                .map(firm -> new AccountingFirmNode(
                        firm.getId(),
                        firm.getName(),
                        firm.isActive(),
                        scopeOf.apply(firm.getId()),
                        clientsByFirm.getOrDefault(firm.getId(), List.of()).stream()
                                .sorted(CLIENT_ORDER)
                                .map(client -> new ClientNode(
                                        client.getId(),
                                        client.getName(),
                                        client.isActive(),
                                        companiesByClient.getOrDefault(client.getId(), List.of()).stream()
                                                .sorted(COMPANY_ORDER)
                                                .map(company -> new CompanyNode(company.getId(), company.getLegalName()))
                                                .toList()))
                                .toList()))
                .toList();

        return new AccountingPortfolioResponse(nodes);
    }

    private static <T> List<T> distinct(List<T> items, java.util.function.Function<T, UUID> id) {
        Map<UUID, T> unique = new LinkedHashMap<>();
        for (T item : items) {
            unique.putIfAbsent(id.apply(item), item);
        }
        return new ArrayList<>(unique.values());
    }
}
