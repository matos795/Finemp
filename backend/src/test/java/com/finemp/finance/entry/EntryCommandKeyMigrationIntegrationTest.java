package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EntryCommandKeyMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    @Test void actualJournalMigrationMustBackfillExistingKeysWithoutChangingTheirReceiptsOrAudit() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            // Isolated transactional schema: minimal FK ancestors, actual journal migrations, no operational data.
            var schema="synthetic_entry_keys_"+UUID.randomUUID().toString().replace("-","");
            jdbc.execute("CREATE SCHEMA "+schema);jdbc.execute("SET LOCAL search_path TO "+schema);
            jdbc.execute("CREATE TABLE companies(id UUID PRIMARY KEY)");jdbc.execute("CREATE TABLE user_accounts(id UUID PRIMARY KEY)");
            script("V30__journal_financial_entry_commands.sql");script("V31__allow_entry_settlement_journal_actions.sql");script("V34__allow_financial_entry_deletion_journal.sql");
            var company=UUID.randomUUID();var actor=UUID.randomUUID();var command=UUID.randomUUID();var entry=UUID.randomUUID();var hash="a".repeat(64);
            jdbc.update("INSERT INTO companies(id) VALUES(?)",company);jdbc.update("INSERT INTO user_accounts(id) VALUES(?)",actor);
            jdbc.update("INSERT INTO financial_entry_command_receipts(company_id,actor_id,command_id,fingerprint,entry_id,action,result_version,committed_at) VALUES(?,?,?,?,?,'CREATED',0,CURRENT_TIMESTAMP)",company,actor,command,hash,entry);
            jdbc.update("INSERT INTO financial_entry_events(id,company_id,actor_id,command_id,entry_id,action,before_version,after_version,before_state,after_state,occurred_at) VALUES(?,?,?,?,?,'CREATED',NULL,0,NULL,'{}',CURRENT_TIMESTAMP)",UUID.randomUUID(),company,actor,command,entry);
            script("V36__share_entry_command_keys_and_series_receipts.sql");
            assertThat(jdbc.queryForObject("SELECT fingerprint FROM financial_entry_command_receipts",String.class)).isEqualTo(hash);
            assertThat(jdbc.queryForObject("SELECT entry_id FROM financial_entry_command_receipts",UUID.class)).isEqualTo(entry);
            assertThat(jdbc.queryForObject("SELECT key_kind FROM financial_entry_command_receipts",String.class)).isEqualTo("INDIVIDUAL");
            assertThat(jdbc.queryForObject("SELECT kind FROM financial_entry_command_keys",String.class)).isEqualTo("INDIVIDUAL");
            assertThat(jdbc.queryForObject("SELECT after_state FROM financial_entry_events",String.class)).isEqualTo("{}");
            assertThatThrownBy(()->jdbc.update("INSERT INTO financial_entry_series_command_receipts(company_id,actor_id,command_id,fingerprint,series_id,quantity,result_entries,committed_at) VALUES(?,?,?,?,?,2,'[{},{}]',CURRENT_TIMESTAMP)",company,actor,command,hash,UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }
    void script(String name) {
        jdbc.execute((ConnectionCallback<Void>)connection->{ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/"+name));return null;});
    }
}
