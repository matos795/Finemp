package com.finemp.planning.action;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.CompanyRepository;
import com.finemp.planning.PlanningAuthorization;
import com.finemp.planning.PlanningConflictException;
import com.finemp.planning.goal.Goal;
import com.finemp.planning.goal.GoalRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ActionPlanService {

    private final ActionPlanItemRepository actions;
    private final GoalRepository goals;
    private final CompanyRepository companies;
    private final PlanningAuthorization authorization;
    private final Clock clock;

    public ActionPlanService(
            ActionPlanItemRepository actions,
            GoalRepository goals,
            CompanyRepository companies,
            PlanningAuthorization authorization,
            Clock clock) {
        this.actions = actions;
        this.goals = goals;
        this.companies = companies;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PagedResponse<ActionPlanResponse> list(
            UUID actorId,
            UUID companyId,
            String status,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);

        var normalizedStatus = status == null ? "" : status.trim();
        if (normalizedStatus.length() > 40) {
            throw new IllegalArgumentException(
                    "Filtro de status inválido.");
        }

        var result = actions.search(
                companyId,
                normalizedStatus,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("dueOn").ascending()
                                .and(Sort.by("id"))));

        return PagedResponse.from(
                result,
                ActionPlanResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ActionPlanResponse create(
            UUID actorId,
            UUID companyId,
            ActionPlanCreateRequest request) {

        authorization.requireManage(actorId, companyId);

        var company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);

        authorization.requireManage(actorId, companyId);

        var goal = ownedGoal(companyId, request.goalId());

        var item = new ActionPlanItem(
                company,
                goal,
                request.what(),
                request.why(),
                request.responsible(),
                request.dueOn(),
                request.where(),
                request.how(),
                request.cost(),
                request.workflowStatus());

        return ActionPlanResponse.from(
                actions.saveAndFlush(item));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ActionPlanResponse update(
            UUID actorId,
            UUID companyId,
            UUID actionId,
            ActionPlanUpdateRequest request) {

        authorization.requireManage(actorId, companyId);

        var item = locked(companyId, actionId);

        authorization.requireManage(actorId, companyId);
        requireVersion(item.getVersion(), request.expectedVersion());

        var goal = ownedGoal(companyId, request.goalId());

        item.replace(
                goal,
                request.what(),
                request.why(),
                request.responsible(),
                request.dueOn(),
                request.where(),
                request.how(),
                request.cost(),
                request.workflowStatus());

        actions.flush();
        return ActionPlanResponse.from(item);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ActionPlanResponse setCompleted(
            UUID actorId,
            UUID companyId,
            UUID actionId,
            ActionCompletionRequest request) {

        authorization.requireManage(actorId, companyId);

        var item = locked(companyId, actionId);

        authorization.requireManage(actorId, companyId);
        requireVersion(item.getVersion(), request.expectedVersion());

        if (request.completed()) {
            item.complete(clock.instant());
        } else {
            item.reopen();
        }

        actions.flush();
        return ActionPlanResponse.from(item);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(
            UUID actorId,
            UUID companyId,
            UUID actionId,
            long expectedVersion) {

        authorization.requireManage(actorId, companyId);

        var item = locked(companyId, actionId);

        authorization.requireManage(actorId, companyId);
        requireVersion(item.getVersion(), expectedVersion);

        actions.delete(item);
        actions.flush();
    }

    private Goal ownedGoal(UUID companyId, UUID goalId) {
        if (goalId == null) return null;

        return goals.findByIdAndCompanyId(goalId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Meta"));
    }

    private ActionPlanItem locked(UUID companyId, UUID actionId) {
        return actions.findLockedByIdAndCompanyId(actionId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException(
                                "Ação do plano"));
    }

    private static void requireVersion(long current, Long expected) {
        if (expected == null || expected < 0) {
            throw new IllegalArgumentException(
                    "Versão esperada da ação inválida.");
        }
        if (current != expected) {
            throw new PlanningConflictException(
                    PlanningConflictException.Reason.STALE_VERSION);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Paginação de ações inválida.");
        }
    }
}
