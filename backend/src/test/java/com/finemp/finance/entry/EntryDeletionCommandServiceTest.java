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

class EntryDeletionCommandServiceTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final CompanyRepository companies=mock(CompanyRepository.class);
    final FinancialEntryRepository entries=mock(FinancialEntryRepository.class);
    final EntryWriteTransactionGuard guard=mock(EntryWriteTransactionGuard.class);
    final EntryCommandJournal journal=mock(EntryCommandJournal.class);
    final EntryDeletionSelection selection=mock(EntryDeletionSelection.class);
    final EntryDeletionCommandService service=new EntryDeletionCommandService(permissions,companies,entries,guard,journal,selection);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),key=UUID.randomUUID(),id=UUID.randomUUID();
    @Test void denialAndRevocationWhileWaitingMustStopBeforeReceiptOrFinancialLookup() {
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(OrganizationAccessDeniedException.class);verifyNoInteractions(companies,entries,guard,journal);
        allow();when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(OrganizationAccessDeniedException.class);verifyNoInteractions(entries,journal);
    }
    @Test void paidIndividualEntryCanBeDeletedWithoutRevalidatingCaptureReferences() {
        allow();var row=row();when(guard.lockEntryVersion(company,id)).thenReturn(Optional.of(0L));when(entries.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(row));
        var receipt=service.delete(actor,company,key,id,0L);assertThat(receipt.action()).isEqualTo(EntryCommandAction.DELETED);
        verify(entries).deleteAll(List.of(row));verify(entries).flush();verify(journal).appendDeletion(actor,EntryDeletionFingerprint.of(company,actor,id,0L),receipt,List.of(new EntryCommandJournal.DeletedState(id,0L,row.getValues())));
    }
    @Test void replayMustReturnReceiptBeforeReadingADeletedTarget() {
        allow();var original=new FinancialEntryCommandReceipt(company,key,id,EntryCommandAction.DELETED,0);
        when(journal.find(company,actor,key)).thenReturn(Optional.of(new EntryCommandJournal.StoredReceipt(EntryDeletionFingerprint.of(company,actor,id,0L),original)));
        assertThat(service.delete(actor,company,key,id,0L)).isEqualTo(original);verifyNoInteractions(entries);verify(guard,never()).lockEntryVersion(any(),any());
        verify(guard,never()).requireLinkedStatementPeriodsOpen(any(),any());
    }
    @Test void closedStatementOnRelatedOccurrenceMustBlockAllDeletionsBeforeRemovingMemberships(){
        allow();var root=row();var later=row();UUID laterId=UUID.randomUUID();
        when(guard.lockEntryVersion(company,id)).thenReturn(Optional.of(0L));when(entries.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(root));when(entries.findByIdAndCompanyId(laterId,company)).thenReturn(Optional.of(later));
        when(selection.lock(company,id)).thenReturn(List.of(new EntryDeletionSelection.Target(id,0,null,null,1),new EntryDeletionSelection.Target(laterId,0,null,null,2)));
        doThrow(new com.finemp.finance.period.FinancialPeriodClosedException()).when(guard).requireLinkedStatementPeriodsOpen(company,List.of(id,laterId));
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
        verify(selection,never()).removeMemberships(any(),any());verify(entries,never()).deleteAll(anyList());verify(entries,never()).flush();verify(journal,never()).appendDeletion(any(),any(),any(),any());
    }
    @Test void mismatchedKeyMustConflictBeforeTargetLookup() {
        allow();when(journal.find(company,actor,key)).thenReturn(Optional.of(new EntryCommandJournal.StoredReceipt("different",null)));
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(FinancialEntryCommandConflictException.class).extracting("reason").isEqualTo(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        verifyNoInteractions(entries);verify(guard,never()).lockEntryVersion(any(),any());
    }
    @Test void currentAndManagedVersionsMustMatchExpectedAndMissingTargetsRemainOwned() {
        allow();when(guard.lockEntryVersion(company,id)).thenReturn(Optional.of(1L));when(entries.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(row()));
        assertThatThrownBy(()->service.delete(actor,company,key,id,1L)).isInstanceOf(FinancialEntryCommandConflictException.class).extracting("reason").isEqualTo(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
        verify(entries,never()).deleteAll(anyList());when(guard.lockEntryVersion(company,id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(SaasResourceNotFoundException.class);
    }
    @Test void nullOrNegativeCommandContextMustFailBeforeReceiptLookup() {
        allow();assertThatThrownBy(()->service.delete(actor,company,null,id,0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.delete(actor,company,key,id,-1L)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(entries,journal);
    }
    void allow() {when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);when(companies.findLockedById(company)).thenReturn(Optional.of(mock(Company.class)));when(journal.find(company,actor,key)).thenReturn(Optional.empty());when(selection.lock(company,id)).thenReturn(List.of(new EntryDeletionSelection.Target(id,0,null,null,null)));}
    FinancialEntry row() {var date=LocalDate.of(2026,7,1);return new FinancialEntry(mock(Company.class),new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic paid entry",new BigDecimal("1.23"),date,date,YearMonth.of(2026,7),date,date,null,UUID.randomUUID(),UUID.randomUUID(),null,null,null,"","",""),actor);}
}
