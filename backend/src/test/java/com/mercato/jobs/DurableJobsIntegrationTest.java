package com.mercato.jobs;

import com.mercato.Jobs.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=DurableJobsIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:durable-jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.flyway.enabled=false", "mercato.jobs.enabled=false"})
// These integration tests use real JDBC and no Mockito; avoid its JVM attach dependency.
@TestExecutionListeners(listeners=DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class DurableJobsIntegrationTest {
    @Configuration @EnableAutoConfiguration @EntityScan(basePackageClasses=JobEntity.class)
    @Import({JobStore.class, DurableJobs.class}) static class Config {}
    @Autowired DurableJobs jobs;
    @Autowired JobStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void clear(){jdbc.update("DELETE FROM durable_job_retry_audit");jdbc.update("DELETE FROM durable_jobs");}
    @Test void enqueueRollsBackTogetherWithBusinessTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jobs.enqueue("email", "rollback", Map.of("version",1,"orderId",42));tx.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs",Integer.class));
    }
    @Test void identicalBusinessKeyReconcilesAndChangedPayloadIsRejected() {
        JobRecord first=jobs.enqueue("email","same",Map.of("version",1,"orderId",42));
        assertEquals(first.id(),jobs.enqueue("email","same",Map.of("orderId",42L,"version",1)).id());
        assertThrows(IllegalArgumentException.class,()->jobs.enqueue("email","same",Map.of("version",1,"orderId",43)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs",Integer.class));
    }
    @Test void decimalPayloadValuesRemainExactAndDifferentAmountsDoNotReconcile() {
        JobRecord job=jobs.enqueue("refund","decimal",Map.of("amount",new java.math.BigDecimal("9007199254740993.01")));
        assertEquals(new java.math.BigDecimal("9007199254740993.01"),job.payload().get("amount"));
        assertThrows(IllegalArgumentException.class,()->jobs.enqueue("refund","decimal",Map.of("amount",new java.math.BigDecimal("9007199254740993.02"))));
    }
    @Test void claimsAreBoundedAndCompletionIsFencedAfterLeaseRecovery() {
        for(int i=0;i<12;i++) jobs.enqueue("email","claim-"+i,Map.of("version",1));
        List<JobRecord> first=store.claim("first",100,Duration.ofSeconds(30));
        assertEquals(10,first.size());assertEquals(2,store.claim("second",10,Duration.ofSeconds(30)).size());
        JobRecord stale=first.get(0);
        jdbc.update("UPDATE durable_jobs SET lease_expires_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),stale.id());
        JobRecord recovered=store.claim("recovery",1,Duration.ofSeconds(30)).get(0);
        assertEquals(stale.id(),recovered.id()); assertEquals(2,recovered.attempts());
        assertFalse(store.complete(stale));assertTrue(store.complete(recovered));
        assertEquals(JobStatus.SUCCEEDED,store.get(stale.id()).status());
    }
    @Test void processingRunsOutsideClaimTransaction() {
        AtomicInteger deliveries=new AtomicInteger();
        JobHandler handler=new JobHandler(){public String type(){return "email";} public void handle(JobRecord job){
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());deliveries.incrementAndGet();
        }};
        JobRecord job=jobs.enqueue("email","success",Map.of("version",1));
        new JobProcessor(store,List.of(handler)).process(store.claim("worker",1,Duration.ofSeconds(30)).get(0));
        assertEquals(1,deliveries.get());assertEquals(JobStatus.SUCCEEDED,store.get(job.id()).status());
        assertTrue(store.claim("worker",10,Duration.ofSeconds(30)).isEmpty());
    }
    @Test void invalidVersionAndUnknownHandlerBecomePoisonImmediately() {
        JobRecord malformed=jobs.enqueue("email","bad-version",Map.of("version",99));
        Map<String,Object> nullVersion=new HashMap<>();nullVersion.put("version",null);
        JobRecord nullPayloadVersion=jobs.enqueue("email","null-version",nullVersion);
        JobRecord missing=jobs.enqueue("unregistered","missing-handler",Map.of("version",1));
        JobHandler email=new JobHandler(){public String type(){return "email";}public void handle(JobRecord ignored){}};
        store.claim("worker",10,Duration.ofSeconds(30)).forEach(new JobProcessor(store,List.of(email))::process);
        assertEquals(JobStatus.NEEDS_ATTENTION,store.get(malformed.id()).status());
        assertEquals(JobStatus.NEEDS_ATTENTION,store.get(nullPayloadVersion.id()).status());
        assertEquals(JobStatus.NEEDS_ATTENTION,store.get(missing.id()).status());
    }
    @Test void unknownIdenticalFailureStopsAtThreeAndDoesNotStoreSecrets() {
        JobRecord job=jobs.enqueue("fail","unknown",Map.of("version",1));
        JobProcessor processor=new JobProcessor(store,List.of(new JobHandler(){public String type(){return "fail";}
            public void handle(JobRecord ignored){throw new IllegalStateException("password=very-secret");}}));
        for(int i=0;i<3;i++) {
            processor.process(store.claim("worker",1,Duration.ofSeconds(30)).get(0));
            if(i<2) {assertEquals(JobStatus.WAITING,store.get(job.id()).status());makeDue(job);}
        }
        JobRecord failed=store.get(job.id());assertEquals(JobStatus.NEEDS_ATTENTION,failed.status());
        assertEquals(3,failed.failureCount());assertFalse(failed.lastFailure().contains("very-secret"));
    }
    @Test void transientAndWaitingFailuresStayRetryableAndManualRetryIsAudited() {
        JobRecord job=jobs.enqueue("retry","retry",Map.of("version",1));
        JobHandler handler=new JobHandler(){public String type(){return "retry";}public void handle(JobRecord j){throw new TransientJobException("provider unavailable");}};
        new JobProcessor(store,List.of(handler)).process(store.claim("w",1,Duration.ofSeconds(30)).get(0));
        JobRecord retry=store.get(job.id());assertEquals(JobStatus.WAITING,retry.status());
        assertTrue(retry.dueAt().isAfter(Instant.now().plusSeconds(3)));assertTrue(retry.dueAt().isBefore(Instant.now().plusSeconds(7)));
        assertThrows(IllegalArgumentException.class,()->store.retry(job.id(),"", "reason"));
        store.retry(job.id(),"operator-42","provider balance restored");assertEquals(JobStatus.READY,store.get(job.id()).status());
        assertEquals("operator-42",jdbc.queryForObject("SELECT actor FROM durable_job_retry_audit WHERE job_id=?",String.class,job.id()));
        JobHandler waiting=new JobHandler(){public String type(){return "retry";}public void handle(JobRecord j){throw new WaitingJobException("balance",Duration.ofHours(2));}};
        new JobProcessor(store,List.of(waiting)).process(store.claim("w",1,Duration.ofSeconds(30)).get(0));
        assertTrue(store.get(job.id()).dueAt().isAfter(Instant.now().plusSeconds(7195)));
    }
    @Test void malformedJsonDoesNotPreventOtherJobsFromBeingClaimed() {
        JobRecord broken=jobs.enqueue("email","malformed-json",Map.of("version",1));
        jdbc.update("UPDATE durable_jobs SET payload='{' WHERE id=?",broken.id());
        JobRecord healthy=jobs.enqueue("email","valid-json",Map.of("version",1));
        JobHandler handler=new JobHandler(){public String type(){return "email";}public void handle(JobRecord ignored){}};
        store.claim("w",10,Duration.ofSeconds(30)).forEach(new JobProcessor(store,List.of(handler))::process);
        assertEquals(JobStatus.NEEDS_ATTENTION,store.get(broken.id()).status());
        assertEquals(JobStatus.SUCCEEDED,store.get(healthy.id()).status());
    }
    @Test void manualInterventionStopsImmediatelyAndAuditRetainsStableKey() {
        JobRecord job=jobs.enqueue("manual","stable-key",Map.of("version",1));
        JobHandler handler=new JobHandler(){public String type(){return "manual";}public void handle(JobRecord ignored){throw new ManualInterventionJobException("provider rejected");}};
        new JobProcessor(store,List.of(handler)).process(store.claim("w",1,Duration.ofSeconds(30)).get(0));
        assertEquals(JobStatus.NEEDS_ATTENTION,store.get(job.id()).status());
        JobRecord retried=store.retry(job.id(),"operator","confirmed provider fix");
        assertEquals(job.id(),retried.id());assertEquals("stable-key",retried.businessKey());assertEquals(1,retried.attempts());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM durable_job_retry_audit",Integer.class));
    }
    @Test void staleLeaseCannotRenewOrRecordFailureAndRunningJobsCannotBeManuallyReplayed() {
        JobRecord job=jobs.enqueue("email","fenced",Map.of("version",1));
        JobRecord claimed=store.claim("w",1,Duration.ofSeconds(30)).get(0);
        assertTrue(store.renew(claimed,Duration.ofSeconds(60)));
        assertThrows(IllegalStateException.class,()->store.retry(job.id(),"operator","too early"));
        jdbc.update("UPDATE durable_jobs SET lease_expires_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),job.id());
        assertFalse(store.renew(claimed,Duration.ofSeconds(60)));
        assertFalse(store.fail(claimed,JobStatus.NEEDS_ATTENTION,Instant.now(),"fingerprint",1,"safe failure"));
        assertFalse(store.complete(claimed));assertEquals(JobStatus.RUNNING,store.get(job.id()).status());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM durable_job_retry_audit",Integer.class));
    }
    @Test void retryScheduleGrowsAndCapsAtSixHours() {
        long[] seconds={5,30,120,600,3600,21600,21600};
        JobRecord job=jobs.enqueue("retry","backoff",Map.of("version",1));
        JobHandler handler=new JobHandler(){public String type(){return "retry";}public void handle(JobRecord ignored){throw new TransientJobException("network");}};
        JobProcessor processor=new JobProcessor(store,List.of(handler));
        for(int i=0;i<seconds.length;i++) {
            processor.process(store.claim("w",1,Duration.ofSeconds(30)).get(0));
            long delay=Duration.between(Instant.now(),store.get(job.id()).dueAt()).toMillis();
            assertTrue(delay>=seconds[i]*890, "retry delay too short at attempt "+(i+1));
            assertTrue(delay<=seconds[i]*1110, "retry delay too long at attempt "+(i+1));
            makeDue(job);
        }
    }
    private void makeDue(JobRecord job){jdbc.update("UPDATE durable_jobs SET due_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),job.id());}
}
