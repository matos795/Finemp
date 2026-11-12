package com.finemp.audit;

import com.finemp.access.PlatformPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventQueryService {

    private final AccessAuditEventRepository events;
    private final PlatformPermissionAuthorizationService platformAuthorization;

    public AuditEventQueryService(
            AccessAuditEventRepository events,
            PlatformPermissionAuthorizationService platformAuthorization) {
        this.events = events;
        this.platformAuthorization = platformAuthorization;
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> listGlobal(
            UUID actorUserAccountId,
            int page,
            int size) {

        requireSystemAdmin(actorUserAccountId);

        var result = events.findAll(
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "occurredAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))));

        return PagedResponse.from(result, AuditEventResponse::from);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> listCompany(
            UUID actorUserAccountId,
            UUID companyId,
            int page,
            int size) {

        requireSystemAdmin(actorUserAccountId);

        var result = events.findByCompanyId(
                companyId,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "occurredAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))));

        return PagedResponse.from(result, AuditEventResponse::from);
    }

    private void requireSystemAdmin(UUID actorUserAccountId) {
        if (!platformAuthorization.isSystemAdmin(actorUserAccountId)) {
            throw new OrganizationAccessDeniedException();
        }
    }
}
