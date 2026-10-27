package com.finemp.accounting.sync.worker;

import org.springframework.scheduling.annotation.Scheduled;

public class SyncWorkerPoller {
    private final BoundedSyncWorker worker;
    public SyncWorkerPoller(BoundedSyncWorker worker) { this.worker = worker; }
    @Scheduled(fixedDelayString = "${finemp.sync.worker.poll-delay-ms:5000}")
    public void poll() { worker.poll(); }
}
