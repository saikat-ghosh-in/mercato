package com.mercato.Operations;

import com.mercato.Jobs.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.*;

/** Outbound monitoring; no unauthenticated health/management endpoint is exposed. */
@Service @ConditionalOnProperty(name="mercato.operations.enabled",havingValue="true")
public class OperationalChecks {
    private final JdbcTemplate jdbc;private final DurableJobs jobs;private final ObjectProvider<JobWorker> worker;
    private final RestTemplate http;private final String heartbeat,recipient;
    public OperationalChecks(JdbcTemplate jdbc,DurableJobs jobs,ObjectProvider<JobWorker> worker,RestTemplate http,@Value("${mercato.operations.heartbeat-url:}")String heartbeat,@Value("${mercato.operations.alert-email:${app.admin.email:}}")String recipient){
        this.jdbc=jdbc;this.jobs=jobs;this.worker=worker;this.http=http;this.heartbeat=heartbeat;this.recipient=recipient;
        if(!heartbeat.isBlank()&&(!heartbeat.startsWith("https://hc-ping.com/")&&!heartbeat.startsWith("https://ping.healthchecks.io/")))throw new IllegalArgumentException("Use a Healthchecks HTTPS ping URL");
        if(recipient.isBlank())throw new IllegalArgumentException("Operations alert email is required");
    }
    @Scheduled(fixedDelay=15000,initialDelay=15000) @Transactional
    public void alerts(){
        var attention=jdbc.queryForList("SELECT id,job_type,failure_fingerprint FROM durable_jobs WHERE status='NEEDS_ATTENTION' AND job_type<>'AUTH_EMAIL' ORDER BY updated_at LIMIT 100");
        for(var row:attention)alert("job:"+row.get("id")+":"+row.get("failure_fingerprint"),"Mercato work needs attention","Durable job "+row.get("id")+" ("+row.get("job_type")+") stopped safely. Inspect the private job console; correct the cause before an audited retry.");
        for(var row:jdbc.queryForList("SELECT refund_id FROM refunds WHERE status<>'SUCCESS' AND failure_reason='WAITING_FOR_FUNDS'"))alert("refund-funds:"+row.get("refund_id"),"Mercato refund waiting for funds","Refund "+row.get("refund_id")+" remains owed to the customer. Replenish merchant funds; the existing refund identity will be reconciled before retry.");
        for(var row:jdbc.queryForList("SELECT o.order_id FROM order_snapshot o JOIN payments p ON p.id=o.payment_fk WHERE p.captured_at<CURRENT_TIMESTAMP-INTERVAL '24 hours' AND ((o.allocation_completed_at IS NULL AND o.automatic_full_refund=false) OR EXISTS(SELECT 1 FROM refunds r WHERE r.payment_fk=p.id AND r.status<>'SUCCESS'))"))alert("capture-24h:"+row.get("order_id"),"Mercato payment unresolved for 24 hours","Order "+row.get("order_id")+" has a verified capture awaiting allocation or refund resolution. Customer obligation remains open.");
    }
    private void alert(String key,String subject,String text){jobs.enqueue("AUTH_EMAIL","ops:"+key,Map.of("to",recipient,"subject",subject,"text",text));}
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void heartbeat(){
        if(heartbeat.isBlank())return;
        try{
            JobWorker current=worker.getIfAvailable();
            if(current==null||current.lastSuccessfulPoll()==null||current.lastSuccessfulPoll().isBefore(Instant.now().minusSeconds(60)))return;
            jdbc.queryForObject("SELECT 1",Integer.class);
            Integer failures=jdbc.queryForObject("SELECT count(*) FROM durable_jobs WHERE status='NEEDS_ATTENTION'",Integer.class);
            Integer funds=jdbc.queryForObject("SELECT count(*) FROM refunds WHERE status<>'SUCCESS' AND failure_reason='WAITING_FOR_FUNDS'",Integer.class);
            http.getForEntity(heartbeat+((failures>0||funds>0)?"/fail":""),String.class);
        }catch(Exception ignored){/* Missing outbound heartbeat is the independent alert. Never log its secret URL. */}
    }
}
