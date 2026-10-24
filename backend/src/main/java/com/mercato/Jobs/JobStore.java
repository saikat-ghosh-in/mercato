package com.mercato.Jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

// JdbcTemplate already translates SQL errors. Do not translate deliberate domain input rejections.
@Component
public class JobStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JobStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc=jdbc;
        // Preserve exact decimal payloads and exact duplicate comparisons; never round through double.
        this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public JobRecord enqueue(String type, String businessKey, Map<String,Object> payload) {
        requireText(type,120,"type"); requireText(businessKey,255,"business key");
        if(payload==null) throw new IllegalArgumentException("Payload is required");
        Map<String,Object> versioned=new LinkedHashMap<>(payload);
        if(!versioned.containsKey("version")) versioned.put("version",1);
        String encoded=encode(versioned);
        Object version=versioned.get("version");
        int payloadVersion=version instanceof Number n && n.doubleValue()==n.intValue() ? n.intValue() : 0;
        Instant now=now();
        jdbc.update("INSERT INTO durable_jobs (id,job_type,business_key,payload,payload_version,status,attempts,due_at,failure_count,created_at,updated_at) "
            +"VALUES (?,?,?,?,?,'READY',0,?,0,?,?) ON CONFLICT DO NOTHING",
            UUID.randomUUID(),type,businessKey,encoded,payloadVersion,ts(now),ts(now),ts(now));
        JobRecord result=jdbc.queryForObject("SELECT * FROM durable_jobs WHERE business_key=?",this::map,businessKey);
        // Compare parsed JSON so object-key ordering and numeric serialization do not create false conflicts.
        if(!result.type().equals(type) || !sameJson(result.payload(),encoded))
            throw new IllegalArgumentException("Business key already exists with different type or payload");
        return result;
    }

    public JobRecord get(UUID id) { return jdbc.queryForObject("SELECT * FROM durable_jobs WHERE id=?",this::map,id); }

    /** Locks are held only for the bounded claim transaction, never across handlers or network I/O. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public List<JobRecord> claim(String owner,int limit,Duration lease) {
        requireText(owner,120,"lease owner");
        if(lease==null || lease.isZero() || lease.isNegative()) throw new IllegalArgumentException("Lease must be positive");
        if(limit<1) throw new IllegalArgumentException("Claim limit must be positive");
        Instant now=now();
        List<JobRecord> available=jdbc.query("SELECT * FROM durable_jobs WHERE "
            +"(status IN ('READY','WAITING') AND due_at<=?) OR (status='RUNNING' AND lease_expires_at<=?) "
            +"ORDER BY due_at,created_at,id LIMIT ? FOR UPDATE SKIP LOCKED",this::map,ts(now),ts(now),Math.min(10,limit));
        List<JobRecord> claimed=new ArrayList<>();
        for(JobRecord job:available) {
            jdbc.update("UPDATE durable_jobs SET status='RUNNING',attempts=attempts+1,lease_owner=?,lease_token=?,lease_expires_at=?,updated_at=? WHERE id=?",
                owner,UUID.randomUUID(),ts(now.plus(lease)),ts(now),job.id());
            claimed.add(get(job.id()));
        }
        return claimed;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean renew(JobRecord job,Duration lease) {
        if(lease==null || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("Lease must be positive");
        Instant now=now();
        return jdbc.update("UPDATE durable_jobs SET lease_expires_at=?,updated_at=? WHERE "+fence(),
            ts(now.plus(lease)),ts(now),job.id(),job.leaseToken(),job.leaseOwner(),ts(now))==1;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean complete(JobRecord job) {
        Instant now=now();
        return jdbc.update("UPDATE durable_jobs SET status='SUCCEEDED',lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,updated_at=? WHERE "+fence(),
            ts(now),job.id(),job.leaseToken(),job.leaseOwner(),ts(now))==1;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean fail(JobRecord job,JobStatus status,Instant dueAt,String fingerprint,int repeated,String sanitizedFailure) {
        if(status!=JobStatus.WAITING && status!=JobStatus.NEEDS_ATTENTION) throw new IllegalArgumentException("Invalid failure status");
        Instant now=now();
        return jdbc.update("UPDATE durable_jobs SET status=?,due_at=?,failure_fingerprint=?,failure_count=?,last_failure=?,"
            +"lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,updated_at=? WHERE "+fence(),
            status.name(),ts(dueAt),fingerprint,repeated,sanitizedFailure,ts(now),job.id(),job.leaseToken(),job.leaseOwner(),ts(now))==1;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public JobRecord retry(UUID id,String actor,String reason) {
        requireText(actor,120,"actor"); requireText(reason,500,"reason");
        JobRecord before=jdbc.queryForObject("SELECT * FROM durable_jobs WHERE id=? FOR UPDATE",this::map,id);
        if(before.status()!=JobStatus.WAITING && before.status()!=JobStatus.NEEDS_ATTENTION)
            throw new IllegalStateException("Only waiting or attention jobs may be retried");
        Instant now=now();
        jdbc.update("INSERT INTO durable_job_retry_audit (id,job_id,actor,reason,previous_status,created_at) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(),id,actor,reason,before.status().name(),ts(now));
        jdbc.update("UPDATE durable_jobs SET status='READY',due_at=?,failure_fingerprint=NULL,failure_count=0,last_failure=NULL,"
            +"lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL,updated_at=? WHERE id=?",ts(now),ts(now),id);
        return get(id);
    }

    private String fence(){return "id=? AND lease_token=? AND lease_owner=? AND status='RUNNING' AND lease_expires_at>?";}
    private Instant now(){return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",(rs,n)->rs.getTimestamp(1).toInstant());}
    private static Timestamp ts(Instant instant){return Timestamp.from(instant);}
    private static Instant instant(ResultSet rs,String name)throws SQLException {Timestamp value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
    private JobRecord map(ResultSet rs,int row)throws SQLException {
        Map<String,Object> payload;
        try {payload=json.readValue(rs.getString("payload"),new TypeReference<Map<String,Object>>(){});}
        catch(Exception invalid){payload=Map.of("_invalidPayload",true);}
        if(payload==null) payload=Map.of("_invalidPayload",true);
        return new JobRecord(rs.getObject("id",UUID.class),rs.getString("job_type"),rs.getString("business_key"),payload,
            rs.getInt("payload_version"),JobStatus.valueOf(rs.getString("status")),rs.getInt("attempts"),instant(rs,"due_at"),
            rs.getString("lease_owner"),rs.getObject("lease_token",UUID.class),instant(rs,"lease_expires_at"),
            rs.getString("failure_fingerprint"),rs.getInt("failure_count"),rs.getString("last_failure"),instant(rs,"created_at"),instant(rs,"updated_at"));
    }
    private String encode(Map<String,Object> payload){
        try{return json.writeValueAsString(payload);}catch(Exception invalid){throw new IllegalArgumentException("Payload must be JSON serializable",invalid);}
    }
    private boolean sameJson(Map<String,Object> stored,String encoded){
        try{return json.readTree(encode(stored)).equals(json.readTree(encoded));}
        catch(Exception invalid){throw new IllegalArgumentException("Payload must be JSON serializable",invalid);}
    }
    private static void requireText(String value,int length,String name){
        if(value==null || value.isBlank() || value.length()>length) throw new IllegalArgumentException("Invalid "+name);
    }
}
