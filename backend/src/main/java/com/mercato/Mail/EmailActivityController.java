package com.mercato.Mail;
import com.mercato.Jobs.*;
import com.mercato.Utils.AuthUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

@RestController @RequestMapping("/api/admin/operations/emails")
public class EmailActivityController {
    private final JdbcTemplate jdbc;private final DurableJobs jobs;private final AuthUtil auth;private final boolean reportsEnabled;
    public EmailActivityController(JdbcTemplate jdbc,DurableJobs jobs,AuthUtil auth,@Value("${mercato.email.reports.enabled:false}")boolean reportsEnabled){this.jdbc=jdbc;this.jobs=jobs;this.auth=auth;this.reportsEnabled=reportsEnabled;}
    private static final String PROJECTION="SELECT CAST(id AS varchar) AS id,email_recipient AS recipient,email_purpose AS purpose,"
        +"CASE WHEN email_state IN ('ACCEPTED','SKIPPED','LEGACY_COMPLETED') THEN email_state WHEN status='NEEDS_ATTENTION' THEN 'FAILED' WHEN status='WAITING' THEN 'RETRYING' ELSE COALESCE(email_state,'QUEUED') END AS state,"
        +"status AS job_status,attempts,due_at,email_code AS failure_code,email_smtp_code AS smtp_code,email_accepted_at AS accepted_at,created_at FROM durable_jobs WHERE email_purpose IS NOT NULL "
        +"UNION ALL SELECT 'notice:'||CAST(id AS varchar),email,'COMMISSION_NOTICE',CASE WHEN superseded=true THEN 'SKIPPED' WHEN email_sent_at IS NOT NULL THEN 'ACCEPTED' WHEN email_failure_code IS NOT NULL THEN 'FAILED' WHEN attempts>0 THEN 'RETRYING' ELSE 'QUEUED' END,"
        +"'NOTICE',attempts,next_attempt_at,email_failure_code,NULL,email_sent_at,created_at FROM seller_notices";
    @GetMapping public Object list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="ALL")String state){
        if(page<0||page>10000||!Set.of("ALL","FAILED","PENDING","ACCEPTED").contains(state))throw new IllegalArgumentException("Invalid email activity filter");
        String filter=switch(state){case "FAILED"->" WHERE state='FAILED'";case "PENDING"->" WHERE state IN ('QUEUED','SENDING','RETRYING')";case "ACCEPTED"->" WHERE state='ACCEPTED'";default->"";};
        var rows=jdbc.queryForList("SELECT * FROM ("+PROJECTION+") activity"+filter+" ORDER BY created_at DESC,id DESC LIMIT 30 OFFSET ?",page*30);
        var summary=jdbc.queryForMap("SELECT COUNT(*) AS total,COALESCE(SUM(CASE WHEN state='FAILED' THEN 1 ELSE 0 END),0) AS failed,COALESCE(SUM(CASE WHEN state IN ('QUEUED','SENDING','RETRYING') THEN 1 ELSE 0 END),0) AS pending,MAX(accepted_at) AS last_accepted_at FROM ("+PROJECTION+") activity");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+PROJECTION+") activity"+filter,Long.class);
        return Map.of("items",rows.stream().map(EmailActivityController::stableNames).toList(),"total",total,"page",page,"summary",stableNames(summary),"monthlyReportsEnabled",reportsEnabled);
    }
    /** H2 folds unquoted aliases to upper case; PostgreSQL folds them to lower case. Keep one API contract. */
    private static Map<String,Object> stableNames(Map<String,Object> row){
        var result=new LinkedHashMap<String,Object>();row.forEach((key,value)->result.put(key.toLowerCase(Locale.ROOT),value));return result;
    }
    public record Retry(String reason){}
    @PostMapping("/{id}/retry") public Object retry(@PathVariable UUID id,@RequestBody Retry request){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs WHERE id=? AND email_purpose IS NOT NULL",Long.class,id)==0)throw new IllegalArgumentException("Email not found");
        var result=jobs.retry(id,auth.getLoggedInUser().getUserId(),request.reason());return Map.of("id",result.id(),"status",result.status());
    }
}
