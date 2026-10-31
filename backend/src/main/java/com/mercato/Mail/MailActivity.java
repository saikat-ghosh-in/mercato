package com.mercato.Mail;
import com.mercato.Jobs.JobRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
public class MailActivity {
    private final JdbcTemplate jdbc;
    public MailActivity(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean accepted(UUID id){return jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs WHERE id=? AND email_accepted_at IS NOT NULL",Long.class,id)>0;}
    /** An expired worker cannot overwrite its replacement. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean record(JobRecord job,String state,MailFailure failure){
        return jdbc.update("UPDATE durable_jobs SET email_state=?,email_code=?,email_smtp_code=?,email_attempt_at=CURRENT_TIMESTAMP,"
            +"email_accepted_at=CASE WHEN ?='ACCEPTED' THEN CURRENT_TIMESTAMP ELSE email_accepted_at END "
            +"WHERE id=? AND lease_token=? AND lease_owner=? AND status='RUNNING' AND lease_expires_at>CURRENT_TIMESTAMP",
            state,failure==null?null:failure.code(),failure==null?null:failure.smtpCode(),state,job.id(),job.leaseToken(),job.leaseOwner())==1;
    }
    public Optional<UUID> latestVerification(String email){
        return jdbc.query("SELECT id FROM durable_jobs WHERE email_purpose='VERIFICATION' AND LOWER(email_recipient)=LOWER(?) ORDER BY created_at DESC,id DESC LIMIT 1",
            (rs,n)->rs.getObject(1,UUID.class),email).stream().findFirst();
    }
    public Map<String,Object> verificationStatus(UUID id){
        var rows=jdbc.queryForList("SELECT status,email_state,email_code,attempts FROM durable_jobs WHERE id=? AND email_purpose='VERIFICATION'",id);
        if(rows.isEmpty())return Map.of("state","UNKNOWN","message","Delivery status is unavailable. You can request another link.");
        var row=rows.get(0);String state=Objects.toString(row.get("email_state"),"QUEUED");
        if("ACCEPTED".equals(state))return Map.of("state",state,"message","The email service accepted your verification email. Check your inbox and spam folder; inbox delivery is not yet confirmed.");
        if("SKIPPED".equals(state))return Map.of("state",state,"message","This verification link is no longer active. Use the latest email or request another link.");
        if("FAILED".equals(state)||"NEEDS_ATTENTION".equals(row.get("status")))return Map.of("state","FAILED","message","We couldn’t send your verification email. Our team can see the failure. Please try again later.");
        if("RETRYING".equals(state))return Map.of("state",state,"message","We couldn’t send your email yet. We’ll retry automatically.");
        return Map.of("state","QUEUED","message","Your verification email is queued. We’ll update this status when the email service responds.");
    }
}
