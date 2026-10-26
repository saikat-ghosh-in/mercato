package com.mercato.Mail;
import com.mercato.Jobs.JobRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationMailGuard {
    private final JdbcTemplate jdbc;
    private final org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessions;
    private final com.mercato.Service.FulfillmentNoticeGuard fulfillment;
    public AuthenticationMailGuard(JdbcTemplate jdbc,org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessions,com.mercato.Service.FulfillmentNoticeGuard fulfillment){this.jdbc=jdbc;this.sessions=sessions;this.fulfillment=fulfillment;}
    public boolean current(JobRecord job){
        String key=job.businessKey();
        if(key.startsWith("fulfillment-mail:"))return fulfillment.current(job);
        if(key.startsWith("verify-email:"))return jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens t JOIN users u ON u.id=t.user_id WHERE t.token=? AND t.verified_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP AND u.enabled=true AND u.email_verified=false AND LOWER(u.email)=LOWER(?)",Long.class,key.substring("verify-email:".length()),job.payload().get("to"))>0;
        if(key.startsWith("password-reset:"))return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens t JOIN users u ON u.id=t.user_id WHERE t.token=? AND t.used_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP AND u.enabled=true AND LOWER(u.email)=LOWER(?)",Long.class,key.substring("password-reset:".length()),job.payload().get("to"))>0;
        if(key.startsWith("step-up:")){
            if(!(job.payload().get("userId") instanceof String userId)||!(job.payload().get("challengeId") instanceof String challengeId))return false;
            final java.util.UUID delivery;try{delivery=java.util.UUID.fromString(challengeId);}catch(IllegalArgumentException bad){return false;}
            var rows=jdbc.queryForList("SELECT c.session_hash,u.access_version FROM auth_email_challenges c JOIN users u ON u.user_id=c.user_id WHERE c.user_id=? AND c.delivery_id=? AND c.expires_at>CURRENT_TIMESTAMP AND c.attempts<5 AND u.enabled=true AND u.email_verified=true AND u.account_locked=false AND LOWER(u.email)=LOWER(?)",userId,delivery,job.payload().get("to"));
            if(rows.isEmpty())return false;
            var row=rows.get(0);
            return sessions.findByPrincipalName(userId).values().stream().anyMatch(session->{
                var identity=session.getAttribute(com.mercato.Session.SessionService.IDENTITY);
                return com.mercato.Auth.AuthHash.digest(session.getId()).equals(row.get("session_hash"))
                    &&identity instanceof com.mercato.Session.SessionIdentity id&&"STEP_UP_REQUIRED".equals(id.mfaState())
                    &&id.accessVersion()==((Number)row.get("access_version")).longValue()&&id.expiresAt().isAfter(java.time.Instant.now())
                    &&id.authenticatedAt().plusSeconds(600).isAfter(java.time.Instant.now());
            });
        }
        return true;
    }
}
