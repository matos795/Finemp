package com.finemp.finance.transfer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Repository
@ConditionalOnProperty(prefix="finemp.finance.transfers",name="enabled",havingValue="true")
@Transactional(propagation=Propagation.MANDATORY)
public class BankTransferCommandJournal {
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    public BankTransferCommandJournal(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    Optional<StoredReceipt> find(UUID company,UUID actor,UUID command) {
        return jdbc.query("SELECT fingerprint,transfer_id,action,result_version FROM financial_bank_transfer_command_receipts WHERE company_id=? AND actor_id=? AND command_id=?",
                (row,index)->new StoredReceipt(row.getString("fingerprint"),new BankTransferCommandReceipt(company,command,row.getObject("transfer_id",UUID.class),
                        BankTransferCommandAction.valueOf(row.getString("action")),row.getLong("result_version"))),company,actor,command).stream().findFirst();
    }
    void append(UUID actor,String fingerprint,BankTransferCommandReceipt receipt,Long beforeVersion,BankTransferValues before,BankTransferValues after) {
        var now=Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO financial_bank_transfer_events(id,company_id,actor_id,command_id,transfer_id,action,before_version,after_version,before_state,after_state,occurred_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),receipt.companyId(),actor,receipt.commandId(),receipt.transferId(),receipt.action().name(),beforeVersion,
                after==null?null:receipt.version(),state(before),state(after),now);
        jdbc.update("""
                INSERT INTO financial_bank_transfer_command_receipts(company_id,actor_id,command_id,fingerprint,transfer_id,action,result_version,committed_at)
                VALUES(?,?,?,?,?,?,?,?)
                """,receipt.companyId(),actor,receipt.commandId(),fingerprint,receipt.transferId(),receipt.action().name(),receipt.version(),now);
    }
    private String state(BankTransferValues values) {
        if(values==null)return null;
        var state=new TreeMap<String,String>();state.put("schema","FINEMP_BANK_TRANSFER_STATE_V1");
        state.put("originBankAccountId",values.originBankAccountId().toString());state.put("destinationBankAccountId",values.destinationBankAccountId().toString());
        state.put("transferDate",values.transferDate().toString());state.put("amount",values.amount().toPlainString());state.put("description",values.description());
        return json.writeValueAsString(state);
    }
    record StoredReceipt(String fingerprint,BankTransferCommandReceipt receipt) {
        @Override public String toString() {return "StoredReceipt[redacted]";}
    }
}
