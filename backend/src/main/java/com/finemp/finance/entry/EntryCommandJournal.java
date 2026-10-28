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
public class EntryCommandJournal {
    private final JdbcTemplate jdbc;
    private final EntryCommandKeyRegistry keys;
    private final JsonMapper json=JsonMapper.builder().build();
    public EntryCommandJournal(JdbcTemplate jdbc,EntryCommandKeyRegistry keys) {this.jdbc=jdbc;this.keys=keys;}
    Optional<StoredReceipt> find(UUID company,UUID actor,UUID command) {
        var result=jdbc.query("SELECT fingerprint,entry_id,action,result_version,deleted_entries FROM financial_entry_command_receipts WHERE company_id=? AND actor_id=? AND command_id=?",
                (row,index)->new StoredReceipt(row.getString("fingerprint"),receipt(company,command,row.getObject("entry_id",UUID.class),
                        EntryCommandAction.valueOf(row.getString("action")),row.getLong("result_version"),row.getString("deleted_entries"))),company,actor,command).stream().findFirst();
        if(result.isEmpty() && keys.exists(company,actor,command))throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.IDEMPOTENCY_MISMATCH);
        return result;
    }
    void append(UUID actor,String fingerprint,FinancialEntryCommandReceipt receipt,Long beforeVersion,FinancialEntryValues before,FinancialEntryValues after) {
        keys.claim(receipt.companyId(),actor,receipt.commandId(),EntryCommandKeyRegistry.Kind.INDIVIDUAL);
        appendEvent(actor,receipt,beforeVersion,before,after);
        writeReceipt(actor,fingerprint,receipt);
    }
    void appendDeletion(UUID actor,String fingerprint,FinancialEntryCommandReceipt receipt,List<DeletedState> states) {
        if(receipt.action()!=EntryCommandAction.DELETED || receipt.deletedEntries().size()!=states.size())throw new IllegalArgumentException("Resultado de exclusão inválido.");
        keys.claim(receipt.companyId(),actor,receipt.commandId(),EntryCommandKeyRegistry.Kind.INDIVIDUAL);
        for(int index=0;index<states.size();index++) {
            var state=states.get(index);var result=receipt.deletedEntries().get(index);
            if(!state.entryId().equals(result.entryId()) || state.version()!=result.version() || state.values()==null)throw new IllegalArgumentException("Resultado de exclusão inválido.");
            appendEvent(actor,new FinancialEntryCommandReceipt(receipt.companyId(),receipt.commandId(),state.entryId(),EntryCommandAction.DELETED,state.version()),state.version(),state.values(),null);
        }
        writeReceipt(actor,fingerprint,receipt);
    }
    private void writeReceipt(UUID actor,String fingerprint,FinancialEntryCommandReceipt receipt) {
        var now=Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO financial_entry_command_receipts(company_id,actor_id,command_id,fingerprint,entry_id,action,result_version,committed_at,deleted_entries)
                VALUES(?,?,?,?,?,?,?,?,?)
                """,receipt.companyId(),actor,receipt.commandId(),fingerprint,receipt.entryId(),receipt.action().name(),receipt.version(),now,
                receipt.deletedEntries().isEmpty()?null:json.writeValueAsString(receipt.deletedEntries()));
    }
    void appendEvent(UUID actor,FinancialEntryCommandReceipt receipt,Long beforeVersion,FinancialEntryValues before,FinancialEntryValues after) {
        var now=Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO financial_entry_events(id,company_id,actor_id,command_id,entry_id,action,before_version,after_version,before_state,after_state,occurred_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),receipt.companyId(),actor,receipt.commandId(),receipt.entryId(),receipt.action().name(),beforeVersion,after==null?null:receipt.version(),
                before==null?null:state(before),state(after),now);
    }
    private FinancialEntryCommandReceipt receipt(UUID company,UUID command,UUID entry,EntryCommandAction action,long version,String deleted) {
        if(deleted==null)return new FinancialEntryCommandReceipt(company,command,entry,action,version);
        try {
            var root=json.readTree(deleted);if(!root.isArray() || root.size()<1 || root.size()>120)throw new IllegalArgumentException();
            var items=new ArrayList<FinancialEntryCommandReceipt.DeletedEntry>(root.size());
            for(var node:root) {
                var id=node.get("entryId");var itemVersion=node.get("version");
                if(id==null || !id.isTextual() || itemVersion==null || !itemVersion.isIntegralNumber() || !itemVersion.canConvertToLong())throw new IllegalArgumentException();
                items.add(new FinancialEntryCommandReceipt.DeletedEntry(UUID.fromString(id.asString()),itemVersion.longValue()));
            }
            return new FinancialEntryCommandReceipt(company,command,entry,action,version,items);
        }catch(RuntimeException invalid) {throw new IllegalStateException("Recibo persistido de exclusão inválido.");}
    }
    record DeletedState(UUID entryId,long version,FinancialEntryValues values) { }
    private String state(FinancialEntryValues values) {
        if(values==null)return null;
        // Persist canonical strings; never rely on locale, binary amounts, or date-module defaults.
        var state=new TreeMap<String,String>();
        state.put("schema","FINEMP_ENTRY_STATE_V1");state.put("direction",values.direction().name());state.put("description",values.description());
        state.put("nominalAmount",values.nominalAmount().toPlainString());state.put("documentDate",values.documentDate().toString());
        state.put("entryDate",values.entryDate().toString());state.put("competence",values.competence().toString());state.put("dueDate",values.dueDate().toString());
        state.put("paymentDate",text(values.paymentDate()));state.put("actualPaidAmount",values.actualPaidAmount()==null?null:values.actualPaidAmount().toPlainString());
        state.put("bankAccountId",text(values.bankAccountId()));state.put("categoryId",text(values.categoryId()));state.put("partnerId",text(values.partnerId()));
        state.put("resultCenterId",text(values.resultCenterId()));state.put("costBehaviorOverride",text(values.costBehaviorOverride()));
        state.put("paymentMethod",values.paymentMethod());state.put("documentNumber",values.documentNumber());state.put("notes",values.notes());
        return json.writeValueAsString(state);
    }
    private String text(Object value) {return value==null?null:value.toString();}
    record StoredReceipt(String fingerprint,FinancialEntryCommandReceipt receipt) {
        @Override public String toString() {return "StoredReceipt[redacted]";}
    }
}
