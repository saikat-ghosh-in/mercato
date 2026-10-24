package com.mercato.Operations;
import com.mercato.Jobs.*;
import com.mercato.Utils.AuthUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/admin/operations/jobs")
public class JobConsole {
    private final JdbcTemplate jdbc;private final DurableJobs jobs;private final AuthUtil auth;
    public JobConsole(JdbcTemplate jdbc,DurableJobs jobs,AuthUtil auth){this.jdbc=jdbc;this.jobs=jobs;this.auth=auth;}
    @GetMapping public Object list(@RequestParam(defaultValue="0")int page){if(page<0||page>10000)throw new IllegalArgumentException("Invalid page");return jdbc.queryForList("SELECT id,job_type,status,attempts,due_at,last_failure,created_at,updated_at FROM durable_jobs WHERE status IN ('WAITING','NEEDS_ATTENTION') ORDER BY due_at LIMIT 50 OFFSET ?",page*50);}
    public record Retry(String reason){}
    @PostMapping("/{id}/retry") public Object retry(@PathVariable UUID id,@RequestBody Retry request){var result=jobs.retry(id,auth.getLoggedInUser().getUserId(),request.reason());return Map.of("id",result.id(),"status",result.status());}
}
