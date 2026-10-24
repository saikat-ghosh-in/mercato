package com.mercato.Jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Two independently polling workers; bounded claims avoid leased work waiting in an unbounded queue. */
@Component
@ConditionalOnProperty(name="mercato.jobs.enabled",havingValue="true",matchIfMissing=true)
public class JobWorker implements SmartLifecycle {
    private static final Logger log=LoggerFactory.getLogger(JobWorker.class);
    private final JobStore store;
    private final JobProcessor processor;
    private final long pollMillis;
    private final int workers;
    private final Duration lease;
    private volatile boolean running;
    private volatile Instant lastSuccessfulPoll;
    private ScheduledExecutorService execution;
    private ScheduledExecutorService heartbeats;
    private final String owner=UUID.randomUUID().toString();

    public JobWorker(JobStore store,JobProcessor processor,
        @Value("${mercato.jobs.poll-millis:2000}") long pollMillis,
        @Value("${mercato.jobs.workers:2}") int workers,
        @Value("${mercato.jobs.lease-millis:60000}") long leaseMillis) {
        if(pollMillis<1 || workers<1 || workers>32 || leaseMillis<300)
            throw new IllegalArgumentException("Invalid durable job worker configuration");
        this.store=store;this.processor=processor;this.pollMillis=pollMillis;this.workers=workers;this.lease=Duration.ofMillis(leaseMillis);
    }
    @Override public synchronized void start() {
        if(running) return;
        running=true;
        execution=Executors.newScheduledThreadPool(workers,threadFactory("durable-job-worker"));
        heartbeats=Executors.newScheduledThreadPool(Math.min(2,workers),threadFactory("durable-job-lease"));
        for(int i=0;i<workers;i++) {
            String worker=owner+":"+i;
            execution.scheduleWithFixedDelay(()->poll(worker),pollMillis,pollMillis,TimeUnit.MILLISECONDS);
        }
    }
    private void poll(String worker) {
        if(!running) return;
        try {
            List<JobRecord> claimed=store.claim(worker,1,lease);
            lastSuccessfulPoll=Instant.now();
            for(JobRecord job:claimed) {
                ScheduledFuture<?> heartbeat=heartbeats.scheduleWithFixedDelay(()->renew(job),lease.toMillis()/3,lease.toMillis()/3,TimeUnit.MILLISECONDS);
                try {processor.process(job);} finally {heartbeat.cancel(false);}
            }
        } catch(Exception failure) {
            // Never log provider/SQL exception messages or payloads; alerting can use persisted safe categories.
            log.warn("Durable job poll failed ({})",failure.getClass().getSimpleName());
        }
    }
    private void renew(JobRecord job) {
        try {if(!store.renew(job,lease)) log.warn("Durable job lease was lost for {}",job.id());}
        catch(Exception failure){log.warn("Durable job lease renewal failed ({})",failure.getClass().getSimpleName());}
    }
    public Instant lastSuccessfulPoll(){return lastSuccessfulPoll;}
    @Override public synchronized void stop() {
        running=false;
        if(execution!=null) execution.shutdownNow();
        if(heartbeats!=null) heartbeats.shutdownNow();
    }
    @Override public boolean isRunning(){return running;}
    private static ThreadFactory threadFactory(String name) {
        return task->{Thread thread=new Thread(task,name);thread.setDaemon(true);return thread;};
    }
}
