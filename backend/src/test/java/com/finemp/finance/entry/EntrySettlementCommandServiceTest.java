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

class EntrySettlementCommandServiceTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final CompanyRepository companies=mock(CompanyRepository.class);
    final FinancialEntryRepository entries=mock(FinancialEntryRepository.class);
    final EntryWriteTransactionGuard transactions=mock(EntryWriteTransactionGuard.class);
    final EntryCommandReferences references=mock(EntryCommandReferences.class);
    final EntryCommandJournal journal=mock(EntryCommandJournal.class);
    final EntrySettlementCommandService service=new EntrySettlementCommandService(permissions,companies,entries,transactions,references,journal);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),command=UUID.randomUUID(),entry=UUID.randomUUID(),bank=UUID.randomUUID();
    final Company owner=mock(Company.class);
    @Test void deniedSettlementAndReversalMustNotTouchFinancialDataOrAcquireLocks() {
        assertThatThrownBy(()->service.settle(actor,company,command,entry,0L,data())).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.reverse(actor,company,command,entry,0L)).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(companies,entries,transactions,references,journal);
    }
    @Test void revocationWhileWaitingForTheCompanyLockMustStopBeforeReceiptLookup() {
        allow();when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.settle(actor,company,command,entry,0L,data())).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(companies).findLockedById(company);verifyNoInteractions(entries,references,journal);
    }
    @Test void settlementMustValidateOnlyTheSelectedBankWithoutReclassifyingHistoricalReferences() {
        allow();var row=row();owned(row,0);
        var receipt=service.settle(actor,company,command,entry,0L,data());
        assertThat(receipt.action()).isEqualTo(EntryCommandAction.SETTLED);assertThat(row.getValues().actualPaidAmount()).isEqualByComparingTo("110.00");
        verify(references).validateBank(company,bank);verifyNoMoreInteractions(references);verify(entries).flush();
        verify(journal).append(eq(actor),anyString(),eq(receipt),eq(0L),any(),eq(row.getValues()));
    }
    @Test void reversalMustPreserveTheBankAndNotRevalidateCatalogsOrNewEntrySettings() {
        allow();var row=row();row.replace(EntrySettlement.settle(row.getValues(),data()),actor);owned(row,0);
        var receipt=service.reverse(actor,company,command,entry,0L);
        assertThat(receipt.action()).isEqualTo(EntryCommandAction.SETTLEMENT_REVERSED);
        assertThat(row.getValues().paymentDate()).isNull();assertThat(row.getValues().actualPaidAmount()).isNull();assertThat(row.getValues().bankAccountId()).isEqualTo(bank);
        verifyNoInteractions(references);
    }
    @Test void replayMustReturnTheOriginalReceiptBeforeConsultingCurrentEntryState() {
        allow();var data=data();var receipt=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.SETTLED,1);
        when(journal.find(company,actor,command)).thenReturn(Optional.of(new EntryCommandJournal.StoredReceipt(
                EntrySettlementFingerprint.settlement(company,actor,entry,0,data),receipt)));
        assertThat(service.settle(actor,company,command,entry,0L,data)).isEqualTo(receipt);
        verifyNoInteractions(entries,references);verify(transactions,never()).lockEntryVersion(any(),any());
        verify(transactions,never()).requireLinkedStatementPeriodsOpen(any(),any());
    }
    @Test void keyMismatchMustFailBeforeLookingUpOrMutatingTheEntry() {
        allow();when(journal.find(company,actor,command)).thenReturn(Optional.of(new EntryCommandJournal.StoredReceipt("different",null)));
        assertThatThrownBy(()->service.reverse(actor,company,command,entry,0L)).isInstanceOf(FinancialEntryCommandConflictException.class)
                .extracting("reason").isEqualTo(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        verifyNoInteractions(entries,references);
    }
    @Test void currentDatabaseVersionAndManagedVersionMustBothMatchTheExpectedVersion() {
        allow();owned(row(),1);
        assertThatThrownBy(()->service.settle(actor,company,command,entry,1L,data())).isInstanceOf(FinancialEntryCommandConflictException.class)
                .extracting("reason").isEqualTo(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
        verifyNoInteractions(references);verify(entries,never()).flush();
    }
    @Test void missingOwnedTargetsMustNotFallBackToAnUnscopedEntityLookup() {
        allow();when(transactions.lockEntryVersion(company,entry)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.reverse(actor,company,command,entry,0L)).isInstanceOf(SaasResourceNotFoundException.class);
        verify(transactions).lockEntryVersion(company,entry);verifyNoInteractions(entries,references);
    }
    void allow() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);
        when(companies.findLockedById(company)).thenReturn(Optional.of(owner));when(journal.find(company,actor,command)).thenReturn(Optional.empty());
    }
    void owned(FinancialEntry row,long currentVersion) {when(transactions.lockEntryVersion(company,entry)).thenReturn(Optional.of(currentVersion));when(entries.findByIdAndCompanyId(entry,company)).thenReturn(Optional.of(row));}
    EntrySettlementData data() {return new EntrySettlementData(LocalDate.of(2026,7,10),new BigDecimal("110.00"),bank);}
    FinancialEntry row() {
        var date=LocalDate.of(2026,7,1);return new FinancialEntry(owner,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic",new BigDecimal("100.00"),date,date,YearMonth.of(2026,7),date,
                null,null,UUID.randomUUID(),UUID.randomUUID(),null,null,null,"","",""),actor);
    }
}
