package com.finemp.planning.meeting;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.CompanyRepository;
import com.finemp.planning.PlanningAuthorization;
import com.finemp.planning.PlanningConflictException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanningMeetingService {

    private final PlanningMeetingRepository meetings;
    private final CompanyRepository companies;
    private final PlanningAuthorization authorization;

    public PlanningMeetingService(
            PlanningMeetingRepository meetings,
            CompanyRepository companies,
            PlanningAuthorization authorization) {
        this.meetings = meetings;
        this.companies = companies;
        this.authorization = authorization;
    }

    public PagedResponse<PlanningMeetingResponse> list(
            UUID actorId,
            UUID companyId,
            String stage,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);

        var normalizedStage = stage == null ? "" : stage.trim();
        if (normalizedStage.length() > 80) {
            throw new IllegalArgumentException("Filtro de etapa inválido.");
        }

        var result = meetings.search(
                companyId,
                normalizedStage,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("scheduledOn").ascending()
                                .and(Sort.by("id"))));

        return PagedResponse.from(result, PlanningMeetingResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanningMeetingResponse create(
            UUID actorId,
            UUID companyId,
            PlanningMeetingCreateRequest request) {

        authorization.requireManage(actorId, companyId);

        var company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);

        authorization.requireManage(actorId, companyId);

        var row = new PlanningMeeting(
                company,
                request.title(),
                request.stage(),
                request.scheduledOn(),
                request.notes(),
                request.diagnosticText());

        return PlanningMeetingResponse.from(
                meetings.saveAndFlush(row));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanningMeetingResponse update(
            UUID actorId,
            UUID companyId,
            UUID meetingId,
            PlanningMeetingUpdateRequest request) {

        authorization.requireManage(actorId, companyId);

        var row = locked(companyId, meetingId);

        authorization.requireManage(actorId, companyId);
        requireVersion(row.getVersion(), request.expectedVersion());

        row.replace(
                request.title(),
                request.stage(),
                request.scheduledOn(),
                request.notes(),
                request.diagnosticText());

        meetings.flush();
        return PlanningMeetingResponse.from(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(
            UUID actorId,
            UUID companyId,
            UUID meetingId,
            long expectedVersion) {

        authorization.requireManage(actorId, companyId);

        var row = locked(companyId, meetingId);

        authorization.requireManage(actorId, companyId);
        requireVersion(row.getVersion(), expectedVersion);

        meetings.delete(row);
        meetings.flush();
    }

    private PlanningMeeting locked(UUID companyId, UUID meetingId) {
        return meetings.findLockedByIdAndCompanyId(meetingId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException(
                                "Encontro de planejamento"));
    }

    private static void requireVersion(long current, Long expected) {
        if (expected == null || expected < 0) {
            throw new IllegalArgumentException(
                    "Versão esperada do encontro inválida.");
        }
        if (current != expected) {
            throw new PlanningConflictException(
                    PlanningConflictException.Reason.STALE_VERSION);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Paginação de encontros inválida.");
        }
    }
}
