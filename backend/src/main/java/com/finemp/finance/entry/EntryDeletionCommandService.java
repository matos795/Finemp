package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import java.util.UUID;
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
    public EntryDeletionCommandService(CompanyPermissionAuthorizationService permissions,CompanyRepository companies,FinancialEntryRepository entries,
            EntryWriteTransactionGuard guard,EntryCommandJournal journal) {this.permissions=permissions;this.companies=companies;this.entries=entries;this.guard=guard;this.journal=journal;}
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
        var before=row.getValues();entries.delete(row);entries.flush();
        var receipt=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.DELETED,version);
        journal.append(actor,fingerprint,receipt,version,before,null);return receipt;
    }
    private void requireManage(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
}
