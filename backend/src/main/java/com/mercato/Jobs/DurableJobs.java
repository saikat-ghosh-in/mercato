package com.mercato.Jobs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Service
public class DurableJobs {
    private final JobStore store;
    public DurableJobs(JobStore store) { this.store = store; }

    /** REQUIRED propagation joins the caller's transaction, including its rollback. */
    @Transactional
    public JobRecord enqueue(String type, String businessKey, Map<String,Object> payload) {
        return store.enqueue(type, businessKey, payload);
    }

    @Transactional
    public JobRecord enqueueAt(String type,String businessKey,Map<String,Object> payload,java.time.Instant dueAt) {
        return store.enqueueAt(type,businessKey,payload,dueAt);
    }

    /** Caller must establish operator authorization; identity and reason are retained atomically. */
    public JobRecord retry(UUID jobId, String actor, String reason) { return store.retry(jobId, actor, reason); }
}
