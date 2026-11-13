package com.finemp.sales;

import com.finemp.administration.*;
import com.finemp.company.*;
import com.finemp.finance.partner.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class OpportunityService {

    private final OpportunityRepository opportunities;
    private final CompanyRepository companies;
    private final BusinessPartnerRepository partners;
    private final SalesActivityRepository activities;
    private final SalesAuthorization authorization;
    private final Clock clock;

    public OpportunityService(
            OpportunityRepository opportunities,
            CompanyRepository companies,
            BusinessPartnerRepository partners,
            SalesActivityRepository activities,
            SalesAuthorization authorization,
            Clock clock) {
        this.opportunities = opportunities;
        this.companies = companies;
        this.partners = partners;
        this.activities = activities;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PagedResponse<OpportunitySummaryResponse> list(
            UUID actor,
            UUID companyId,
            String search,
            String responsible,
            String stage,
            OpportunityStatus status,
            int page,
            int size) {

        authorization.requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > 100
                || tooLong(search, 180) || tooLong(responsible, 160) || tooLong(stage, 80)) {
            throw invalid();
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));

        return PagedResponse.from(
                opportunities.search(
                        companyId,
                        normalized(search),
                        normalized(responsible),
                        normalized(stage),
                        status,
                        pageable),
                OpportunitySummaryResponse::from);
    }

    public OpportunityResponse find(UUID actor, UUID companyId, UUID opportunityId) {
        authorization.requireRead(actor, companyId);
        return OpportunityResponse.from(owned(companyId, opportunityId));
    }

    public PagedResponse<SalesPartnerOptionResponse> partnerOptions(
            UUID actor,
            UUID companyId,
            String search,
            boolean includeInactive,
            int page,
            int size) {
        authorization.requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > 100 || tooLong(search, 100)) throw invalid();

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("displayNameKey").ascending().and(Sort.by("id")));
        var result = includeInactive
                ? partners.searchLookup(companyId, normalized(search), pageable)
                : partners.searchActiveLookup(companyId, normalized(search), pageable);

        return PagedResponse.from(result, SalesPartnerOptionResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse create(UUID actor, UUID companyId, OpportunityRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();

        var opportunity = new Opportunity(
                company,
                partner(companyId, request.businessPartnerId()),
                values(request),
                clock.instant());

        return OpportunityResponse.from(opportunities.saveAndFlush(opportunity));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse update(
            UUID actor,
            UUID companyId,
            UUID opportunityId,
            OpportunityRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        Opportunity opportunity = owned(companyId, opportunityId);
        requireVersion(opportunity, request.expectedVersion());
        BusinessPartner nextPartner = partner(companyId, request.businessPartnerId());
        if (nextPartner != null
                && activities.existsByCompanyIdAndOpportunityIdAndBusinessPartnerIdNot(
                        companyId, opportunityId, nextPartner.getId())) {
            throw new SalesRelationshipConflictException();
        }
        opportunity.replace(
                nextPartner,
                values(request),
                clock.instant());
        opportunities.flush();
        return OpportunityResponse.from(opportunity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse moveStage(
            UUID actor,
            UUID companyId,
            UUID opportunityId,
            OpportunityStageRequest request) {
        writeOwner(actor, companyId);
        if (request == null) throw invalid();

        Opportunity opportunity = owned(companyId, opportunityId);
        requireVersion(opportunity, request.expectedVersion());
        opportunity.moveStage(request.stage(), clock.instant());
        opportunities.flush();
        return OpportunityResponse.from(opportunity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpportunityResponse changeStatus(
            UUID actor,
            UUID companyId,
            UUID opportunityId,
            OpportunityStatusRequest request) {
        writeOwner(actor, companyId);
        if (request == null) throw invalid();

        Opportunity opportunity = owned(companyId, opportunityId);
        requireVersion(opportunity, request.expectedVersion());
        switch (request.status()) {
            case OPEN -> opportunity.reopen(clock.instant());
            case WON -> opportunity.win(clock.instant());
            case LOST -> opportunity.lose(clock.instant());
        }
        opportunities.flush();
        return OpportunityResponse.from(opportunity);
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        authorization.requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        authorization.requireManage(actor, companyId);
        return company;
    }

    private Opportunity owned(UUID companyId, UUID id) {
        if (id == null) throw invalid();
        return opportunities.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Oportunidade"));
    }

    private BusinessPartner partner(UUID companyId, UUID partnerId) {
        if (partnerId == null) return null;
        return partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Parceiro comercial"));
    }

    private OpportunityValues values(OpportunityRequest request) {
        try {
            return new OpportunityValues(
                    request.title(),
                    request.stage(),
                    new BigDecimal(request.monthlyAmount()),
                    request.probability(),
                    request.responsible(),
                    request.expectedCloseOn(),
                    request.notes());
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static void requireVersion(Opportunity opportunity, long expected) {
        if (opportunity.getVersion() != expected) throw new SalesConflictException();
    }

    private static boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros da oportunidade inválidos.");
    }
}
