package com.finemp.sales.activity;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerRepository;
import com.finemp.finance.partner.PartnerRole;
import com.finemp.sales.SalesAuthorization;
import com.finemp.sales.SalesConflictException;
import com.finemp.sales.opportunity.Opportunity;
import com.finemp.sales.opportunity.OpportunityRepository;
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
public class SalesActivityService {

    private final SalesActivityRepository activities;
    private final OpportunityRepository opportunities;
    private final BusinessPartnerRepository partners;
    private final CompanyRepository companies;
    private final SalesAuthorization authorization;
    private final Clock clock;

    public SalesActivityService(
            SalesActivityRepository activities,
            OpportunityRepository opportunities,
            BusinessPartnerRepository partners,
            CompanyRepository companies,
            SalesAuthorization authorization,
            Clock clock) {

        this.activities = activities;
        this.opportunities = opportunities;
        this.partners = partners;
        this.companies = companies;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PagedResponse<SalesActivityResponse> list(
            UUID actorId,
            UUID companyId,
            UUID opportunityId,
            UUID partnerId,
            SalesActivityFilter filter,
            String search,
            int page,
            int size) {

        authorization.requireRead(actorId, companyId);
        validatePage(page, size);

        var normalizedSearch =
                search == null ? "" : search.trim();

        if (normalizedSearch.length() > 200) {
            throw new IllegalArgumentException("Busca comercial inválida.");
        }

        var effectiveFilter =
                filter == null ? SalesActivityFilter.ALL : filter;
        var today = LocalDate.now(clock);

        var result = activities.search(
                companyId,
                opportunityId,
                partnerId,
                effectiveFilter.name(),
                today,
                normalizedSearch,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("dueOn").ascending()
                                .and(Sort.by("id"))));

        return PagedResponse.from(
                result,
                activity -> SalesActivityResponse.from(
                        activity,
                        today));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesActivityResponse create(
            UUID actorId,
            UUID companyId,
            SalesActivityCreateRequest request) {

        authorization.requireManage(actorId, companyId);

        var company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);

        authorization.requireManage(actorId, companyId);

        var links = links(
                companyId,
                request.opportunityId(),
                request.businessPartnerId());

        var activity = new SalesActivity(
                company,
                links.opportunity(),
                links.partner(),
                request.title(),
                request.notes(),
                request.responsible(),
                request.dueOn());

        return response(activities.saveAndFlush(activity));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesActivityResponse update(
            UUID actorId,
            UUID companyId,
            UUID activityId,
            SalesActivityUpdateRequest request) {

        authorization.requireManage(actorId, companyId);

        var activity = locked(companyId, activityId);

        authorization.requireManage(actorId, companyId);
        requireVersion(
                activity.getVersion(),
                request.expectedVersion());

        var links = links(
                companyId,
                request.opportunityId(),
                request.businessPartnerId());

        activity.replace(
                links.opportunity(),
                links.partner(),
                request.title(),
                request.notes(),
                request.responsible(),
                request.dueOn());

        activities.flush();
        return response(activity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesActivityResponse command(
            UUID actorId,
            UUID companyId,
            UUID activityId,
            SalesActivityCommandRequest request) {

        authorization.requireManage(actorId, companyId);

        var activity = locked(companyId, activityId);

        authorization.requireManage(actorId, companyId);
        requireVersion(
                activity.getVersion(),
                request.expectedVersion());

        switch (request.command()) {
            case COMPLETE -> activity.complete(clock.instant());
            case CANCEL -> activity.cancel(clock.instant());
            case REOPEN -> activity.reopen();
        }

        activities.flush();
        return response(activity);
    }

    private SalesActivityResponse response(SalesActivity activity) {
        return SalesActivityResponse.from(
                activity,
                LocalDate.now(clock));
    }

    private Links links(
            UUID companyId,
            UUID opportunityId,
            UUID partnerId) {

        Opportunity opportunity = null;
        BusinessPartner partner = null;

        if (opportunityId != null) {
            opportunity = opportunities
                    .findByIdAndCompanyId(opportunityId, companyId)
                    .orElseThrow(() ->
                            new SaasResourceNotFoundException(
                                    "Oportunidade"));
        }

        if (partnerId != null) {
            partner = commercialPartner(companyId, partnerId);
        }

        if (opportunity != null
                && opportunity.getBusinessPartner() != null) {

            var opportunityPartner = opportunity.getBusinessPartner();

            if (partner == null) {
                partner = opportunityPartner;
            } else if (!opportunityPartner.getId().equals(partner.getId())) {
                throw new IllegalArgumentException(
                        "Parceiro não corresponde à oportunidade.");
            }
        }

        return new Links(opportunity, partner);
    }

    private BusinessPartner commercialPartner(
            UUID companyId,
            UUID partnerId) {

        var partner = partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException(
                                "Cliente comercial"));

        var profile = partner.getProfile();

        if (!profile.active()
                || profile.role() == PartnerRole.SUPPLIER) {
            throw new IllegalArgumentException(
                    "Parceiro não está disponível como cliente comercial.");
        }

        return partner;
    }

    private SalesActivity locked(UUID companyId, UUID activityId) {
        return activities.findLockedByIdAndCompanyId(
                        activityId,
                        companyId)
                .orElseThrow(() ->
                        new SaasResourceNotFoundException(
                                "Atividade comercial"));
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
                    "Paginação de atividades inválida.");
        }
    }

    private record Links(
            Opportunity opportunity,
            BusinessPartner partner) {
    }
}
