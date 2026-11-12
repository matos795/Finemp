package com.finemp.sales.stage;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.CompanyRepository;
import com.finemp.sales.SalesAuthorization;
import com.finemp.sales.SalesConflictException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesStageService {

    private final SalesStageRepository stages;
    private final CompanyRepository companies;
    private final SalesAuthorization authorization;

    public SalesStageService(
            SalesStageRepository stages,
            CompanyRepository companies,
            SalesAuthorization authorization) {
        this.stages = stages;
        this.companies = companies;
        this.authorization = authorization;
    }

    public PagedResponse<SalesStageResponse> list(
            UUID actorId,
            UUID companyId,
            Boolean active,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("position").ascending()
                        .and(Sort.by("id")));

        var result = active == null
                ? stages.findByCompanyId(companyId, pageable)
                : stages.findByCompanyIdAndActive(
                        companyId,
                        active,
                        pageable);

        return PagedResponse.from(result, SalesStageResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesStageResponse create(
            UUID actorId,
            UUID companyId,
            SalesStageCreateRequest request) {

        authorization.requireManage(actorId, companyId);

        var company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);

        authorization.requireManage(actorId, companyId);

        var stage = new SalesStage(
                company,
                request.name(),
                request.position());

        return SalesStageResponse.from(
                stages.saveAndFlush(stage));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesStageResponse update(
            UUID actorId,
            UUID companyId,
            UUID stageId,
            SalesStageUpdateRequest request) {

        authorization.requireManage(actorId, companyId);

        var stage = locked(companyId, stageId);

        authorization.requireManage(actorId, companyId);
        requireVersion(stage.getVersion(), request.expectedVersion());

        stage.replace(request.name(), request.position());
        stages.flush();

        return SalesStageResponse.from(stage);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesStageResponse deactivate(
            UUID actorId,
            UUID companyId,
            UUID stageId,
            long expectedVersion) {

        authorization.requireManage(actorId, companyId);

        var stage = locked(companyId, stageId);

        authorization.requireManage(actorId, companyId);
        requireVersion(stage.getVersion(), expectedVersion);

        stage.deactivate();
        stages.flush();

        return SalesStageResponse.from(stage);
    }

    private SalesStage locked(UUID companyId, UUID stageId) {
        return stages.findLockedByIdAndCompanyId(stageId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Etapa comercial"));
    }

    private static void requireVersion(long current, Long expected) {
        if (expected == null || expected < 0) {
            throw new IllegalArgumentException("Versão esperada inválida.");
        }
        if (current != expected) {
            throw new SalesConflictException(
                    SalesConflictException.Reason.STALE_VERSION);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Paginação de etapas inválida.");
        }
    }
}
