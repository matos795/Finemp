package com.finemp.accounting.reading;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.source.AccountingSourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AccountingSourceReaderRegistry {
    private final Map<AccountingSourceType, AccountingSourceReader> readers;
    public AccountingSourceReaderRegistry(List<AccountingSourceReader> available) {
        var registered = new EnumMap<AccountingSourceType, AccountingSourceReader>(AccountingSourceType.class);
        for (var reader : available) {
            if (registered.putIfAbsent(reader.sourceType(), reader) != null)
                throw new IllegalStateException("Mais de um adapter configurado para o mesmo tipo de fonte.");
        }
        readers = Map.copyOf(registered);
    }
    public List<ImportedAccountingMovement> readMovements(AccountingReadRequest request) {
        var reader = readers.get(request.sourceType());
        if (reader == null) throw new AccountingReaderUnavailableException();
        return List.copyOf(reader.readMovements(request));
    }
}
