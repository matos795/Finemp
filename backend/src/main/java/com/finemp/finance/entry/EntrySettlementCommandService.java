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
public class EntrySettlementCommandService {
    private final CompanyPermissionAuthorizationService permissions;
    private final CompanyRepository companies;
    private final FinancialEntryRepository entries;
    private final EntryWriteTransactionGuard transactions;
    private final EntryCommandReferences references;
    private final EntryCommandJournal journal;
    public EntrySettlementCommandService(CompanyPermissionAuthorizationService permissions,CompanyRepository companies,FinancialEntryRepository entries,
            EntryWriteTransactionGuard transactions,EntryCommandReferences references,EntryCommandJournal journal) {
        this.permissions=permissions;this.companies=companies;this.entries=entries;this.transactions=transactions;this.references=references;this.journal=journal;
    }
    public FinancialEntryCommandReceipt settle(UUID actor,UUID company,UUID command,UUID entry,Long expectedVersion,EntrySettlementData data) {
        return transition(actor,company,command,entry,expectedVersion,data,EntryCommandAction.SETTLED);
    }
    public FinancialEntryCommandReceipt reverse(UUID actor,UUID company,UUID command,UUID entry,Long expectedVersion) {
        return transition(actor,company,command,entry,expectedVersion,null,EntryCommandAction.SETTLEMENT_REVERSED);
    }
    private FinancialEntryCommandReceipt transition(UUID actor,UUID company,UUID command,UUID entry,Long expectedVersion,EntrySettlementData data,EntryCommandAction action) {
        requireManage(actor,company);transactions.requireReadCommitted();
        companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);requireManage(actor,company);
        if(command==null || entry==null || expectedVersion==null || expectedVersion<0 || action==EntryCommandAction.SETTLED && data==null)throw invalid();
        String fingerprint=action==EntryCommandAction.SETTLED?EntrySettlementFingerprint.settlement(company,actor,entry,expectedVersion,data)
                :EntrySettlementFingerprint.reversal(company,actor,entry,expectedVersion);
        var replay=journal.find(company,actor,command);
        if(replay.isPresent()) {
            if(!replay.get().fingerprint().equals(fingerprint))throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
            return replay.get().receipt();
        }
        long currentVersion=transactions.lockEntryVersion(company,entry).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        var row=entries.findByIdAndCompanyId(entry,company).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro"));
        if(currentVersion!=expectedVersion || row.getVersion()!=currentVersion)throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.STALE_VERSION);
        var before=row.getValues();long beforeVersion=row.getVersion();
        // Settlement changes only payment and bank; historical category/party/center are not reclassified.
        if(action==EntryCommandAction.SETTLED)references.validateBank(company,data.bankAccountId());
        var after=action==EntryCommandAction.SETTLED?EntrySettlement.settle(before,data):EntrySettlement.reverse(before);
        row.replace(after,actor);entries.flush();
        var receipt=new FinancialEntryCommandReceipt(company,command,entry,action,row.getVersion());
        journal.append(actor,fingerprint,receipt,beforeVersion,before,row.getValues());return receipt;
    }
    private void requireManage(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Comando de baixa/estorno inválido.");}
}
