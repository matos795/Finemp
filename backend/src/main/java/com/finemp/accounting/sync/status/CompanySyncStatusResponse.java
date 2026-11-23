package com.finemp.accounting.sync.status;

import com.finemp.accounting.source.AccountingConnectionMode;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.control.SyncRunResponse;
import java.util.List;
import java.util.UUID;

/**
 * Estado de sincronização das fontes de uma Company. Só fatos registrados em SyncRun:
 * sem execução, os campos de execução ficam nulos. Não traz configuração externa.
 */
public record CompanySyncStatusResponse(List<Source> items) {

    public CompanySyncStatusResponse {
        items = List.copyOf(items);
    }

    /**
     * @param latestRun última execução solicitada, qualquer status
     * @param latestSucceededRun última execução SUCCEEDED: o período efetivamente publicado
     */
    public record Source(UUID accountingSourceId, AccountingSourceType type, AccountingConnectionMode connectionMode,
                         boolean active, SyncRunResponse latestRun, SyncRunResponse latestSucceededRun) { }
}
