package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import com.finemp.connector.completion.ConnectorWorkResult;
import com.finemp.connector.work.ConnectorWorkResponse;
import com.finemp.integration.questor.QuestorIdentityMapping;
import com.finemp.integration.questor.QuestorConnectionProvider;
import java.sql.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorRuntimeTest {
    @TempDir Path directory;
    @Test void shouldShowHelpAndRejectCredentialArgumentsWithoutReadingAnySecretOrStartingSpring(){
        var source=mock(ConnectorSecretSource.class);var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        assertThat(ConnectorMain.execute(new String[]{"--help"},source,new PrintStream(output),new PrintStream(error))).isZero();
        assertThat(ConnectorMain.execute(new String[]{"--password","synthetic-private"},source,new PrintStream(output),new PrintStream(error))).isEqualTo(2);
        assertThat(error.toString()).doesNotContain("synthetic-private");verifyNoInteractions(source);
    }
    @Test void shouldComposeAnIdleProfileWithoutOpeningJdbcOrHttpAndReleaseItsLockOnClose(){
        var profile=profile();var secrets=secrets();
        try(var runtime=ConnectorRuntime.open(new ConnectorLocalConfiguration(5,List.of(profile)),secrets::get,directory)){
            assertThat(runtime.toString()).doesNotContain("FINEMP_CONNECTOR","jdbc:","password");
            assertThatThrownBy(()->ConnectorProfileLock.acquire(directory,profile.sourceId())).isInstanceOf(ConnectorClientException.class);
        }
        try(var lock=ConnectorProfileLock.acquire(directory,profile.sourceId())){assertThat(lock).isNotNull();}
    }
    @Test void shouldRefuseMissingOrThrownSecretBeforeTakingAnyProfileLockAndDiscardPrivateCause(){
        var profile=profile();
        for(ConnectorSecretSource secrets:List.<ConnectorSecretSource>of(key->null,key->{throw new IllegalStateException("synthetic private store diagnostic");})){
            assertThatThrownBy(()->ConnectorLocalProfile.open(profile,secrets,directory)).hasMessage("Connector client: CREDENTIAL").hasNoCause();
            try(var lock=ConnectorProfileLock.acquire(directory,profile.sourceId())){assertThat(lock).isNotNull();}
        }
    }
    @Test void shouldIsolateAProfileWithUnavailableSecretDuringStartup(){
        var healthy=profile();var unavailable=new ConnectorLocalConfiguration.Profile(UUID.randomUUID(),healthy.origin(),"FINEMP_CONNECTOR_UNAVAILABLE_TOKEN",
                healthy.jdbcUrlEnv(),healthy.jdbcUserEnv(),healthy.jdbcPasswordEnv(),healthy.identity(),25000,120,30,20);
        var secrets=secrets();
        try(var runtime=ConnectorRuntime.open(new ConnectorLocalConfiguration(5,List.of(healthy,unavailable)),secrets::get,directory)){
            assertThat(runtime.readyProfiles()).isEqualTo(1);
            assertThatThrownBy(()->ConnectorProfileLock.acquire(directory,healthy.sourceId())).isInstanceOf(ConnectorClientException.class);
            try(var independent=ConnectorProfileLock.acquire(directory,unavailable.sourceId())){assertThat(independent).isNotNull();}
        }
    }
    @Test void shouldFetchMachineCredentialAgainAndHaltBeforeNetworkWhenItIsNoLongerValid(){
        var profile=profile();var secrets=secrets();
        try(var local=ConnectorLocalProfile.open(profile,secrets::get,directory)){
            secrets.put(profile.credentialEnv(),"revoked");
            assertThat(local.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.HALTED);
        }
    }
    @Test void shouldRetainTheOsLockUntilAnUncooperativeReadActuallyStops() throws Exception {
        var client=mock(FinempConnectorClient.class);var reader=mock(AccountingSourceSnapshotReader.class);var release=new CountDownLatch(1);
        var entered=new CountDownLatch(1);UUID source=UUID.randomUUID();var now=Instant.now();
        var work=new ConnectorWorkResponse(UUID.randomUUID(),UUID.randomUUID(),1,SyncRunMode.INITIAL,now.plusSeconds(300),now,
                new AccountingReadRequest(source,UUID.randomUUID(),AccountingSourceType.QUESTOR,"57","55555555000155",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31)));
        when(client.claim()).thenReturn(Optional.of(work));when(reader.sourceType()).thenReturn(AccountingSourceType.QUESTOR);
        when(reader.readSnapshot(any())).thenAnswer(call->{entered.countDown();while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return new AccountingSourceSnapshot(new ExternalCompanyIdentity("57","55555555000155"),List.of());});
        when(client.fail(work,SyncFailureCode.RESOURCE_LIMIT)).thenReturn(new ConnectorWorkResult(work.attemptId(),work.runId(),1,true,SyncAttemptStatus.FAILED,
                SyncRunStatus.FAILED,work.leaseExpiresAt(),null,SyncFailureCode.RESOURCE_LIMIT,SyncFailureCode.RESOURCE_LIMIT,null,null,null));
        var nanos=new java.util.concurrent.atomic.AtomicLong();var caller=Executors.newSingleThreadExecutor();
        var cycle=new ConnectorAgentCycle(client,reader,Duration.ofMillis(300),nanos::get);
        var local=new ConnectorLocalProfile(ConnectorProfileLock.acquire(directory,source),mock(ConnectorHttpsTransport.class),cycle);
        try{
            var result=caller.submit(local::runOnce);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            nanos.set(Duration.ofMillis(300).toNanos());assertThat(result.get(5,TimeUnit.SECONDS)).isEqualTo(ConnectorAgentCycle.Outcome.FAILED);local.close();
            assertThatThrownBy(()->ConnectorProfileLock.acquire(directory,source)).isInstanceOf(ConnectorClientException.class);
        }finally{release.countDown();local.close();caller.shutdownNow();}
        assertThat(cycle.awaitStopped(Duration.ofSeconds(2))).isTrue();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);ConnectorProfileLock reopened=null;
        while(reopened==null&&System.nanoTime()<deadline){try{reopened=ConnectorProfileLock.acquire(directory,source);}catch(ConnectorClientException waiting){java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));}}
        assertThat(reopened).isNotNull();reopened.close();verify(client,never()).complete(any(),any());
    }
    @Test void shouldComposeTheActualVerifiedReaderAndRefuseMovementSqlWhenObservedJdbcIdentityDiffers() throws Exception {
        for(boolean matches:new boolean[]{false,true}){
            var profile=profile();var provider=mock(QuestorConnectionProvider.class);var connection=mock(Connection.class);
            var metadata=mock(DatabaseMetaData.class);var tables=mock(ResultSet.class);var columns=mock(ResultSet.class);
            var identityStatement=mock(PreparedStatement.class);var movementStatement=mock(PreparedStatement.class);var observed=mock(ResultSet.class);
            when(provider.openConnection()).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getSearchStringEscape()).thenReturn("\\");
            when(metadata.getTables(isNull(),isNull(),anyString(),any(String[].class))).thenReturn(tables);
            when(tables.next()).thenReturn(true,false);when(tables.getString("TABLE_NAME")).thenReturn("SYN_COMPANIES");
            when(metadata.getColumns(isNull(),isNull(),anyString(),eq("%"))).thenReturn(columns);
            when(columns.next()).thenReturn(true,true,false);when(columns.getString("TABLE_NAME")).thenReturn("SYN_COMPANIES");
            when(columns.getString("COLUMN_NAME")).thenReturn("COMPANY_CODE","TAX_ID");when(columns.getInt("DATA_TYPE")).thenReturn(Types.INTEGER,Types.VARCHAR);
            when(columns.getInt("DECIMAL_DIGITS")).thenReturn(0);when(columns.getInt("COLUMN_SIZE")).thenReturn(14);
            when(connection.prepareStatement(anyString())).thenAnswer(call->{String sql=call.getArgument(0);return sql.contains("FROM LCTOCTB")?movementStatement:identityStatement;});
            when(identityStatement.executeQuery()).thenReturn(observed);when(observed.next()).thenReturn(true,false);
            when(observed.getInt("FINEMP_OBS_REF")).thenReturn(matches?57:58);when(observed.getString("FINEMP_OBS_CNPJ")).thenReturn("57575757000157");
            when(movementStatement.executeQuery()).thenReturn(mock(ResultSet.class));
            var request=new AccountingReadRequest(profile.sourceId(),UUID.randomUUID(),AccountingSourceType.QUESTOR,"57","57575757000157",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31));
            var reader=ConnectorLocalProfile.questorReader(profile,provider);
            if(matches){var result=reader.readSnapshot(request);assertThat(result.identity().matches(request)).isTrue();assertThat(result.movements()).isEmpty();verify(movementStatement).executeQuery();}
            else{assertThatThrownBy(()->reader.readSnapshot(request)).isInstanceOf(AccountingIdentityMismatchException.class);verifyNoInteractions(movementStatement);}
            verify(provider).openConnection();verify(connection).setReadOnly(true);verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(connection).rollback();verify(connection).close();verify(connection,never()).commit();
        }
    }
    private ConnectorLocalConfiguration.Profile profile(){return new ConnectorLocalConfiguration.Profile(UUID.randomUUID(),URI.create("https://connector.invalid"),
            "FINEMP_CONNECTOR_TEST_TOKEN","FINEMP_CONNECTOR_TEST_URL","FINEMP_CONNECTOR_TEST_USER","FINEMP_CONNECTOR_TEST_PASSWORD",
            new QuestorIdentityMapping("SYN_COMPANIES","COMPANY_CODE","TAX_ID"),25000,120,30,20);}
    private Map<String,String> secrets(){return new HashMap<>(Map.of("FINEMP_CONNECTOR_TEST_TOKEN","s".repeat(43),"FINEMP_CONNECTOR_TEST_URL","jdbc:firebirdsql:synthetic-not-connected",
            "FINEMP_CONNECTOR_TEST_USER","synthetic-user","FINEMP_CONNECTOR_TEST_PASSWORD","synthetic-password"));}
}
