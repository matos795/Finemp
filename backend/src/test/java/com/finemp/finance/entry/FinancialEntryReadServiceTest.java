package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.center.ResultCenterRepository;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialEntryReadServiceTest {
    FinancialEntryReadRepository queries=mock(FinancialEntryReadRepository.class);
    CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    ResultCenterRepository centers=mock(ResultCenterRepository.class);
    FinancialEntryReadService service=new FinancialEntryReadService(queries,permissions,centers,"America/Sao_Paulo");
    UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),entry=UUID.randomUUID();
    @Test void denyMustOccurBeforeAnyFinancialDataLookup() {
        assertThatThrownBy(()->service.find(actor,company,entry,LocalDate.of(2026,7,1))).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(queries,centers);
    }
    @Test void recordLookupMustRemainInsideTheAuthorizedCompany() {
        allow();when(queries.find(company,entry)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.find(actor,company,entry,LocalDate.of(2026,7,1))).isInstanceOf(SaasResourceNotFoundException.class);
        verify(queries).find(company,entry);
    }
    @Test void unboundedOrContradictoryQueriesMustBeRejectedBeforeDatabaseSearch() {
        allow();
        assertThatThrownBy(()->service.list(actor,company,FinancialEntryFilter.empty(),0,101)).isInstanceOf(IllegalArgumentException.class);
        var invalid=new FinancialEntryFilter(LocalDate.of(2026,8,1),LocalDate.of(2026,7,1),null,null,null,null,null,null,null,null,null,null);
        assertThatThrownBy(()->service.list(actor,company,invalid,0,50)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queries);
    }
    @Test void anUnknownOrForeignSubtreeMustNotFallbackToAnUnrestrictedCenterFilter() {
        allow();UUID root=UUID.randomUUID();
        var filter=new FinancialEntryFilter(null,null,null,null,null,null,null,root,null,null,null,LocalDate.of(2026,7,1));
        assertThatThrownBy(()->service.list(actor,company,filter,0,50)).isInstanceOf(SaasResourceNotFoundException.class);
        verifyNoInteractions(queries);
    }
    private void allow() {when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW)).thenReturn(true);}
}
