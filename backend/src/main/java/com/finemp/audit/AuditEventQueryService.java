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
    private final PlatformPermissionAuthorizationService authorization;

    public AuditEventQueryService(
            AccessAuditEventRepository events,
            PlatformPermissionAuthorizationService authorization) {
        this.events = events;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> listGlobal(
            UUID actorId,
            int page,
            int size) {

        requireSystemAdmin(actorId);
        var pageable = pageRequest(page, size);

        return PagedResponse.from(
                events.findAll(pageable),
                AuditEventResponse::from);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> listCompany(
            UUID actorId,
            UUID companyId,
            int page,
            int size) {

        requireSystemAdmin(actorId);
        var pageable = pageRequest(page, size);

        return PagedResponse.from(
                events.findByCompanyId(companyId, pageable),
                AuditEventResponse::from);
    }

    private void requireSystemAdmin(UUID actorId) {
        if (!authorization.isSystemAdmin(actorId)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid audit page.");
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    }
}
