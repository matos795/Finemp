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
public class FinancialEntryCommandService {
    private final CompanyPermissionAuthorizationService permissions;
    private final CompanyRepository companies;
    private final FinancialEntryRepository entries;
    private final EntryWriteTransactionGuard transactions;
    private final EntryCommandReferences references;
    private final EntryCommandJournal journal;
    public FinancialEntryCommandService(CompanyPermissionAuthorizationService permissions,CompanyRepository companies,FinancialEntryRepository entries,
            EntryWriteTransactionGuard transactions,EntryCommandReferences references,EntryCommandJournal journal) {
        this.permissions=permissions;this.companies=companies;this.entries=entries;this.transactions=transactions;this.references=references;this.journal=journal;
    }
    public FinancialEntryCommandReceipt create(UUID actor,UUID company,UUID command,FinancialEntryCommandData data) {
        var owner=owner(actor,company);validate(command,data);
        String fingerprint=EntryCommandFingerprint.of(company,actor,EntryCommandAction.CREATED,null,null,data);
        var replay=replay(company,actor,command,fingerprint);if(replay!=null)return replay;
        references.validate(company,data,null);
        transactions.requireEntryPeriodsOpen(company,data.values(null));
        var row=entries.saveAndFlush(new FinancialEntry(owner,data.values(null),actor));
        var receipt=new FinancialEntryCommandReceipt(company,command,row.getId(),EntryCommandAction.CREATED,row.getVersion());
        journal.append(actor,fingerprint,receipt,null,null,row.getValues());return receipt;
    }
    public FinancialEntryCommandReceipt update(UUID actor,UUID company,UUID command,UUID entry,Long expectedVersion,FinancialEntryCommandData data) {
        owner(actor,company);validate(command,data);
        if(entry==null || expectedVersion==null || expectedVersion<0)throw invalid();
        String fingerprint=EntryCommandFingerprint.of(company,actor,EntryCommandAction.UPDATED,entry,expectedVersion,data);
        var replay=replay(company,actor,command,fingerprint);if(replay!=null)return replay;
        // Check the current database version under a row lock, including no-op edits and stale ambient contexts.
        long currentVersion=transactions.lockEntryVersion(company,entry).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        var row=entries.findByIdAndCompanyId(entry,company).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        if(currentVersion!=expectedVersion || row.getVersion()!=currentVersion)throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
        var before=row.getValues();long beforeVersion=row.getVersion();references.validate(company,data,before);
        transactions.requireEntryPeriodsOpen(company,before,data.values(before.actualPaidAmount()));
        transactions.requireLinkedStatementPeriodsOpen(company,java.util.List.of(entry));
        row.replace(data.values(before.actualPaidAmount()),actor);entries.flush();
        var receipt=new FinancialEntryCommandReceipt(company,command,entry,EntryCommandAction.UPDATED,row.getVersion());
        journal.append(actor,fingerprint,receipt,beforeVersion,before,row.getValues());return receipt;
    }
    private Company owner(UUID actor,UUID company) {
        requireManage(actor,company);transactions.requireReadCommitted();
        var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor,company);return owner;
    }
    private void requireManage(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private FinancialEntryCommandReceipt replay(UUID company,UUID actor,UUID command,String fingerprint) {
        var stored=journal.find(company,actor,command);if(stored.isEmpty())return null;
        if(!stored.get().fingerprint().equals(fingerprint))throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        return stored.get().receipt();
    }
    private void validate(UUID command,FinancialEntryCommandData data) {if(command==null || data==null)throw invalid();}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Comando financeiro inválido.");}
}
