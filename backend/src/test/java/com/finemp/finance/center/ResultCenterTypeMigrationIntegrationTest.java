package com.finemp.finance.center;

import com.finemp.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResultCenterTypeMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("financialEntryCenterReferences") ResultCenterUsageGuard history;
    @Test void actualMigrationMustPreserveUsedParentsAndSeparateCompaniesWithoutRewritingHistory() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            var schema="synthetic_center_type_"+UUID.randomUUID().toString().replace("-","");
            jdbc.execute("CREATE SCHEMA "+schema);jdbc.execute("SET LOCAL search_path TO "+schema);
            jdbc.execute("CREATE TABLE operational_result_centers(id UUID PRIMARY KEY,company_id UUID,parent_id UUID)");
            jdbc.execute("CREATE TABLE financial_entries(id UUID PRIMARY KEY,company_id UUID,result_center_id UUID)");
            var company=UUID.randomUUID();var foreign=UUID.randomUUID();var unused=UUID.randomUUID();var used=UUID.randomUUID();var leaf=UUID.randomUUID();
            jdbc.update("INSERT INTO operational_result_centers VALUES(?,?,NULL),(?,?,NULL),(?,?,?),(?,?,?),(?,?,?)",unused,company,used,company,leaf,company,used,UUID.randomUUID(),company,unused,UUID.randomUUID(),foreign,leaf);
            var entry=UUID.randomUUID();jdbc.update("INSERT INTO financial_entries VALUES(?,?,?)",entry,company,used);
            jdbc.execute((ConnectionCallback<Void>)connection->{ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V38__explicit_result_center_type.sql"));return null;});
            assertThat(jdbc.queryForObject("SELECT type FROM operational_result_centers WHERE id=?",String.class,unused)).isEqualTo("GROUP");
            for(var id:java.util.List.of(used,leaf))assertThat(jdbc.queryForObject("SELECT type FROM operational_result_centers WHERE id=?",String.class,id)).isEqualTo("ANALYTICAL");
            assertThat(jdbc.queryForObject("SELECT result_center_id FROM financial_entries WHERE id=?",UUID.class,entry)).isEqualTo(used);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_entries",Long.class)).isEqualTo(1L);
            assertThat(history.hasEntries(company,used)).isTrue();assertThat(history.hasEntries(foreign,used)).isFalse();
            status.setRollbackOnly();
        });
    }
}
