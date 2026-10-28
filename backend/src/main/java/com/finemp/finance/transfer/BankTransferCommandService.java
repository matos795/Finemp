package com.finemp.finance.transfer;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.company.*;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.transfers",name="enabled",havingValue="true")
@Transactional(isolation=Isolation.READ_COMMITTED)
public class BankTransferCommandService {
    private final CompanyPermissionAuthorizationService permissions;
    private final CompanyRepository companies;
    private final BankTransferRepository transfers;
    private final BankTransferWriteGuard guard;
    private final BankTransferCommandJournal journal;
    public BankTransferCommandService(CompanyPermissionAuthorizationService permissions,CompanyRepository companies,BankTransferRepository transfers,
            BankTransferWriteGuard guard,BankTransferCommandJournal journal) {
        this.permissions=permissions;this.companies=companies;this.transfers=transfers;this.guard=guard;this.journal=journal;
    }
    public BankTransferCommandReceipt create(UUID actor,UUID company,UUID command,BankTransferValues values) {
        var owner=writeOwner(actor,company);
        if(command==null || values==null || values.amount().signum()<=0)throw invalid();
        var fingerprint=BankTransferCommandFingerprint.create(company,actor,values);
        var replay=replay(company,actor,command,fingerprint);if(replay!=null)return replay;
        guard.validateCreationReferences(company,values);
        var row=transfers.saveAndFlush(new BankTransfer(owner,values,actor));
        var receipt=new BankTransferCommandReceipt(company,command,row.getId(),BankTransferCommandAction.CREATED,row.getVersion());
        journal.append(actor,fingerprint,receipt,null,null,values);return receipt;
    }
    public BankTransferCommandReceipt delete(UUID actor,UUID company,UUID command,UUID transfer,Long expectedVersion) {
        writeOwner(actor,company);
        if(command==null || transfer==null || expectedVersion==null || expectedVersion<0)throw invalid();
        var fingerprint=BankTransferCommandFingerprint.delete(company,actor,transfer,expectedVersion);
        var replay=replay(company,actor,command,fingerprint);if(replay!=null)return replay;
        long version=guard.lockVersion(company,transfer).orElseThrow(()->new SaasResourceNotFoundException("Transferência financeira"));
        var row=transfers.findByIdAndCompanyId(transfer,company).orElseThrow(()->new SaasResourceNotFoundException("Transferência financeira"));
        if(version!=expectedVersion || row.getVersion()!=version)throw new BankTransferCommandConflictException(BankTransferCommandConflictException.Reason.STALE_VERSION);
        var before=row.getValues();transfers.delete(row);transfers.flush();
        var receipt=new BankTransferCommandReceipt(company,command,transfer,BankTransferCommandAction.DELETED,version);
        journal.append(actor,fingerprint,receipt,version,before,null);return receipt;
    }
    private Company writeOwner(UUID actor,UUID company) {
        requireManage(actor,company);guard.requireReadCommitted();
        var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);requireManage(actor,company);return owner;
    }
    private void requireManage(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private BankTransferCommandReceipt replay(UUID company,UUID actor,UUID command,String fingerprint) {
        var stored=journal.find(company,actor,command);if(stored.isEmpty())return null;
        if(!stored.get().fingerprint().equals(fingerprint))throw new BankTransferCommandConflictException(BankTransferCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        return stored.get().receipt();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Comando de transferência inválido.");}
}
