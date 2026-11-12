package com.finemp.administration.users;

import com.finemp.access.PlatformPermission;
import com.finemp.access.PlatformPermissionAuthorizationService;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.audit.AccessAuditAction;
import com.finemp.audit.AccessAuditRecorder;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class PlatformUserAdministrationService {

    private final UserAccountRepository users;
    private final PlatformPermissionAuthorizationService authorization;
    private final PlatformAdministrationLock lock;
    private final AccessAuditRecorder audit;

    public PlatformUserAdministrationService(UserAccountRepository users,
            PlatformPermissionAuthorizationService authorization, PlatformAdministrationLock lock, AccessAuditRecorder audit) {
        this.users = users;
        this.authorization = authorization;
        this.lock = lock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserAdministrationSummary> list(UUID actorId, String search, int page, int size) {
        requireAdministrator(actorId);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page.");
        var result = users.searchForAdministration(search == null ? "" : search.trim(),
                PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
        return PagedResponse.from(result, UserAdministrationSummary::from);
    }

    @Transactional(readOnly = true)
    public UserAdministrationDetail find(UUID actorId, UUID targetId) {
        requireAdministrator(actorId);
        var target = users.findById(targetId).orElseThrow(() -> new SaasResourceNotFoundException("User"));
        return new UserAdministrationDetail(UserAdministrationSummary.from(target), target.getPlatformPermissions());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserAdministrationSummary setActive(UUID actorId, UUID targetId, boolean active) {
        lockAndRevalidate(actorId);
        var target = lockedUser(targetId);
        requireGlobalDelegation(actorId, target.getPlatformPermissions());
        if (active) {
            if (target.isActive()) return UserAdministrationSummary.from(target);
            try {
                target.reactivate();
            } catch (IllegalStateException pending) {
                throw new PlatformUserConflictException("A identidade precisa definir a própria senha antes da reativação.");
            }
        } else {
            if (!target.isActive() && !target.isInvitationPending()) return UserAdministrationSummary.from(target);
            protectLastAdministrator(target, false, target.getPlatformPermissions());
            target.deactivate();
        }
        audit.record(active ? AccessAuditAction.USER_REACTIVATED : AccessAuditAction.USER_DEACTIVATED,
                actorId, targetId, null, null, target.getPlatformPermissions());
        return UserAdministrationSummary.from(target);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void replacePermissions(UUID actorId, UUID targetId, Set<PlatformPermission> requested) {
        lockAndRevalidate(actorId);
        var target = lockedUser(targetId);
        Set<PlatformPermission> permissions = Set.copyOf(requested);
        requireGlobalDelegation(actorId, target.getPlatformPermissions());
        requireGlobalDelegation(actorId, permissions);
        protectLastAdministrator(target, target.isActive(), permissions);
        if (target.getPlatformPermissions().equals(permissions)) return;
        target.replacePlatformPermissions(permissions);
        audit.record(AccessAuditAction.PLATFORM_PERMISSIONS_REPLACED, actorId, targetId, null, null, permissions);
    }

    private void lockAndRevalidate(UUID actorId) {
        requireAdministrator(actorId);
        lock.acquire();
        requireAdministrator(actorId);
    }

    private UserAccount lockedUser(UUID id) {
        return users.findLockedById(id).orElseThrow(() -> new SaasResourceNotFoundException("User"));
    }

    private void requireAdministrator(UUID actorId) {
        if (!authorization.hasPermission(actorId, PlatformPermission.PLATFORM_USER_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void requireGlobalDelegation(UUID actorId, Set<PlatformPermission> permissions) {
        if (permissions.contains(PlatformPermission.SYSTEM_ADMIN) && !authorization.isSystemAdmin(actorId)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void protectLastAdministrator(UserAccount target, boolean requestedActive, Set<PlatformPermission> requested) {
        if (target.isActive() && target.getPlatformPermissions().contains(PlatformPermission.SYSTEM_ADMIN)
                && (!requestedActive || !requested.contains(PlatformPermission.SYSTEM_ADMIN))
                && users.countActiveByPlatformPermission(PlatformPermission.SYSTEM_ADMIN) <= 1) {
            throw new PlatformUserConflictException("Não é permitido remover o último administrador do sistema ativo.");
        }
        var administratorPermissions = Set.of(PlatformPermission.PLATFORM_USER_MANAGE, PlatformPermission.SYSTEM_ADMIN);
        boolean currentlyAdministrator = target.isActive()
                && target.getPlatformPermissions().stream().anyMatch(administratorPermissions::contains);
        boolean remainsAdministrator = requestedActive && requested.stream().anyMatch(administratorPermissions::contains);
        if (currentlyAdministrator && !remainsAdministrator
                && users.countActiveWithAnyPermission(administratorPermissions) <= 1) {
            throw new PlatformUserConflictException("Não é permitido remover o último administrador ativo da plataforma.");
        }
    }
}
