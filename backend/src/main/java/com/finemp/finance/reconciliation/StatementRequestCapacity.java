package com.finemp.finance.reconciliation;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/** Shares a bounded capacity across both memory-intensive file operations. */
@Component
public class StatementRequestCapacity {
    private final Semaphore capacity=new Semaphore(1);
    boolean acquire(){return capacity.tryAcquire();}
    void release(){capacity.release();}
}
