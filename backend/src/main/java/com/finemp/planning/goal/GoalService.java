package com.finemp.planning.goal;

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
public class GoalService {

    private final GoalRepository goals;
    private final GoalMeasurementRepository measurements;
    private final CompanyRepository companies;
    private final PlanningAuthorization authorization;

    public GoalService(
            GoalRepository goals,
            GoalMeasurementRepository measurements,
            CompanyRepository companies,
            PlanningAuthorization authorization) {
        this.goals = goals;
        this.measurements = measurements;
        this.companies = companies;
        this.authorization = authorization;
    }

    public PagedResponse<GoalResponse> list(
            UUID actorId,
            UUID companyId,
            Boolean active,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);

        var result = goals.search(
                companyId,
                active,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("dueOn").ascending()
                                .and(Sort.by("id"))));

        return PagedResponse.from(
                result,
                goal -> GoalResponse.from(
                        goal,
                        measurements
                                .findFirstByCompanyIdAndGoalIdOrderByMeasuredOnDescCreatedAtDescIdDesc(
                                        companyId,
                                        goal.getId())
                                .orElse(null)));
    }

    public GoalResponse find(
            UUID actorId,
            UUID companyId,
            UUID goalId) {

        authorization.requireRead(actorId, companyId);
        var goal = owned(companyId, goalId);

        return GoalResponse.from(
                goal,
                measurements
                        .findFirstByCompanyIdAndGoalIdOrderByMeasuredOnDescCreatedAtDescIdDesc(
                                companyId,
                                goalId)
                        .orElse(null));
    }

    public PagedResponse<GoalMeasurementResponse> measurements(
            UUID actorId,
            UUID companyId,
            UUID goalId,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);
        owned(companyId, goalId);

        var result = measurements.findByCompanyIdAndGoalId(
                companyId,
                goalId,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "measuredOn")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));

        return PagedResponse.from(
                result,
                GoalMeasurementResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GoalResponse create(
            UUID actorId,
            UUID companyId,
            GoalCreateRequest request) {

        authorization.requireManage(actorId, companyId);

        var company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);

        authorization.requireManage(actorId, companyId);

        var goal = new Goal(
                company,
                request.name(),
                request.direction(),
                request.baselineValue(),
                request.targetValue(),
                request.dueOn(),
                request.notes());

        return GoalResponse.from(
                goals.saveAndFlush(goal),
                null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GoalResponse update(
            UUID actorId,
            UUID companyId,
            UUID goalId,
            GoalUpdateRequest request) {

        authorization.requireManage(actorId, companyId);

        var goal = locked(companyId, goalId);

        authorization.requireManage(actorId, companyId);
        requireVersion(goal.getVersion(), request.expectedVersion());

        goal.replace(
                request.name(),
                request.direction(),
                request.baselineValue(),
                request.targetValue(),
                request.dueOn(),
                request.notes());

        goals.flush();

        return GoalResponse.from(
                goal,
                measurements
                        .findFirstByCompanyIdAndGoalIdOrderByMeasuredOnDescCreatedAtDescIdDesc(
                                companyId,
                                goalId)
                        .orElse(null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GoalResponse deactivate(
            UUID actorId,
            UUID companyId,
            UUID goalId,
            long expectedVersion) {

        authorization.requireManage(actorId, companyId);

        var goal = locked(companyId, goalId);

        authorization.requireManage(actorId, companyId);
        requireVersion(goal.getVersion(), expectedVersion);

        goal.deactivate();
        goals.flush();

        return GoalResponse.from(
                goal,
                measurements
                        .findFirstByCompanyIdAndGoalIdOrderByMeasuredOnDescCreatedAtDescIdDesc(
                                companyId,
                                goalId)
                        .orElse(null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GoalMeasurementResponse measure(
            UUID actorId,
            UUID companyId,
            UUID goalId,
            GoalMeasurementRequest request) {

        authorization.requireManage(actorId, companyId);

        var goal = locked(companyId, goalId);

        authorization.requireManage(actorId, companyId);

        if (!goal.isActive()) {
            throw new IllegalArgumentException(
                    "Meta inativa não pode receber medição.");
        }

        var measurement = new GoalMeasurement(
                goal,
                request.measuredOn(),
                request.measuredValue(),
                request.notes());

        return GoalMeasurementResponse.from(
                measurements.saveAndFlush(measurement));
    }

    private Goal owned(UUID companyId, UUID goalId) {
        return goals.findByIdAndCompanyId(goalId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Meta"));
    }

    private Goal locked(UUID companyId, UUID goalId) {
        return goals.findLockedByIdAndCompanyId(goalId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Meta"));
    }

    private static void requireVersion(long current, Long expected) {
        if (expected == null || expected < 0) {
            throw new IllegalArgumentException(
                    "Versão esperada da meta inválida.");
        }
        if (current != expected) {
            throw new PlanningConflictException(
                    PlanningConflictException.Reason.STALE_VERSION);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Paginação de metas inválida.");
        }
    }
}
