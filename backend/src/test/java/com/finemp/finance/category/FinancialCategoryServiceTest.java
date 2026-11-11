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
    @Test void structureOnlyMustBeDeniedOnTheFullClassificationUpdateBeforeLockingOrLoading() {
        allowStructure();
        assertThatThrownBy(()->service.update(actor,company,id,new FinancialCategoryUpdateRequest("Changed",CashActivity.INVESTING,OperationalDreLine.NAO,CostBehavior.VARIABLE,0L)))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(companies,categories,transactions);
        assertThat(row.getCashActivity()).isEqualTo(CashActivity.OPERATING);assertThat(row.getDreLine()).isEqualTo(OperationalDreLine.DADM);
    }
    @Test void structureOnlyMayRenameAGroupWithoutTouchingItsClassification() {
        allowStructure();
        row.updateGroup("Synthetic Category",CashActivity.FINANCING,OperationalDreLine.NAO,CostBehavior.VARIABLE);
        var renamed=service.rename(actor,company,id,new FinancialCategoryRenameRequest("  Renamed Synthetic  ",0L));
        assertThat(renamed.name()).isEqualTo("Renamed Synthetic");
        assertThat(row.getCashActivity()).isEqualTo(CashActivity.FINANCING);assertThat(row.getDreLine()).isEqualTo(OperationalDreLine.NAO);
        assertThat(row.getCostBehavior()).isEqualTo(CostBehavior.VARIABLE);
        verify(transactions).requireReadCommitted();verify(categories).flush();
    }
    @Test void renamingASubcategoryMustKeepItsNullOverridesInsteadOfSnapshottingTheGroupDre() {
        allowStructure();var parentId=UUID.randomUUID();
        var sub=FinancialCategory.subcategory(withId(row,parentId),1,"Synthetic Sub");var subId=UUID.randomUUID();ReflectionTestUtils.setField(sub,"id",subId);
        when(categories.findByIdAndCompanyId(subId,company)).thenReturn(Optional.of(sub));
        when(categories.findByIdAndCompanyId(parentId,company)).thenReturn(Optional.of(row));
        var renamed=service.rename(actor,company,subId,new FinancialCategoryRenameRequest("Renamed Sub",0L));
        assertThat(sub.getName()).isEqualTo("Renamed Sub");
        assertThat(sub.getCashActivity()).isNull();assertThat(sub.getDreLine()).isNull();assertThat(sub.getCostBehavior()).isNull();
        assertThat(renamed.dreLine()).isNull();assertThat(renamed.effective().dreLine()).isEqualTo(OperationalDreLine.DADM);
    }
    @Test void renameMustRejectAStaleVersionWithoutChangingTheName() {
        allowStructure();ReflectionTestUtils.setField(row,"version",2L);
        assertThatThrownBy(()->service.rename(actor,company,id,new FinancialCategoryRenameRequest("Changed",1L)))
                .isInstanceOfSatisfying(FinancialCategoryConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialCategoryConflictException.Reason.STALE_VERSION));
        assertThat(row.getName()).isEqualTo("Synthetic Category");verify(categories,never()).flush();
    }
    @Test void renameMustRequireAVersionAndAValidName() {
        allowStructure();
        assertThatThrownBy(()->service.rename(actor,company,id,new FinancialCategoryRenameRequest("Changed",null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.rename(actor,company,id,new FinancialCategoryRenameRequest("   ",0L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.rename(actor,company,id,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(row.getName()).isEqualTo("Synthetic Category");verify(categories,never()).flush();
    }
    @Test void viewOnlyMustBeDeniedOnEveryStructuralCommandBeforeLocking() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_VIEW)).thenReturn(true);
        assertThatThrownBy(()->service.rename(actor,company,id,new FinancialCategoryRenameRequest("Changed",0L))).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.createGroup(actor,company,new FinancialCategoryGroupRequest(FinancialDirection.INCOME,"Denied"))).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.createSubcategory(actor,company,id,new FinancialSubcategoryRequest("Denied"))).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.createStandardPlan(actor,company)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.delete(actor,company,id,0)).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(companies,categories,transactions);
    }
    @Test void structureAndFullManagementMayBothRead() {
        when(categories.findSnapshot(company,id)).thenReturn(Optional.empty());
        allowStructure();
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(SaasResourceNotFoundException.class);
        reset(permissions);allowWrite();
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(SaasResourceNotFoundException.class);
    }
    @Test void structuralPermissionMustAlsoBeRecheckedAfterWaitingForTheCompanyLock() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_STRUCTURE_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.rename(actor,company,id,new FinancialCategoryRenameRequest("Changed",0L))).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(companies).findLockedById(company);verifyNoInteractions(categories);
        assertThat(row.getName()).isEqualTo("Synthetic Category");
    }
    @Test void structuralPermissionMayDeleteAnUnusedCategory() {
        allowStructure();
        service.delete(actor,company,id,0);
        verify(categories).delete(row);
    }
    private void allowWrite() {when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_MANAGE)).thenReturn(true);}
    private void allowStructure() {when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_STRUCTURE_MANAGE)).thenReturn(true);}
    private static FinancialCategory withId(FinancialCategory category,UUID value) {ReflectionTestUtils.setField(category,"id",value);return category;}
}
