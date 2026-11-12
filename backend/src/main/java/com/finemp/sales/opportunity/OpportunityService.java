package com.finemp.sales.opportunity;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerRepository;
import com.finemp.finance.partner.PartnerRole;
import com.finemp.sales.SalesAuthorization;
import com.finemp.sales.SalesConflictException;
import com.finemp.sales.stage.SalesStage;
import com.finemp.sales.stage.SalesStageRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OpportunityService {

    private final OpportunityRepository opportunities;
    private final OpportunityStageHistoryRepository history;
    private final SalesStageRepository stages;
    private final BusinessPartnerRepository partners;
    private final CompanyRepository companies;
    private final SalesAuthorization authorization;
    private final Clock clock;

    public OpportunityService(
            OpportunityRepository opportunities,
            OpportunityStageHistoryRepository history,
            SalesStageRepository stages,
            BusinessPartnerRepository partners,
            CompanyRepository companies,
            SalesAuthorization authorization,
            Clock clock) {

        this.opportunities = opportunities;
        this.history = history;
        this.stages = stages;
        this.partners = partners;
        this.companies = companies;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PagedResponse<OpportunityResponse> list(
            UUID actorId,
            UUID companyId,
            OpportunityOutcome outcome,
            UUID stageId,
            UUID partnerId,
            String responsible,
            String search,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);

        var normalizedResponsible =
                responsible == null ? "" : responsible.trim();
        var normalizedSearch =
                search == null ? "" : search.trim();

        if (normalizedResponsible.length() > 160
                || normalizedSearch.length() > 200) {
            throw new IllegalArgumentException("Filtro comercial inválido.");
        }

        var result = opportunities.search(
                companyId,
                outcome,
                stageId,
                partnerId,
                normalizedResponsible,
                normalizedSearch,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("expectedCloseOn").ascending()
                                .and(Sort.by("id"))));

        return PagedResponse.from(
                result,
                opportunity -> response(companyId, opportunity));
    }

    public OpportunityResponse find(
            UUID actorId,
            UUID companyId,
            UUID opportunityId) {

        authorization.requireRead(actorId, companyId);
        return response(
                companyId,
                owned(companyId, opportunityId));
    }

    public PagedResponse<OpportunityStageHistoryResponse> history(
            UUID actorId,
            UUID companyId,
            UUID opportunityId,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);
        owned(companyId, opportunityId);

        var result = history.findByCompanyIdAndOpportunityId(
                companyId,
                opportunityId,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "occurredAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))));

        return PagedResponse.from(
                result,
                OpportunityStageHistoryResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse create(
            UUID actorId,
            UUID companyId,
            OpportunityCreateRequest request) {

        authorization.requireManage(actorId, companyId);

        var company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);

        authorization.requireManage(actorId, companyId);

        var stage = activeStage(companyId, request.stageId());
        var partner = partner(companyId, request.businessPartnerId());

        var opportunity = new Opportunity(
                company,
                partner,
                stage,
                request.title(),
                request.amount(),
                request.monthlyRecurring(),
                request.probability(),
                request.responsible(),
                request.expectedCloseOn(),
                LocalDate.now(clock),
                request.notes());

        opportunity = opportunities.saveAndFlush(opportunity);
        appendHistory(actorId, opportunity, stage);

        return response(companyId, opportunity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse update(
            UUID actorId,
            UUID companyId,
            UUID opportunityId,
            OpportunityUpdateRequest request) {

        authorization.requireManage(actorId, companyId);

        var opportunity = locked(companyId, opportunityId);

        authorization.requireManage(actorId, companyId);
        requireVersion(
                opportunity.getVersion(),
                request.expectedVersion());

        var partner = partner(companyId, request.businessPartnerId());

        opportunity.replace(
                partner,
                request.title(),
                request.amount(),
                request.monthlyRecurring(),
                request.probability(),
                request.responsible(),
                request.expectedCloseOn(),
                request.notes());

        opportunities.flush();
        return response(companyId, opportunity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse moveStage(
            UUID actorId,
            UUID companyId,
            UUID opportunityId,
            OpportunityMoveStageRequest request) {

        authorization.requireManage(actorId, companyId);

        var opportunity = locked(companyId, opportunityId);

        authorization.requireManage(actorId, companyId);
        requireVersion(
                opportunity.getVersion(),
                request.expectedVersion());

        if (opportunity.getOutcome() != OpportunityOutcome.OPEN) {
            throw new IllegalArgumentException(
                    "Reabra a oportunidade antes de mover a etapa.");
        }

        if (opportunity.getStage().getId().equals(request.stageId())) {
            return response(companyId, opportunity);
        }

        var stage = activeStage(companyId, request.stageId());
        opportunity.moveTo(stage);
        opportunities.flush();
        appendHistory(actorId, opportunity, stage);

        return response(companyId, opportunity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse setOutcome(
            UUID actorId,
            UUID companyId,
            UUID opportunityId,
            OpportunityOutcomeRequest request) {

        authorization.requireManage(actorId, companyId);

        var opportunity = locked(companyId, opportunityId);

        authorization.requireManage(actorId, companyId);
        requireVersion(
                opportunity.getVersion(),
                request.expectedVersion());

        if (opportunity.getOutcome() == request.outcome()) {
            return response(companyId, opportunity);
        }

        var today = LocalDate.now(clock);

        switch (request.outcome()) {
            case OPEN -> opportunity.reopen();
            case WON -> opportunity.win(today);
            case LOST -> opportunity.lose(today);
        }

        opportunities.flush();
        return response(companyId, opportunity);
    }

    private void appendHistory(
            UUID actorId,
            Opportunity opportunity,
            SalesStage stage) {

        history.saveAndFlush(new OpportunityStageHistory(
                opportunity,
                stage,
                actorId,
                clock.instant()));
    }

    private OpportunityResponse response(
            UUID companyId,
            Opportunity opportunity) {

        var highest = history
                .findFirstByCompanyIdAndOpportunityIdOrderByStagePositionDescOccurredAtDescIdDesc(
                        companyId,
                        opportunity.getId())
                .orElse(null);

        return OpportunityResponse.from(opportunity, highest);
    }

    private Opportunity owned(UUID companyId, UUID opportunityId) {
        return opportunities.findByIdAndCompanyId(
                        opportunityId,
                        companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Oportunidade"));
    }

    private Opportunity locked(UUID companyId, UUID opportunityId) {
        return opportunities.findLockedByIdAndCompanyId(
                        opportunityId,
                        companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Oportunidade"));
    }

    private SalesStage activeStage(UUID companyId, UUID stageId) {
        var stage = stages.findByIdAndCompanyId(stageId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Etapa comercial"));

        if (!stage.isActive()) {
            throw new IllegalArgumentException("Etapa comercial inativa.");
        }

        return stage;
    }

    private BusinessPartner partner(UUID companyId, UUID partnerId) {
        if (partnerId == null) return null;

        var partner = partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException("Cliente comercial"));

        var profile = partner.getProfile();

        if (!profile.active()
                || profile.role() == PartnerRole.SUPPLIER) {
            throw new IllegalArgumentException(
                    "Parceiro não está disponível como cliente comercial.");
        }

        return partner;
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
            throw new IllegalArgumentException(
                    "Paginação de oportunidades inválida.");
        }
    }
}
