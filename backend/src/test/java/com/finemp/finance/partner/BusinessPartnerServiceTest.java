package com.finemp.finance.partner;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessPartnerServiceTest {
    BusinessPartnerRepository partners=mock(BusinessPartnerRepository.class);
    CompanyRepository companies=mock(CompanyRepository.class);
    CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    @SuppressWarnings("unchecked") ObjectProvider<BusinessPartnerReferenceGuard> references=mock(ObjectProvider.class);
    PartnerWriteTransactionGuard transactions=mock(PartnerWriteTransactionGuard.class);
    BusinessPartnerService service=new BusinessPartnerService(partners,companies,permissions,references,transactions);
    UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID();
    Company owner=mock(Company.class);
    BusinessPartner row;
    @BeforeEach void prepare() {
        when(owner.getId()).thenReturn(company);
        row=new BusinessPartner(owner,BusinessPartnerProfileTest.profile("12ABC34501DE35",PartnerPersonType.LEGAL_ENTITY,PartnerRole.BOTH));
        ReflectionTestUtils.setField(row,"id",id);
        when(partners.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(row));
        when(companies.findLockedById(company)).thenReturn(Optional.of(owner));
        when(references.orderedStream()).thenAnswer(call->Stream.empty());
    }
    @Test void mustDenyBeforeLoadingCompanyOrPartnerWithoutExplicitPermission() {
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(companies,partners,transactions);
    }
    @Test void mustScopeLookupWithoutFallingBackToGlobalId() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_VIEW)).thenReturn(true);
        when(partners.findByIdAndCompanyId(id,company)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(SaasResourceNotFoundException.class);
        verify(partners,never()).findById(any());
    }
    @Test void mustPreserveStateOnStaleWriteAndRecheckPermissionAfterLock() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE)).thenReturn(true);
        ReflectionTestUtils.setField(row,"version",1L);
        assertThatThrownBy(()->service.update(actor,company,id,new BusinessPartnerRequest(row.getProfile().withActive(false),0L)))
                .isInstanceOfSatisfying(BusinessPartnerConflictException.class,e->assertThat(e.reason()).isEqualTo(BusinessPartnerConflictException.Reason.STALE_VERSION));
        assertThat(row.getProfile().active()).isTrue();
        verify(permissions,times(2)).hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE);
        verify(partners,never()).flush();
    }
    @Test void mustDeactivateUsedHistoryButDeleteUnusedPartnerWithoutMinimumCount() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE)).thenReturn(true);
        var guard=mock(BusinessPartnerReferenceGuard.class);
        when(guard.hasReferences(company,id)).thenReturn(true);
        when(references.orderedStream()).thenAnswer(call->Stream.of(guard));
        assertThat(service.delete(actor,company,id,0).action()).isEqualTo(BusinessPartnerService.DeleteAction.DEACTIVATED);
        assertThat(row.getProfile().active()).isFalse();verify(partners,never()).delete(any());
        when(references.orderedStream()).thenAnswer(call->Stream.empty());
        assertThat(service.delete(actor,company,id,0).action()).isEqualTo(BusinessPartnerService.DeleteAction.DELETED);
        verify(partners).delete(row);
    }
    @Test void mustStopIfFinancialPermissionIsRevokedWhileWaitingForCompanyLock() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.delete(actor,company,id,0)).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(companies).findLockedById(company);verifyNoInteractions(partners);
    }
    @Test void mustRejectDuplicateDocumentInsideLockedCompanyWithoutEchoingPersonalData() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE)).thenReturn(true);
        when(partners.existsByCompanyIdAndProfileDocument(company,row.getProfile().document())).thenReturn(true);
        assertThatThrownBy(()->service.create(actor,company,new BusinessPartnerRequest(row.getProfile(),null)))
                .isInstanceOfSatisfying(BusinessPartnerConflictException.class,e->assertThat(e.reason()).isEqualTo(BusinessPartnerConflictException.Reason.DUPLICATE_DOCUMENT))
                .hasMessageNotContaining(row.getProfile().document());
        verify(partners,never()).saveAndFlush(any());
    }
}
