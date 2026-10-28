package com.finemp.finance.transfer;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankTransferCommandServiceTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final CompanyRepository companies=mock(CompanyRepository.class);
    final BankTransferRepository transfers=mock(BankTransferRepository.class);
    final BankTransferWriteGuard guard=mock(BankTransferWriteGuard.class);
    final BankTransferCommandJournal journal=mock(BankTransferCommandJournal.class);
    final BankTransferCommandService service=new BankTransferCommandService(permissions,companies,transfers,guard,journal);
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),key=UUID.randomUUID(),id=UUID.randomUUID();
    final Company owner=mock(Company.class);
    final BankTransferValues data=new BankTransferValues(UUID.randomUUID(),UUID.randomUUID(),LocalDate.of(2026,7,1),new BigDecimal("1.23"),"Synthetic");

    @Test void deniedCreateAndDeleteMustNotAcquireLocksOrReadFinancialData() {
        assertThatThrownBy(()->service.create(actor,company,key,data)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(companies,transfers,guard,journal);
    }
    @Test void revocationDuringCompanyLockMustStopBeforeReceiptLookup() {
        allow();when(permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true,false);
        assertThatThrownBy(()->service.create(actor,company,key,data)).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(companies).findLockedById(company);verifyNoInteractions(transfers,journal);verify(guard,never()).validateCreationReferences(any(),any());
    }
    @Test void creationMustValidateCurrentBanksAndAppendReceiptWithPersistedState() {
        allow();var stored=mock(BankTransfer.class);when(stored.getId()).thenReturn(id);when(stored.getVersion()).thenReturn(0L);
        when(transfers.saveAndFlush(any())).thenReturn(stored);
        var receipt=service.create(actor,company,key,data);
        assertThat(receipt).isEqualTo(new BankTransferCommandReceipt(company,key,id,BankTransferCommandAction.CREATED,0));
        verify(guard).validateCreationReferences(company,data);
        verify(journal).append(actor,BankTransferCommandFingerprint.create(company,actor,data),receipt,null,null,data);
    }
    @Test void creationRejectsZeroRepresentationWithoutChoosingRawRoundingPolicy() {
        allow();var zero=new BankTransferValues(data.originBankAccountId(),data.destinationBankAccountId(),data.transferDate(),BigDecimal.ZERO,"");
        assertThatThrownBy(()->service.create(actor,company,key,zero)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transfers,journal);verify(guard,never()).validateCreationReferences(any(),any());
    }
    @Test void replayMustPrecedeCurrentBankAndTargetValidation() {
        allow();var original=new BankTransferCommandReceipt(company,key,id,BankTransferCommandAction.CREATED,0);
        when(journal.find(company,actor,key)).thenReturn(Optional.of(new BankTransferCommandJournal.StoredReceipt(BankTransferCommandFingerprint.create(company,actor,data),original)));
        assertThat(service.create(actor,company,key,data)).isEqualTo(original);verifyNoInteractions(transfers);
        verify(guard,never()).validateCreationReferences(any(),any());
    }
    @Test void reusingAKeyForAnotherActionOrContentMustConflictBeforeMutation() {
        allow();when(journal.find(company,actor,key)).thenReturn(Optional.of(new BankTransferCommandJournal.StoredReceipt("different",null)));
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(BankTransferCommandConflictException.class)
                .extracting("reason").isEqualTo(BankTransferCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        verifyNoInteractions(transfers);verify(guard,never()).lockVersion(any(),any());
    }
    @Test void deletionMustPreserveTheBeforeSnapshotAndRequireNoNewCapturePrecondition() {
        allow();var row=new BankTransfer(owner,data,actor);when(guard.lockVersion(company,id)).thenReturn(Optional.of(0L));
        when(transfers.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(row));
        var receipt=service.delete(actor,company,key,id,0L);
        assertThat(receipt.action()).isEqualTo(BankTransferCommandAction.DELETED);assertThat(receipt.version()).isZero();
        verify(transfers).delete(row);verify(transfers).flush();verify(guard,never()).validateCreationReferences(any(),any());
        verify(journal).append(actor,BankTransferCommandFingerprint.delete(company,actor,id,0L),receipt,0L,data,null);
    }
    @Test void databaseAndManagedVersionsMustBothMatchTheExpectedVersion() {
        allow();when(guard.lockVersion(company,id)).thenReturn(Optional.of(1L));
        when(transfers.findByIdAndCompanyId(id,company)).thenReturn(Optional.of(new BankTransfer(owner,data,actor)));
        assertThatThrownBy(()->service.delete(actor,company,key,id,1L)).isInstanceOf(BankTransferCommandConflictException.class)
                .extracting("reason").isEqualTo(BankTransferCommandConflictException.Reason.STALE_VERSION);
        verify(transfers,never()).delete(any());verify(journal,never()).append(any(),any(),any(),any(),any(),any());
    }
    @Test void missingOwnedTargetMustNotFallBackToAnUnscopedLookup() {
        allow();when(guard.lockVersion(company,id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.delete(actor,company,key,id,0L)).isInstanceOf(SaasResourceNotFoundException.class);verifyNoInteractions(transfers);
    }
    void allow() {
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true);
        when(companies.findLockedById(company)).thenReturn(Optional.of(owner));when(journal.find(company,actor,key)).thenReturn(Optional.empty());
    }
}
