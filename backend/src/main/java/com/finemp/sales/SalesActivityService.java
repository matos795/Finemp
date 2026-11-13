package com.finemp.sales;

import com.finemp.administration.*;
import com.finemp.company.*;
import com.finemp.finance.partner.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class SalesActivityService {

    private final SalesActivityRepository activities;
    private final OpportunityRepository opportunities;
    private final CompanyRepository companies;
    private final BusinessPartnerRepository partners;
    private final SalesAuthorization authorization;
    private final Clock clock;

    public SalesActivityService(
            SalesActivityRepository activities,
            OpportunityRepository opportunities,
            CompanyRepository companies,
            BusinessPartnerRepository partners,
            SalesAuthorization authorization,
            Clock clock) {
        this.activities = activities;
        this.opportunities = opportunities;
        this.companies = companies;
        this.partners = partners;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PagedResponse<SalesActivityResponse> list(
            UUID actor,
            UUID companyId,
            String search,
            Boolean completed,
            int page,
            int size) {
        authorization.requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 180) {
            throw invalid();
        }

        LocalDate today = LocalDate.now(clock);
        return PagedResponse.from(
                activities.search(
                        companyId,
                        search == null ? "" : search.trim(),
                        completed,
                        PageRequest.of(page, size,
                                Sort.by(Sort.Order.asc("dueOn"), Sort.Order.asc("id")))),
                activity -> SalesActivityResponse.from(activity, today));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesActivityResponse create(
            UUID actor,
            UUID companyId,
            SalesActivityRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();

        Opportunity opportunity = opportunity(companyId, request.opportunityId());
        BusinessPartner partner = partner(companyId, request.businessPartnerId());
        validateRelationship(opportunity, partner);

        var activity = new SalesActivity(
                company,
                opportunity,
                partner,
                request.subject(),
                request.responsible(),
                request.dueOn(),
                request.notes(),
                clock.instant());

        return SalesActivityResponse.from(
                activities.saveAndFlush(activity),
                LocalDate.now(clock));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesActivityResponse update(
            UUID actor,
            UUID companyId,
            UUID activityId,
            SalesActivityRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        SalesActivity activity = owned(companyId, activityId);
        requireVersion(activity, request.expectedVersion());
        Opportunity opportunity = opportunity(companyId, request.opportunityId());
        BusinessPartner partner = partner(companyId, request.businessPartnerId());
        validateRelationship(opportunity, partner);

        activity.replace(
                opportunity,
                partner,
                request.subject(),
                request.responsible(),
                request.dueOn(),
                request.notes(),
                clock.instant());
        activities.flush();
        return SalesActivityResponse.from(activity, LocalDate.now(clock));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesActivityResponse changeCompletion(
            UUID actor,
            UUID companyId,
            UUID activityId,
            SalesActivityCompletionRequest request) {
        writeOwner(actor, companyId);
        if (request == null) throw invalid();

        SalesActivity activity = owned(companyId, activityId);
        requireVersion(activity, request.expectedVersion());
        if (request.completed()) activity.complete(clock.instant());
        else activity.reopen(clock.instant());
        activities.flush();

        return SalesActivityResponse.from(activity, LocalDate.now(clock));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(
            UUID actor,
            UUID companyId,
            UUID activityId,
            long expectedVersion) {
        writeOwner(actor, companyId);
        SalesActivity activity = owned(companyId, activityId);
        requireVersion(activity, expectedVersion);
        activities.delete(activity);
        activities.flush();
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        authorization.requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        authorization.requireManage(actor, companyId);
        return company;
    }

    private SalesActivity owned(UUID companyId, UUID id) {
        if (id == null) throw invalid();
        return activities.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Atividade comercial"));
    }

    private Opportunity opportunity(UUID companyId, UUID id) {
        if (id == null) return null;
        return opportunities.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Oportunidade"));
    }

    private BusinessPartner partner(UUID companyId, UUID id) {
        if (id == null) return null;
        return partners.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Parceiro comercial"));
    }

    private static void validateRelationship(
            Opportunity opportunity,
            BusinessPartner partner) {
        if (opportunity == null || partner == null || opportunity.getBusinessPartner() == null) return;
        if (!opportunity.getBusinessPartner().getId().equals(partner.getId())) {
            throw invalid();
        }
    }

    private static void requireVersion(SalesActivity activity, long expected) {
        if (expected < 0 || activity.getVersion() != expected) throw new SalesConflictException();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros da atividade comercial inválidos.");
    }
}
