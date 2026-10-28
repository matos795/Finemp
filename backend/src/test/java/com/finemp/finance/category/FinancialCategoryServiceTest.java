package com.finemp.finance.category;

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

class FinancialCategoryServiceTest {
    FinancialCategoryRepository categories=mock(FinancialCategoryRepository.class);
    CompanyRepository companies=mock(CompanyRepository.class);
    CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    @SuppressWarnings("unchecked") ObjectProvider<FinancialCategoryUsageGuard> usage=mock(ObjectProvider.class);
    CategoryWriteTransactionGuard transactions=mock(CategoryWriteTransactionGuard.class);
    FinancialCategoryService service=new FinancialCategoryService(categories,companies,permissions,usage,transactions);
    UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID();
    Company owner=mock(Company.class);FinancialCategory row;
    @BeforeEach void prepare() {
        when(owner.getId()).thenReturn(company);
        row=FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Category");ReflectionTestUtils.setField(row,"id",id);
        when(companies.findLockedById(company)).thenReturn(Optional.of(owner));
        when(categories.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(row));
        when(usage.orderedStream()).thenAnswer(call->Stream.empty());
    }
    @Test void mustDenyBeforeLoadingFinancialDataWithoutExplicitPermission() {
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(categories,companies,transactions);
    }
    @Test void mustNeverFallbackToGlobalLookupForAnotherCompany() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_VIEW)).thenReturn(true);
        when(categories.findSnapshot(company,id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(SaasResourceNotFoundException.class);
        verify(categories,never()).findById(any());
    }
    @Test void mustRefuseDeletionWhenAnyChildHasEntriesInsteadOfDeactivatingOrDeletingIt() {
        allowWrite();var guard=mock(FinancialCategoryUsageGuard.class);
        when(usage.orderedStream()).thenAnswer(call->Stream.of(guard));when(guard.hasEntriesInCategoryOrChildren(company,id)).thenReturn(true);
        assertThatThrownBy(()->service.delete(actor,company,id,0))
                .isInstanceOfSatisfying(FinancialCategoryConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialCategoryConflictException.Reason.CATEGORY_IN_USE));
        verify(categories,never()).deleteChildren(any(),any());verify(categories,never()).delete(any());
    }
    @Test void mustRejectStaleUpdateWithoutChangingClassification() {
        allowWrite();ReflectionTestUtils.setField(row,"version",1L);
        assertThatThrownBy(()->service.update(actor,company,id,new FinancialCategoryUpdateRequest("Changed",CashActivity.INVESTING,OperationalDreLine.NAO,CostBehavior.VARIABLE,0L)))
                .isInstanceOfSatisfying(FinancialCategoryConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialCategoryConflictException.Reason.STALE_VERSION));
        assertThat(row.getDreLine()).isEqualTo(OperationalDreLine.DADM);verify(categories,never()).flush();
    }
    @Test void mustRecheckCurrentPermissionAfterWaitingForTheCompanyLock() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.delete(actor,company,id,0)).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(companies).findLockedById(company);verifyNoInteractions(categories);
    }
    private void allowWrite() {when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_MANAGE)).thenReturn(true);}
}
