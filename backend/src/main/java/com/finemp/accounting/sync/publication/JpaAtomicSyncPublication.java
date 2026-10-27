package com.finemp.accounting.sync.publication;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.sync.execution.ClaimedSyncRun;
import com.finemp.accounting.sync.execution.SyncPublicationPort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "finemp.sync.executor", name = "enabled", havingValue = "true")
public class JpaAtomicSyncPublication implements SyncPublicationPort {
    private final AccountingPublicationTransaction transaction;
    public JpaAtomicSyncPublication(AccountingPublicationTransaction transaction) { this.transaction = transaction; }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void publish(ClaimedSyncRun claim, List<ImportedAccountingMovement> movements) {
        transaction.publish(claim, movements);
    }
}
