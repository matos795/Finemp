package com.finemp.connector.agent;

import com.finemp.integration.questor.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns transport, cycle and local exclusivity. No resource diagnostic exposes profile/secret values. */
final class ConnectorLocalProfile implements AutoCloseable {
    private final ConnectorProfileLock lock;
    private final ConnectorHttpsTransport transport;
    private final ConnectorAgentCycle cycle;
    private final AtomicBoolean closed=new AtomicBoolean();
    ConnectorLocalProfile(ConnectorProfileLock lock,ConnectorHttpsTransport transport,ConnectorAgentCycle cycle){this.lock=lock;this.transport=transport;this.cycle=cycle;}
    static ConnectorLocalProfile open(ConnectorLocalConfiguration.Profile profile,ConnectorSecretSource secrets,Path directory) {
        validateSecrets(profile,secrets);ConnectorProfileLock lock=null;ConnectorHttpsTransport transport=null;ConnectorAgentCycle cycle=null;
        try {
            lock=ConnectorProfileLock.acquire(directory,profile.sourceId());
            transport=new ConnectorHttpsTransport(profile.origin(),()->token(profile,secrets),Duration.ofSeconds(profile.httpSeconds()));
            var provider=new QuestorConnectionProvider(()->connection(profile,secrets));
            var reader=questorReader(profile,provider);
            cycle=new ConnectorAgentCycle(new FinempConnectorClient(transport,profile.sourceId(),profile.maxRecords()),reader,Duration.ofSeconds(profile.readSeconds()));
            return new ConnectorLocalProfile(lock,transport,cycle);
        }catch(RuntimeException failure){
            if(cycle!=null)cycle.close();if(transport!=null)transport.close();if(lock!=null)lock.close();
            if(failure instanceof ConnectorClientException safe)throw new ConnectorClientException(safe.kind(),safe.status(),safe.retryAfterSeconds());
            throw new ConnectorClientException(ConnectorClientException.Kind.CONFIGURATION);
        }
    }
    static void validateSecrets(ConnectorLocalConfiguration.Profile profile,ConnectorSecretSource secrets){
        if(profile==null||secrets==null)throw invalid();token(profile,secrets);connection(profile,secrets);
    }
    static VerifiedQuestorSnapshotReader questorReader(ConnectorLocalConfiguration.Profile profile,QuestorConnectionProvider provider){
        var identities=new FirebirdQuestorCompanyIdentityReader(provider,profile.identity(),profile.querySeconds());
        var movements=new FirebirdQuestorAccountingReader(provider,profile.maxRecords(),profile.querySeconds());
        return new VerifiedQuestorSnapshotReader(provider,identities,movements);
    }
    private static String token(ConnectorLocalConfiguration.Profile profile,ConnectorSecretSource secrets){
        String value=secret(secrets,profile.credentialEnv(),43);if(!value.matches("[A-Za-z0-9_-]{43}"))throw invalid();return value;
    }
    private static QuestorConnectionProperties connection(ConnectorLocalConfiguration.Profile profile,ConnectorSecretSource secrets){
        var url=secret(secrets,profile.jdbcUrlEnv(),2048);if(!url.startsWith("jdbc:firebirdsql:"))throw invalid();
        return new QuestorConnectionProperties(url,secret(secrets,profile.jdbcUserEnv(),256),secret(secrets,profile.jdbcPasswordEnv(),4096));
    }
    private static String secret(ConnectorSecretSource source,String reference,int limit){
        try {
            String value=source.read(reference);
            if(value==null||value.isBlank()||value.length()>limit||value.chars().anyMatch(c->c<32||c==127))throw invalid();
            return value;
        }catch(RuntimeException unavailable){throw invalid();}
    }
    ConnectorAgentCycle.Outcome runOnce(){return closed.get()?ConnectorAgentCycle.Outcome.STOPPED:cycle.runOnce();}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;cycle.close();transport.close();
        try {if(cycle.awaitStopped(Duration.ZERO)){lock.close();return;}}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        // Keep the OS lock while an uncooperative native reader is still alive. Never replace it with another reader.
        var reaper=new Thread(()->{
            boolean interrupted=false;
            try {while(true){try{if(cycle.awaitStopped(Duration.ofSeconds(30)))break;}catch(InterruptedException stop){interrupted=true;}}}
            finally{lock.close();if(interrupted)Thread.currentThread().interrupt();}
        },"finemp-connector-lock-release");reaper.setDaemon(true);reaper.start();
    }
    private static ConnectorClientException invalid(){return new ConnectorClientException(ConnectorClientException.Kind.CREDENTIAL);}
    @Override public String toString(){return "ConnectorLocalProfile[redacted]";}
}
