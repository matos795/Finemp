package com.finemp.finance.bank;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.access.*;
import com.finemp.company.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class BankAccountServiceTest{
    private final BankAccountRepository banks=mock(BankAccountRepository.class);
    private final CompanyRepository companies=mock(CompanyRepository.class);
    private final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    private final ObjectProvider<BankAccountReferenceGuard> references=mock(ObjectProvider.class);
    private final BankWriteTransactionGuard transactions=mock(BankWriteTransactionGuard.class);
    private final BankAccountService service=new BankAccountService(banks,companies,permissions,references,transactions);
    private final UUID actor=UUID.randomUUID(),companyId=UUID.randomUUID(),bankId=UUID.randomUUID();
    @Test void shouldDenyBeforeResolvingAnyAccountWithoutExplicitFinancialPermission(){
        assertThatThrownBy(()->service.find(actor,companyId,bankId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(banks,companies);
    }
    @Test void shouldScopeReadsToTheAuthorizedCompanyAndNotFallBackToGlobalIdLookup(){
        when(permissions.hasFreshPermission(actor,companyId,CompanyPermission.BANK_ACCOUNT_VIEW)).thenReturn(true);
        when(banks.findByIdAndCompanyId(bankId,companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.find(actor,companyId,bankId)).isInstanceOf(RuntimeException.class);
        verify(banks).findByIdAndCompanyId(bankId,companyId);verify(banks,never()).findById(any());
    }
    @Test void shouldRejectStaleVersionWithoutChangingTheStoredOpeningBalance(){
        var row=authorize();when(banks.findByIdAndCompanyId(bankId,companyId)).thenReturn(Optional.of(row));
        assertThatThrownBy(()->service.update(actor,companyId,bankId,request(1L))).isInstanceOfSatisfying(BankAccountConflictException.class,
                e->assertThat(e.reason()).isEqualTo(BankAccountConflictException.Reason.STALE_VERSION));
        assertThat(row.getOpeningBalance()).isEqualTo(new BigDecimal("1.00"));verify(banks,never()).flush();
    }
    @Test void shouldDeactivateAReferencedAccountEvenWhenItIsTheLastAccount(){
        var row=authorize();var usage=mock(BankAccountReferenceGuard.class);
        when(references.orderedStream()).thenAnswer(call->Stream.of(usage));when(usage.hasReferences(companyId,bankId)).thenReturn(true);
        when(banks.findByIdAndCompanyId(bankId,companyId)).thenReturn(Optional.of(row));
        assertThat(service.delete(actor,companyId,bankId,0).action()).isEqualTo(BankAccountService.DeleteAction.DEACTIVATED);
        assertThat(row.isActive()).isFalse();verify(banks,never()).delete(any(BankAccount.class));verify(banks,never()).countByCompanyId(any());
    }
    @Test void shouldRefuseDeletionOfTheLastUnreferencedRowIncludingInactiveAccounts(){
        var row=authorize();when(references.orderedStream()).thenAnswer(call->Stream.empty());
        when(banks.findByIdAndCompanyId(bankId,companyId)).thenReturn(Optional.of(row));when(banks.countByCompanyId(companyId)).thenReturn(1L);
        assertThatThrownBy(()->service.delete(actor,companyId,bankId,0)).isInstanceOfSatisfying(BankAccountConflictException.class,
                e->assertThat(e.reason()).isEqualTo(BankAccountConflictException.Reason.LAST_ACCOUNT));
        verify(banks,never()).delete(any(BankAccount.class));
    }
    @Test void shouldRecheckPermissionAfterWaitingForTheCompanyLock(){
        authorize();when(permissions.hasFreshPermission(actor,companyId,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.delete(actor,companyId,bankId,0)).isInstanceOf(RuntimeException.class);
        verify(companies).findLockedById(companyId);verifyNoInteractions(banks);
    }
    private BankAccount authorize(){
        when(permissions.hasFreshPermission(actor,companyId,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true);
        var company=new Company("Synthetic","59595959000159");ReflectionTestUtils.setField(company,"id",companyId);
        when(companies.findLockedById(companyId)).thenReturn(Optional.of(company));
        var row=new BankAccount(company,"Synthetic",BankAccountType.CASH,BigDecimal.ONE,LocalDate.of(2026,7,1),true);ReflectionTestUtils.setField(row,"id",bankId);return row;
    }
    private BankAccountRequest request(Long version){return new BankAccountRequest("Changed",BankAccountType.CURRENT,new BigDecimal("2.00"),LocalDate.of(2026,7,1),true,version);}
}
