package com.finemp.accounting.sync.worker;

import com.finemp.accounting.sync.execution.*;
import java.time.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "finemp.sync.worker", name = "enabled", havingValue = "true")
public class SyncWorkerConfiguration {
    @Bean SyncCandidateSource syncCandidateSource(JdbcTemplate jdbc, Clock clock) { return new JdbcSyncCandidateSource(jdbc, clock); }
    @Bean(destroyMethod = "close")
    BoundedSyncWorker boundedSyncWorker(SyncCandidateSource candidates, SyncExecutionLifecycle lifecycle, SyncRunExecutor executor,
            SyncRetryPolicy retry, @Value("${finemp.sync.worker.concurrency:2}") int concurrency,
            @Value("${finemp.sync.worker.max-execution-seconds:900}") long maxExecutionSeconds) {
        if (concurrency < 1 || concurrency > 8 || maxExecutionSeconds < retry.leaseDuration().toSeconds() || maxExecutionSeconds > 3600)
            throw new IllegalArgumentException("Invalid sync worker resource configuration.");
        var workers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
                Thread.ofPlatform().daemon(true).name("finemp-sync-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        var pulses = new ScheduledThreadPoolExecutor(concurrency, Thread.ofPlatform().daemon(true).name("finemp-sync-lease-", 0).factory());
        pulses.setRemoveOnCancelPolicy(true);
        return new BoundedSyncWorker(candidates, lifecycle, executor, workers, pulses, concurrency,
                Duration.ofMillis(Math.max(1000, retry.leaseDuration().toMillis() / 3)), Duration.ofSeconds(maxExecutionSeconds), System::nanoTime);
    }
    @Bean SyncWorkerPoller syncWorkerPoller(BoundedSyncWorker worker, @Value("${finemp.sync.worker.poll-delay-ms:5000}") long delay) {
        if (delay < 1000 || delay > 60000) throw new IllegalArgumentException("Invalid sync worker poll interval.");
        return new SyncWorkerPoller(worker);
    }
}
