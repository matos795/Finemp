package com.finemp.sales;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.company.*;
import com.finemp.finance.partner.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;

class OpportunityServiceTest {

    @Test
    void rejectsPartnerReassignmentWhenLinkedFollowUpNamesAnotherPartner() {
        var opportunities = mock(OpportunityRepository.class);
        var companies = mock(CompanyRepository.class);
        var partners = mock(BusinessPartnerRepository.class);
        var activities = mock(SalesActivityRepository.class);
        var authorization = mock(SalesAuthorization.class);
        var clock = Clock.systemUTC();

        var companyId = UUID.randomUUID();
        var opportunityId = UUID.randomUUID();
        var nextPartnerId = UUID.randomUUID();
        var actorId = UUID.randomUUID();

        var company = mock(Company.class);
        var opportunity = mock(Opportunity.class);
        var nextPartner = mock(BusinessPartner.class);

        when(companies.findLockedById(companyId)).thenReturn(Optional.of(company));
        when(opportunities.findByIdAndCompanyId(opportunityId, companyId))
                .thenReturn(Optional.of(opportunity));
        when(opportunity.getVersion()).thenReturn(3L);
        when(partners.findByIdAndCompanyId(nextPartnerId, companyId))
                .thenReturn(Optional.of(nextPartner));
        when(nextPartner.getId()).thenReturn(nextPartnerId);
        when(activities.existsByCompanyIdAndOpportunityIdAndBusinessPartnerIdNot(
                companyId, opportunityId, nextPartnerId))
                .thenReturn(true);

        var service = new OpportunityService(
                opportunities,
                companies,
                partners,
                activities,
                authorization,
                clock);

        var request = new OpportunityRequest(
                nextPartnerId,
                "Projeto ERP",
                "Proposta",
                "1500.00",
                50,
                "Equipe",
                null,
                null,
                3L);

        assertThatThrownBy(() -> service.update(actorId, companyId, opportunityId, request))
                .isInstanceOf(SalesRelationshipConflictException.class);

        verify(opportunity, never()).replace(any(), any(), any());
    }
}
