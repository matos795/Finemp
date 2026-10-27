package com.finemp.connector.agent;

import com.finemp.accounting.reading.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import com.finemp.connector.completion.ConnectorWorkResult;
import com.finemp.connector.work.ConnectorWorkResponse;
import java.time.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;

/** One opt-in outbound cycle; no web server or implicit background startup. */
public final class ConnectorAgentCycle implements AutoCloseable {
    public enum Outcome { IDLE, BUSY, SUCCEEDED, FAILED, PENDING, RETRY_LATER, HALTED, STOPPED }
    private final FinempConnectorClient client;
    private final AccountingSourceSnapshotReader reader;
    private final long readNanos;
    private final LongSupplier nanoTime;
    private final ExecutorService reads;
    private final ScheduledExecutorService heartbeats;
    private final AtomicBoolean cycling=new AtomicBoolean(), reading=new AtomicBoolean();
    private final AtomicReference<ConnectorClientException> heartbeatFailure=new AtomicReference<>();
    private volatile boolean closed, halted;
    private volatile LeaseKeeper activeLease;
    private Pending pending;
    private boolean backoff;
    private long nextTry;
    public ConnectorAgentCycle(FinempConnectorClient client, AccountingSourceSnapshotReader reader, Duration readBudget) {
        this(client,reader,readBudget,System::nanoTime);
    }
    ConnectorAgentCycle(FinempConnectorClient client, AccountingSourceSnapshotReader reader, Duration readBudget, LongSupplier nanoTime) {
        this.client=Objects.requireNonNull(client); this.reader=Objects.requireNonNull(reader); this.nanoTime=Objects.requireNonNull(nanoTime);
        if(readBudget==null || readBudget.compareTo(Duration.ofMillis(1))<0 || readBudget.compareTo(Duration.ofMinutes(10))>0)
            throw new ConnectorClientException(ConnectorClientException.Kind.CONFIGURATION);
        readNanos=readBudget.toNanos();
        reads=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),threads("finemp-connector-read"));
        var scheduler=new ScheduledThreadPoolExecutor(1,threads("finemp-connector-heartbeat"));
        scheduler.setRemoveOnCancelPolicy(true);heartbeats=scheduler;
    }
    public Outcome runOnce() {
        if(!cycling.compareAndSet(false,true)) return Outcome.BUSY;
        LeaseKeeper keeper=null;
        try {
            if(closed) return Outcome.STOPPED;
            drainHeartbeatPolicy();
            if(halted) return Outcome.HALTED;
            if(backoff && nextTry-nanoTime.getAsLong()>0) return Outcome.RETRY_LATER;
            backoff=false;
            if(reading.get()) return Outcome.BUSY; // Future.cancel is not evidence that native I/O has stopped.
            if(pending!=null) return resolvePending();
            long requested=nanoTime.getAsLong();var claim=client.claim();
            if(claim.isEmpty()) return Outcome.IDLE;
            var work=claim.get();
            if(work.mode()!=SyncRunMode.INITIAL || reader.sourceType()!=work.context().sourceType()) {halted=true;return Outcome.HALTED;}
            keeper=new LeaseKeeper(work,requested); activeLease=keeper;
            if(closed) return Outcome.STOPPED;
            if(!keeper.live()) return lostLease();
            keeper.arm();
            AccountingSourceSnapshot snapshot;
            try {snapshot=read(work,keeper);}
            catch(AccountingIdentityConfigurationException invalidLocalMapping) {halted=true;return Outcome.HALTED;}
            catch(AccountingIdentityMismatchException mismatch) {return report(work,keeper,SyncFailureCode.IDENTITY_MISMATCH);}
            catch(AccountingReadLimitExceededException | ReadBudgetExceeded exhausted) {return report(work,keeper,SyncFailureCode.RESOURCE_LIMIT);}
            catch(CancellationException lostLease) {return lostLease();}
            catch(RuntimeException unavailable) {return report(work,keeper,SyncFailureCode.READ_FAILED);}
            if(!keeper.live()) return lostLease();
            if(snapshot==null || !snapshot.identity().matches(work.context())) return report(work,keeper,SyncFailureCode.IDENTITY_MISMATCH);
            try {return accept(work,client.complete(work,snapshot),keeper.expires.get());}
            catch(ConnectorClientException uncertain) {
                // Any error after starting complete is uncertain. Never turn it into a conflicting fail POST or automatic replay.
                pending=new Pending(work,keeper.expires.get());delay(uncertain);return halted?Outcome.HALTED:Outcome.PENDING;
            }
        } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();return closed?Outcome.STOPPED:Outcome.RETRY_LATER;}
        catch(ConnectorClientException unavailable) {delay(unavailable);return halted?Outcome.HALTED:Outcome.RETRY_LATER;}
        finally {if(keeper!=null)keeper.close();drainHeartbeatPolicy();activeLease=null;cycling.set(false);}
    }
    private AccountingSourceSnapshot read(ConnectorWorkResponse work,LeaseKeeper keeper) throws InterruptedException {
        long deadline=nanoTime.getAsLong()+readNanos;
        var task=new FutureTask<AccountingSourceSnapshot>(() -> reader.readSnapshot(work.context()));
        reading.set(true);keeper.attach(task);
        try {reads.execute(() -> {try{task.run();}finally{reading.set(false);}});}
        catch(RejectedExecutionException stopped) {reading.set(false);task.cancel(true);throw new CancellationException();}
        try {
            while(true) {
                if(closed || !keeper.live()) {task.cancel(true);throw new CancellationException();}
                long remaining=deadline-nanoTime.getAsLong();
                if(remaining<=0) {task.cancel(true);throw new ReadBudgetExceeded();}
                try {
                    var result=task.get(Math.min(remaining,keeper.remaining()),TimeUnit.NANOSECONDS);
                    if(deadline-nanoTime.getAsLong()<=0)throw new ReadBudgetExceeded();
                    return result;
                }
                catch(TimeoutException checkBudgetOrRenewedLease) { /* Recheck both budgets; heartbeat may have extended the lease. */ }
                catch(ExecutionException failed) {
                    if(failed.getCause() instanceof AccountingIdentityConfigurationException) throw new AccountingIdentityConfigurationException();
                    if(failed.getCause() instanceof AccountingIdentityMismatchException) throw new AccountingIdentityMismatchException();
                    if(failed.getCause() instanceof AccountingReadLimitExceededException) throw new AccountingReadLimitExceededException();
                    throw new ReadUnavailable(); // Discard all private driver causes, including errors.
                }
            }
        } finally {if(!task.isDone())task.cancel(true);}
    }
    private Outcome report(ConnectorWorkResponse work,LeaseKeeper keeper,SyncFailureCode code) {
        if(!keeper.live()) return lostLease();
        try {return accept(work,client.fail(work,code),keeper.expires.get());}
        catch(ConnectorClientException uncertain) {pending=new Pending(work,keeper.expires.get());delay(uncertain);return halted?Outcome.HALTED:Outcome.PENDING;}
    }
    private Outcome resolvePending() {
        var unresolved=pending;
        try {return accept(unresolved.work(),client.result(unresolved.work()),unresolved.expires());}
        catch(ConnectorClientException unavailable) {
            if(unavailable.status()==404) pending=null; // No ACK; new claim still depends on server state/fencing.
            throw unavailable;
        }
    }
    private Outcome accept(ConnectorWorkResponse work,ConnectorWorkResult result,long expires) {
        if(result==null || !work.attemptId().equals(result.attemptId()) || !work.runId().equals(result.runId())
                || work.attemptNumber()!=result.attemptNumber()) throw new ConnectorClientException(ConnectorClientException.Kind.PROTOCOL);
        if(result.attemptStatus()==SyncAttemptStatus.SUCCEEDED) {
            if(!result.currentAttempt() || result.runStatus()!=SyncRunStatus.SUCCEEDED || result.readCount()==null
                    || result.importedCount()==null || result.skippedCount()==null || result.readCount()<0 || result.importedCount()<0
                    || result.skippedCount()<0 || result.importedCount()>result.readCount() || result.skippedCount()>result.readCount()-result.importedCount())
                throw new ConnectorClientException(ConnectorClientException.Kind.PROTOCOL);
            pending=null;return Outcome.SUCCEEDED;
        }
        if(result.attemptStatus()!=SyncAttemptStatus.RUNNING || !result.currentAttempt()) {pending=null;return Outcome.FAILED;}
        if(expires-nanoTime.getAsLong()<=0) {pending=null;return Outcome.RETRY_LATER;}
        pending=new Pending(work,expires);return Outcome.PENDING;
    }
    private Outcome lostLease() {
        drainHeartbeatPolicy();return closed?Outcome.STOPPED:halted?Outcome.HALTED:Outcome.RETRY_LATER;
    }
    private void drainHeartbeatPolicy() {var failure=heartbeatFailure.getAndSet(null);if(failure!=null)delay(failure);}
    private void rememberHeartbeatFailure(ConnectorClientException failure) {
        heartbeatFailure.accumulateAndGet(failure,(previous,current)->previous==null?current:
                previous.kind()==ConnectorClientException.Kind.CREDENTIAL?previous:
                current.kind()==ConnectorClientException.Kind.CREDENTIAL?current:
                previous.retryAfterSeconds()>=current.retryAfterSeconds()?previous:current);
    }
    private void delay(ConnectorClientException failure) {
        if(failure.kind()==ConnectorClientException.Kind.CREDENTIAL) {halted=true;return;}
        int seconds=failure.retryAfterSeconds()>0?Math.min(failure.retryAfterSeconds(),300):5;
        long now=nanoTime.getAsLong(), wait=TimeUnit.SECONDS.toNanos(seconds);
        // A concurrent heartbeat failure must never shorten an existing foreground Retry-After.
        if(!backoff||nextTry-now<wait)nextTry=now+wait;
        backoff=true;
    }
    @Override public void close() {
        closed=true;var lease=activeLease;if(lease!=null)lease.close();reads.shutdownNow();heartbeats.shutdownNow();
    }
    boolean awaitStopped(Duration timeout) throws InterruptedException {
        if(timeout==null||timeout.isNegative()||timeout.compareTo(Duration.ofMinutes(1))>0)
            throw new ConnectorClientException(ConnectorClientException.Kind.CONFIGURATION);
        long nanos=timeout.toNanos(), started=System.nanoTime();
        if(!reads.awaitTermination(nanos,TimeUnit.NANOSECONDS))return false;
        return heartbeats.awaitTermination(Math.max(0,nanos-(System.nanoTime()-started)),TimeUnit.NANOSECONDS);
    }
    private static ThreadFactory threads(String name) {return task->{var thread=new Thread(task,name);thread.setDaemon(true);return thread;};}
    private record Pending(ConnectorWorkResponse work,long expires) { }
    private static final class ReadBudgetExceeded extends RuntimeException { }
    private static final class ReadUnavailable extends RuntimeException { }
    private final class LeaseKeeper implements AutoCloseable {
        private final ConnectorWorkResponse work;
        private final AtomicLong expires=new AtomicLong();
        private final AtomicBoolean stopped=new AtomicBoolean(), lost=new AtomicBoolean();
        private final AtomicReference<Future<?>> readTask=new AtomicReference<>();
        private volatile ScheduledFuture<?> scheduled;
        LeaseKeeper(ConnectorWorkResponse work,long requested) {this.work=work;update(work.leaseExpiresAt(),work.serverTime(),requested);}
        private void update(Instant expiry,Instant serverTime,long requested) {
            try {
                long duration=Duration.between(serverTime,expiry).toNanos();
                if(duration<=0 || duration>TimeUnit.DAYS.toNanos(1))throw new IllegalArgumentException();
                expires.set(requested+duration); // Includes claim/heartbeat RTT as consumed time, independent of local wall clock.
            }catch(RuntimeException invalid){throw new ConnectorClientException(ConnectorClientException.Kind.PROTOCOL);}
        }
        long remaining(){return expires.get()-nanoTime.getAsLong();}
        boolean live(){return !closed&&!stopped.get()&&!lost.get()&&remaining()>0;}
        void attach(Future<?> task){readTask.set(task);if(!live())task.cancel(true);}
        void arm(){
            if(!live()){invalidate();return;}
            try{scheduled=heartbeats.schedule(this::renew,Math.max(1,remaining()/3),TimeUnit.NANOSECONDS);}
            catch(RejectedExecutionException shutdown){invalidate();}
        }
        private void renew(){
            if(!live()){invalidate();return;}
            long requested=nanoTime.getAsLong();
            try {
                var lease=client.heartbeat(work);
                if(stopped.get())return;
                if(!work.attemptId().equals(lease.attemptId()))throw new ConnectorClientException(ConnectorClientException.Kind.PROTOCOL);
                update(lease.leaseExpiresAt(),lease.serverTime(),requested);arm();
            }catch(ConnectorClientException unavailable){
                if(unavailable.kind()!=ConnectorClientException.Kind.INTERRUPTED)rememberHeartbeatFailure(unavailable);
                if(!stopped.get())invalidate();
            }catch(RuntimeException unavailable){
                if(!stopped.get()){rememberHeartbeatFailure(new ConnectorClientException(ConnectorClientException.Kind.TRANSPORT));invalidate();}
            }
        }
        private void invalidate(){lost.set(true);var task=readTask.get();if(task!=null)task.cancel(true);}
        @Override public void close(){stopped.set(true);var scheduledNow=scheduled;if(scheduledNow!=null)scheduledNow.cancel(true);invalidate();}
    }
}
