package com.finemp.security;

import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.UserAccountRepository;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAccessQueryService {

    private final UserAccountRepository users;
    private final AccountingFirmAccessGrantRepository grants;

    public OrganizationAccessQueryService(UserAccountRepository users, AccountingFirmAccessGrantRepository grants) {
        this.users = users;
        this.grants = grants;
    }

    @Transactional(readOnly = true)
    public OrganizationAccessResponse findForUser(UUID userId) {
        var user = users.findById(userId).orElseThrow();
        var firms = grants.findAllByUserAccountId(userId).stream().map(grant -> {
            var firm = grant.getAccountingFirm();
            return new OrganizationAccessResponse.AccountingFirmAccess(
                    firm.getId(), firm.getName(), firm.isActive(), grant.getPermissions());
        }).sorted(Comparator.comparing(OrganizationAccessResponse.AccountingFirmAccess::name,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OrganizationAccessResponse.AccountingFirmAccess::accountingFirmId)).toList();
        return new OrganizationAccessResponse(user.getPlatformPermissions(), firms);
    }
}
