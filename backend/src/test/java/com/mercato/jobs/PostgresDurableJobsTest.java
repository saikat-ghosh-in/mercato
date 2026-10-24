package com.mercato.jobs;

import com.mercato.Jobs.JobRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authoritative locking coverage: Docker is required; never silently skipped in CI. */
class PostgresDurableJobsTest extends DurableJobsIntegrationTest {
    private static final String database=PostgresTestSupport.database();
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->database);
        registry.add("spring.datasource.username",PostgresTestSupport::username);
        registry.add("spring.datasource.password",PostgresTestSupport::password);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @AfterAll static void cleanup(@Autowired ConfigurableApplicationContext context){context.close();PostgresTestSupport.cleanup();}
    @Test void concurrentWorkersClaimDisjointJobs() throws Exception {
        for(int i=0;i<20;i++) jobs.enqueue("email","concurrent-"+i,Map.of("version",1));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
            Callable<List<JobRecord>> claim=()->{ready.countDown();start.await();return store.claim(UUID.randomUUID().toString(),10,Duration.ofSeconds(30));};
            Future<List<JobRecord>> a=pool.submit(claim),b=pool.submit(claim);
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            List<JobRecord> first=a.get(10,TimeUnit.SECONDS),second=b.get(10,TimeUnit.SECONDS);
            assertEquals(10,first.size());assertEquals(10,second.size());
            Set<UUID> ids=new HashSet<>();first.forEach(job->assertTrue(ids.add(job.id())));second.forEach(job->assertTrue(ids.add(job.id())));
            assertEquals(20,ids.size());
        } finally {pool.shutdownNow();}
    }
    @Test void simultaneousEnqueueOfSameBusinessKeyIsIdempotent() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start=new CountDownLatch(1);
            Callable<JobRecord> enqueue=()->{start.await();return jobs.enqueue("email","parallel-enqueue",Map.of("version",1,"orderId",1));};
            Future<JobRecord> first=pool.submit(enqueue),second=pool.submit(enqueue);start.countDown();
            assertEquals(first.get(10,TimeUnit.SECONDS).id(),second.get(10,TimeUnit.SECONDS).id());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs",Integer.class));
        } finally {pool.shutdownNow();}
    }
}
