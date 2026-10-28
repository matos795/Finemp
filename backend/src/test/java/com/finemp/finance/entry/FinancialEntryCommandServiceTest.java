package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import com.finemp.finance.category.FinancialDirection;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialEntryCommandServiceTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final CompanyRepository companies=mock(CompanyRepository.class);
    final FinancialEntryRepository entries=mock(FinancialEntryRepository.class);
    final EntryWriteTransactionGuard transactions=mock(EntryWriteTransactionGuard.class);
    final EntryCommandReferences references=mock(EntryCommandReferences.class);
    final EntryCommandJournal journal=mock(EntryCommandJournal.class);
    final FinancialEntryCommandService service=new FinancialEntryCommandService(permissions,companies,entries,transactions,references,journal);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),command=UUID.randomUUID(),entry=UUID.randomUUID();
    @Test void deniedWritesMustNotTouchDataOrEvenAcquireTheCompanyLock() {
        assertThatThrownBy(()->service.create(actor,company,command,data())).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(companies,entries,transactions,references,journal);
    }
    @Test void permissionMustBeFreshAgainAfterWaitingForTheCompanyLock() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true,false);
        when(companies.findLockedById(company)).thenReturn(Optional.of(mock(Company.class)));
        assertThatThrownBy(()->service.create(actor,company,command,data())).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(companies).findLockedById(company);verifyNoInteractions(entries,references,journal);
    }
    @Test void replayReturnsTheImmutableReceiptWithoutLookingUpOrRevalidatingChangedReferences() {
        allow();var data=data();var receipt=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.CREATED,0);
        when(journal.find(company,actor,command)).thenReturn(Optional.of(new EntryCommandJournal.StoredReceipt(
                EntryCommandFingerprint.of(company,actor,EntryCommandAction.CREATED,null,null,data),receipt)));
        assertThat(service.create(actor,company,command,data)).isEqualTo(receipt);verifyNoInteractions(entries,references);
    }
    @Test void sameKeyForDifferentPayloadMustConflictBeforeAnyMutation() {
        allow();when(journal.find(company,actor,command)).thenReturn(Optional.of(new EntryCommandJournal.StoredReceipt("different",null)));
        assertThatThrownBy(()->service.create(actor,company,command,data())).isInstanceOf(FinancialEntryCommandConflictException.class)
                .extracting("reason").isEqualTo(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        verifyNoInteractions(entries,references);
    }
    @Test void targetLookupMustAlwaysContainTheAuthorizedCompany() {
        allow();when(journal.find(company,actor,command)).thenReturn(Optional.empty());when(entries.findByIdAndCompanyId(entry,company)).thenReturn(Optional.empty());
        when(transactions.lockEntryVersion(company,entry)).thenReturn(Optional.of(0L));
        assertThatThrownBy(()->service.update(actor,company,command,entry,0L,data())).isInstanceOf(SaasResourceNotFoundException.class);
        verify(entries).findByIdAndCompanyId(entry,company);verifyNoInteractions(references);
    }
    @Test void aMissingOwnedRowMustStopBeforeAnyManagedEntityLookup() {
        allow();when(journal.find(company,actor,command)).thenReturn(Optional.empty());
        when(transactions.lockEntryVersion(company,entry)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.update(actor,company,command,entry,0L,data())).isInstanceOf(SaasResourceNotFoundException.class);
        verify(transactions).lockEntryVersion(company,entry);verifyNoInteractions(entries,references);
    }
    private void allow() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);
        when(companies.findLockedById(company)).thenReturn(Optional.of(mock(Company.class)));
    }
    private FinancialEntryCommandData data() {
        var date=LocalDate.of(2026,7,1);
        return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic",new BigDecimal("1.00"),date,date,YearMonth.of(2026,7),date,null,
                UUID.randomUUID(),UUID.randomUUID(),null,null,null,"","","");
    }
}
