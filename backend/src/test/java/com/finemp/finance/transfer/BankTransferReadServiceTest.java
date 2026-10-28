package com.finemp.finance.transfer;

import com.finemp.access.*;
import com.finemp.administration.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankTransferReadServiceTest {
    final BankTransferReadRepository queries=mock(BankTransferReadRepository.class);
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final BankTransferReadService service=new BankTransferReadService(queries,permissions);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID();
    @Test void deniedReadMustNotLookUpFinancialData() {
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.list(actor,company,0,50)).isInstanceOf(OrganizationAccessDeniedException.class);verifyNoInteractions(queries);
    }
    @Test void missingOwnedRecordMustNotFallBackToAnUnscopedLookup() {
        allow(CompanyPermission.BANK_ACCOUNT_VIEW);when(queries.find(company,id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.find(actor,company,id)).isInstanceOf(SaasResourceNotFoundException.class);verify(queries).find(company,id);
    }
    @Test void eitherExplicitViewOrManageCanReadTheExactProjection() {
        allow(CompanyPermission.BANK_ACCOUNT_MANAGE);var response=row();when(queries.find(company,id)).thenReturn(Optional.of(response));
        assertThat(service.find(actor,company,id)).isEqualTo(response);
    }
    @Test void paginationMustBeBoundedBeforeAnyDatabaseSearch() {
        allow(CompanyPermission.BANK_ACCOUNT_VIEW);
        for(var pair:new int[][]{{-1,50},{0,0},{0,101}})assertThatThrownBy(()->service.list(actor,company,pair[0],pair[1])).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queries);
    }
    @Test void listMustPassOnlyTheAuthorizedCompanyAndServerPageToTheProjectionQuery() {
        allow(CompanyPermission.BANK_ACCOUNT_VIEW);var response=row();var page=PageRequest.of(0,1);
        when(queries.list(company,page)).thenReturn(new PageImpl<>(List.of(response),page,2));
        var result=service.list(actor,company,0,1);assertThat(result.items()).containsExactly(response);assertThat(result.totalItems()).isEqualTo(2);assertThat(result.totalPages()).isEqualTo(2);
        verify(queries).list(company,page);
    }
    void allow(CompanyPermission permission) {when(permissions.hasFreshPermission(actor,company,permission)).thenReturn(true);}
    BankTransferResponse row() {return new BankTransferResponse(id,company,UUID.randomUUID(),"Origin",UUID.randomUUID(),"Destination",LocalDate.of(2026,7,1),new BigDecimal("1.23"),"Synthetic",0);}
}
