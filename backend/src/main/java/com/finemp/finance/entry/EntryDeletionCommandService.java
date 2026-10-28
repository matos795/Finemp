package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class EntryDeletionCommandService {
    private final CompanyPermissionAuthorizationService permissions;
    private final CompanyRepository companies;
    private final FinancialEntryRepository entries;
    private final EntryWriteTransactionGuard guard;
    private final EntryCommandJournal journal;
    private final EntryDeletionSelection selection;
    public EntryDeletionCommandService(CompanyPermissionAuthorizationService permissions,CompanyRepository companies,FinancialEntryRepository entries,
            EntryWriteTransactionGuard guard,EntryCommandJournal journal,EntryDeletionSelection selection) {this.permissions=permissions;this.companies=companies;this.entries=entries;this.guard=guard;this.journal=journal;this.selection=selection;}
    public FinancialEntryCommandReceipt delete(UUID actor,UUID company,UUID command,UUID entry,Long expectedVersion) {
        requireManage(actor,company);guard.requireReadCommitted();
        companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);requireManage(actor,company);
        if(command==null || entry==null || expectedVersion==null || expectedVersion<0)throw new IllegalArgumentException("Comando de exclusão inválido.");
        var fingerprint=EntryDeletionFingerprint.of(company,actor,entry,expectedVersion);var replay=journal.find(company,actor,command);
        if(replay.isPresent()) {
            if(!replay.get().fingerprint().equals(fingerprint))throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
            return replay.get().receipt();
        }
        long version=guard.lockEntryVersion(company,entry).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        var row=entries.findByIdAndCompanyId(entry,company).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        if(version!=expectedVersion || row.getVersion()!=version)throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
        var targets=new ArrayList<>(selection.lock(company,entry));
        if(targets.isEmpty() || targets.size()>120)throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
        // Lock order is UUID in SQL; receipt order is root first, then original occurrence position.
        targets.sort(Comparator.comparing((EntryDeletionSelection.Target target)->!target.entryId().equals(entry))
                .thenComparingInt(target->target.number()==null?0:target.number()));
        var rows=new ArrayList<FinancialEntry>(targets.size());var states=new ArrayList<EntryCommandJournal.DeletedState>(targets.size());
        var results=new ArrayList<FinancialEntryCommandReceipt.DeletedEntry>(targets.size());
        for(var target:targets) {
            var current=entries.findByIdAndCompanyId(target.entryId(),company).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
            if(current.getVersion()!=target.version())throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
            rows.add(current);states.add(new EntryCommandJournal.DeletedState(target.entryId(),target.version(),current.getValues()));
            results.add(new FinancialEntryCommandReceipt.DeletedEntry(target.entryId(),target.version()));
        }
        var receipt=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.DELETED,version,results);
        selection.removeMemberships(company,targets);entries.deleteAll(rows);entries.flush();
        journal.appendDeletion(actor,fingerprint,receipt,states);return receipt;
    }
    private void requireManage(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
}
