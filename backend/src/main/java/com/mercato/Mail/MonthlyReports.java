package com.mercato.Mail;
import com.mercato.Entity.*;
import com.mercato.Repository.UserRepository;
import com.mercato.Jobs.DurableJobs;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class MonthlyReports {
    private final UserRepository users;private final DurableJobs jobs;private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final boolean enabled;private final ZoneId zone;
    public MonthlyReports(UserRepository users,DurableJobs jobs,org.springframework.jdbc.core.JdbcTemplate jdbc,
        @Value("${mercato.email.reports.enabled:false}")boolean enabled,@Value("${mercato.email.reports.zone:Asia/Kolkata}")String zone){this.users=users;this.jobs=jobs;this.jdbc=jdbc;this.enabled=enabled;this.zone=ZoneId.of(zone);}
    public static YearMonth reportMonth(Clock clock,ZoneId zone){return YearMonth.now(clock.withZone(zone)).minusMonths(1);}
    public static boolean eligible(EcommUser user){return user.isEnabled()&&!user.isAccountLocked()&&user.isEmailVerified()&&(user.isAdmin()||(user.isSeller()&&!user.isSellerSuspended()&&user.getSellerStatus()==SellerStatus.APPROVED));}
    @Scheduled(fixedDelayString="${mercato.email.reports.poll-ms:3600000}",initialDelayString="${mercato.email.reports.initial-delay-ms:120000}")
    @Transactional
    public void schedule(){
        if(!enabled)return;
        YearMonth month=reportMonth(Clock.systemUTC(),zone);
        // A bounded batch skips already scheduled recipients; retries belong to the durable worker.
        var recipients=jdbc.queryForList("SELECT DISTINCT u.user_id FROM users u JOIN user_role ur ON ur.user_id=u.id JOIN roles r ON r.role_id=ur.role_id "
            +"WHERE u.enabled=true AND u.account_locked=false AND u.email_verified=true AND (r.role_name='ROLE_ADMIN' OR (r.role_name='ROLE_SELLER' AND u.seller_status='APPROVED' AND u.seller_suspended=false)) "
            +"AND NOT EXISTS (SELECT 1 FROM durable_jobs j WHERE j.business_key='monthly-report:'||?||':'||u.user_id) ORDER BY u.user_id LIMIT 100",String.class,month.toString());
        for(String id:recipients){var user=users.findByUserId(id).orElseThrow();if(eligible(user))jobs.enqueue("MONTHLY_REPORT","monthly-report:"+month+":"+id,Map.of("userId",id,"month",month.toString(),"to",user.getEmail()));}
    }
    public ZoneId zone(){return zone;}
}
