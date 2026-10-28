package com.finemp.connector.agent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit local runtime. One scheduled cycle per profile, with no retry queue or cross-profile failure propagation. */
final class ConnectorRuntime implements AutoCloseable {
    private final List<ConnectorLocalProfile> profiles;
    private final ScheduledThreadPoolExecutor scheduler;
    private final CountDownLatch finished;
    private final int pollSeconds;
    private final AtomicBoolean started=new AtomicBoolean(),closed=new AtomicBoolean();
    private ConnectorRuntime(List<ConnectorLocalProfile> profiles,int pollSeconds){
        this.profiles=List.copyOf(profiles);this.pollSeconds=pollSeconds;finished=new CountDownLatch(profiles.size());
        scheduler=new ScheduledThreadPoolExecutor(Math.max(1,profiles.size()),task->{var thread=new Thread(task,"finemp-connector-poll");thread.setDaemon(true);return thread;});
        scheduler.setRemoveOnCancelPolicy(true);
    }
    static ConnectorRuntime open(ConnectorLocalConfiguration config,ConnectorSecretSource secrets,Path locks){
        var opened=new ArrayList<ConnectorLocalProfile>();
        if(config==null)throw new ConnectorClientException(ConnectorClientException.Kind.CONFIGURATION);
        for(var profile:config.profiles()){
            try{opened.add(ConnectorLocalProfile.open(profile,secrets,locks));}
            catch(RuntimeException unavailable){/* Halt only this local profile; values and private causes are never logged. */}
        }
        return new ConnectorRuntime(opened,config.pollSeconds());
    }
    void start(){if(!started.compareAndSet(false,true)||closed.get())return;for(var profile:profiles)schedule(profile,0);}
    private void schedule(ConnectorLocalProfile profile,int seconds){
        if(closed.get())return;
        try{scheduler.schedule(()->poll(profile),seconds,TimeUnit.SECONDS);}catch(RejectedExecutionException stopped){/* Closing owns cleanup. */}
    }
    private void poll(ConnectorLocalProfile profile){
        if(closed.get())return;
        try{
            var result=profile.runOnce();
            if(result==ConnectorAgentCycle.Outcome.HALTED||result==ConnectorAgentCycle.Outcome.STOPPED){profile.close();finished.countDown();return;}
        }catch(RuntimeException unavailable){profile.close();finished.countDown();return;}
        schedule(profile,pollSeconds); // Delay after completion, not fixed-rate accumulation.
    }
    void await() throws InterruptedException{finished.await();}
    int readyProfiles(){return profiles.size();}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;scheduler.shutdownNow();profiles.forEach(ConnectorLocalProfile::close);while(finished.getCount()>0)finished.countDown();}
    @Override public String toString(){return "ConnectorRuntime[profiles="+profiles.size()+"]";}
}
