package com.finemp.identity;

import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.AccountingFirmPermissionAuthorizationService;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirmRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FirmInvitationService {

    private final AccountingFirmPermissionAuthorizationService authorization;
    private final AccountingFirmRepository firms;
    private final UserAccountRepository users;
    private final AccountingFirmAccessGrantRepository grants;
    private final AccountActionService actions;

    public FirmInvitationService(AccountingFirmPermissionAuthorizationService authorization,
                                 AccountingFirmRepository firms, UserAccountRepository users,
                                 AccountingFirmAccessGrantRepository grants, AccountActionService actions) {
        this.authorization = authorization;
        this.firms = firms;
        this.users = users;
        this.grants = grants;
        this.actions = actions;
    }

    @Transactional
    public void invite(UUID actorId, UUID firmId, InviteFirmUserRequest request) {
        if (!authorization.hasPermission(actorId, firmId, AccountingFirmPermission.USER_ACCESS_MANAGE)) {
            throw new AccessDeniedException("Acesso administrativo não autorizado.");
        }
        var firm = firms.findById(firmId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        actions.inviteNewUser(request.name(), request.email());
        // The action service holds the identity lock until this transaction commits.
        UUID userId = users.findIdByEmailIgnoreCase(request.email().trim()).orElseThrow();
        var user = users.findLockedById(userId).orElseThrow();
        var grant = grants.findByUserAccountIdAndAccountingFirmId(userId, firmId)
                .orElseGet(() -> grants.save(new AccountingFirmAccessGrant(user, firm, java.util.Set.of())));
        grant.addPermissions(request.permissions());
        // No company/financial permission is granted by an invitation.
    }
}
