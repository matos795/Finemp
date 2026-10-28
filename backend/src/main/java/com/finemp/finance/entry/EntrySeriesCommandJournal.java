package com.finemp.finance.entry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Repository
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@Transactional(propagation=Propagation.MANDATORY)
public class EntrySeriesCommandJournal {
    private final JdbcTemplate jdbc;
    private final EntryCommandKeyRegistry keys;
    private final EntryCommandJournal audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public EntrySeriesCommandJournal(JdbcTemplate jdbc,EntryCommandKeyRegistry keys,EntryCommandJournal audit) {this.jdbc=jdbc;this.keys=keys;this.audit=audit;}
    Optional<StoredReceipt> find(UUID company,UUID actor,UUID command) {
        var stored=jdbc.query("SELECT fingerprint,series_id,quantity,result_entries FROM financial_entry_series_command_receipts WHERE company_id=? AND actor_id=? AND command_id=?",
                (row,index)->new StoredReceipt(row.getString("fingerprint"),decode(company,command,row.getObject("series_id",UUID.class),row.getInt("quantity"),row.getString("result_entries"))),company,actor,command).stream().findFirst();
        if(stored.isEmpty() && keys.exists(company,actor,command))throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        return stored;
    }
    void append(UUID actor,String fingerprint,EntrySeriesCommandReceipt receipt,List<FinancialEntry> rows) {
        if(rows.size()!=receipt.items().size())throw new IllegalArgumentException("Resultado de série inválido.");
        keys.claim(receipt.companyId(),actor,receipt.commandId(),EntryCommandKeyRegistry.Kind.SERIES_CREATION);
        for(int index=0;index<rows.size();index++) {
            var row=rows.get(index);var item=receipt.items().get(index);
            if(!row.getId().equals(item.entryId()) || row.getVersion()!=item.version() || !row.getCompany().getId().equals(receipt.companyId()))throw new IllegalArgumentException("Resultado de série inválido.");
            // Reuse the official financial event/state mechanism; only command cardinality changes.
            audit.appendEvent(actor,new FinancialEntryCommandReceipt(receipt.companyId(),receipt.commandId(),row.getId(),EntryCommandAction.CREATED,row.getVersion()),null,null,row.getValues());
        }
        jdbc.update("""
                INSERT INTO financial_entry_series_command_receipts(company_id,actor_id,command_id,fingerprint,series_id,quantity,result_entries,committed_at)
                VALUES(?,?,?,?,?,?,?,?)
                """,receipt.companyId(),actor,receipt.commandId(),fingerprint,receipt.seriesId(),receipt.items().size(),json.writeValueAsString(receipt.items()),Timestamp.from(Instant.now()));
    }
    private EntrySeriesCommandReceipt decode(UUID company,UUID command,UUID series,int quantity,String source) {
        try {
            var root=json.readTree(source);if(!root.isArray() || root.size()!=quantity || quantity<2 || quantity>120)throw new IllegalArgumentException();
            var items=new ArrayList<EntrySeriesCommandReceipt.Item>(quantity);
            for(var node:root) {
                var number=node.get("number");var id=node.get("entryId");var version=node.get("version");
                if(number==null || !number.isIntegralNumber() || !number.canConvertToInt() || id==null || !id.isTextual()
                        || version==null || !version.isIntegralNumber() || !version.canConvertToLong())throw new IllegalArgumentException();
                items.add(new EntrySeriesCommandReceipt.Item(number.intValue(),UUID.fromString(id.asString()),version.longValue()));
            }
            return new EntrySeriesCommandReceipt(company,command,series,items);
        }catch(RuntimeException invalid) {throw new IllegalStateException("Recibo persistido de série inválido.");}
    }
    record StoredReceipt(String fingerprint,EntrySeriesCommandReceipt receipt) {@Override public String toString() {return "StoredReceipt[redacted]";}}
}
