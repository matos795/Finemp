package com.finemp.finance.center;

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

class ResultCenterServiceTest {
    ResultCenterRepository centers=mock(ResultCenterRepository.class);
    FinancialCompanySettingsRepository settings=mock(FinancialCompanySettingsRepository.class);
    CompanyRepository companies=mock(CompanyRepository.class);
    CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    @SuppressWarnings("unchecked") ObjectProvider<ResultCenterUsageGuard> usage=mock(ObjectProvider.class);
    CenterWriteTransactionGuard transactions=mock(CenterWriteTransactionGuard.class);
    ResultCenterService service=new ResultCenterService(centers,settings,companies,permissions,usage,transactions);
    UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID();
    Company owner=mock(Company.class);ResultCenter row;
    @BeforeEach void prepare() {
        when(owner.getId()).thenReturn(company);row=new ResultCenter(owner,"1",null,"Synthetic Center",true);
        ReflectionTestUtils.setField(row,"id",id);
        when(companies.findLockedById(company)).thenReturn(Optional.of(owner));
        when(centers.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(row));
        when(usage.orderedStream()).thenAnswer(call->Stream.empty());
    }
    @Test void mustDenyBeforeReadingAnyCenterDataWithoutExplicitPermission() {
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(centers,settings,companies,transactions);
    }
    @Test void mustRequireTheExactParentInsideTheAuthorizedCompany() {
        allowWrite();
        assertThatThrownBy(()->service.create(actor,company,new ResultCenterCreateRequest("2.1","Synthetic Child")))
                .isInstanceOfSatisfying(ResultCenterConflictException.class,e->assertThat(e.reason()).isEqualTo(ResultCenterConflictException.Reason.PARENT_NOT_FOUND));
        verify(centers).findByCompanyIdAndCode(company,"2");verify(centers,never()).saveAndFlush(any());
    }
    @Test void mustNotTurnACenterWithEntriesIntoAGroup() {
        allowWrite();when(centers.findByCompanyIdAndCode(company,"1")).thenReturn(Optional.of(row));
        var guard=mock(ResultCenterUsageGuard.class);when(guard.hasEntries(company,id)).thenReturn(true);
        when(usage.orderedStream()).thenAnswer(call->Stream.of(guard));
        assertThatThrownBy(()->service.update(actor,company,id,new ResultCenterUpdateRequest("Synthetic Center",true,0L,ResultCenterType.GROUP)))
                .isInstanceOfSatisfying(ResultCenterConflictException.class,e->assertThat(e.reason()).isEqualTo(ResultCenterConflictException.Reason.HAS_ENTRIES));
        assertThat(row.getType()).isEqualTo(ResultCenterType.ANALYTICAL);
        verify(centers,never()).saveAndFlush(any());
    }
    @Test void mustPreserveUsedHistoryBeforeConsideringChildrenAndRefuseDeletingAnUnusedGroup() {
        allowWrite();var guard=mock(ResultCenterUsageGuard.class);
        when(guard.hasReferences(company,id)).thenReturn(true);when(usage.orderedStream()).thenAnswer(call->Stream.of(guard));
        assertThat(service.delete(actor,company,id,0).action()).isEqualTo(ResultCenterService.DeleteAction.DEACTIVATED);
        assertThat(row.isActive()).isFalse();verify(centers,never()).delete(any());
        when(usage.orderedStream()).thenAnswer(call->Stream.empty());when(centers.existsByCompanyIdAndParentId(company,id)).thenReturn(true);
        assertThatThrownBy(()->service.delete(actor,company,id,0))
                .isInstanceOfSatisfying(ResultCenterConflictException.class,e->assertThat(e.reason()).isEqualTo(ResultCenterConflictException.Reason.HAS_CHILDREN));
    }
    @Test void mustRecheckPermissionAfterCompanyLockAndRefuseStaleUpdates() {
        allowWrite();ReflectionTestUtils.setField(row,"version",1L);
        assertThatThrownBy(()->service.update(actor,company,id,new ResultCenterUpdateRequest("Changed",false,0L)))
                .isInstanceOfSatisfying(ResultCenterConflictException.class,e->assertThat(e.reason()).isEqualTo(ResultCenterConflictException.Reason.STALE_VERSION));
        assertThat(row.getName()).isEqualTo("Synthetic Center");verify(permissions,times(2)).hasFreshPermission(actor,company,CompanyPermission.RESULT_CENTER_MANAGE);
    }
    private void allowWrite() {when(permissions.hasFreshPermission(actor,company,CompanyPermission.RESULT_CENTER_MANAGE)).thenReturn(true);}
}
