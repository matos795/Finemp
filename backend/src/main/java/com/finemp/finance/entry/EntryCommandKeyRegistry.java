package com.finemp.finance.entry;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Database-enforced command identity shared by individual and series receipts. Not another audit trail. */
@Repository
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@Transactional(propagation=Propagation.MANDATORY)
public class EntryCommandKeyRegistry {
    public enum Kind { INDIVIDUAL, SERIES_CREATION }
    private final JdbcTemplate jdbc;
    public EntryCommandKeyRegistry(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    boolean exists(UUID company,UUID actor,UUID command) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_entry_command_keys WHERE company_id=? AND actor_id=? AND command_id=?)",Boolean.class,company,actor,command));
    }
    void claim(UUID company,UUID actor,UUID command,Kind kind) {
        jdbc.update("INSERT INTO financial_entry_command_keys(company_id,actor_id,command_id,kind) VALUES(?,?,?,?)",company,actor,command,kind.name());
    }
}
