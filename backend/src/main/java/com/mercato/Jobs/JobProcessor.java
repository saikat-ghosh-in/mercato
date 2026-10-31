package com.mercato.Jobs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class JobProcessor {
    private static final long[] BACKOFF_SECONDS={5,30,120,600,3600,21600};
    private final JobStore store;
    private final Map<String,JobHandler> handlers;
    public JobProcessor(JobStore store,List<JobHandler> handlers) {
        this.store=store; Map<String,JobHandler> registered=new HashMap<>();
        for(JobHandler handler:handlers) if(registered.putIfAbsent(handler.type(),handler)!=null)
            throw new IllegalArgumentException("Duplicate durable job handler type");
        this.handlers=Map.copyOf(registered);
    }

    public void process(JobRecord job) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Job processing must run outside a transaction");
        try {
            Object version=job.payload().get("version");
            if(job.payloadVersion()!=1 || !(version instanceof Number n) || n.doubleValue()!=1.0 || job.payload().containsKey("_invalidPayload"))
                throw new PoisonJobException("Unsupported or malformed payload version");
            JobHandler handler=handlers.get(job.type());
            if(handler==null) throw new PoisonJobException("No registered handler");
            handler.handle(job);
            store.complete(job);
        } catch(Exception failure) {
            String fingerprint=fingerprint(failure);
            int repeated=fingerprint.equals(job.failureFingerprint())?job.failureCount()+1:1;
            boolean known=failure instanceof TransientJobException || failure instanceof WaitingJobException;
            boolean permanent=failure instanceof PoisonJobException || failure instanceof ManualInterventionJobException || (!known && repeated>=3);
            Duration delay=failure instanceof WaitingJobException waiting?waiting.retryAfter():backoff(job.attempts());
            // Persist category and class only: provider messages can contain credentials or personal data.
            String safe=(permanent?"Attention required: ":"Retryable failure: ")+failure.getClass().getSimpleName();
            store.fail(job,permanent?JobStatus.NEEDS_ATTENTION:JobStatus.WAITING,store.currentTime().plus(delay),fingerprint,repeated,safe);
        }
    }

    private Duration backoff(int attempts) {
        long base=BACKOFF_SECONDS[Math.min(BACKOFF_SECONDS.length-1,Math.max(0,attempts-1))];
        return Duration.ofMillis(Math.round(base*1000*ThreadLocalRandom.current().nextDouble(0.9,1.1)));
    }
    private String fingerprint(Exception failure) {
        try {
            String origin=failure.getStackTrace().length==0?"":failure.getStackTrace()[0].toString();
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((failure.getClass().getName()+":"+Objects.toString(failure.getMessage(),"")+":"+origin).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
}
