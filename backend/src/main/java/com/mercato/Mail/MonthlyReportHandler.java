package com.mercato.Mail;
import com.mercato.Jobs.*;
import com.mercato.Repository.UserRepository;
import com.mercato.Service.MarketplaceFinanceService;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.YearMonth;

@Component
public class MonthlyReportHandler implements JobHandler {
    private final UserRepository users;private final MarketplaceFinanceService finance;private final MonthlyReports reports;
    private final TrackedMailSender mail;private final MailActivity activity;private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    public MonthlyReportHandler(UserRepository users,MarketplaceFinanceService finance,MonthlyReports reports,TrackedMailSender mail,MailActivity activity,JdbcTemplate jdbc,PlatformTransactionManager manager){this.users=users;this.finance=finance;this.reports=reports;this.mail=mail;this.activity=activity;this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);}
    public String type(){return "MONTHLY_REPORT";}
    private record Prepared(String to,String text,boolean admin){}
    public void handle(JobRecord job){
        if(!(job.payload().get("userId") instanceof String id)||!(job.payload().get("month") instanceof String value))throw new PoisonJobException("Invalid monthly report payload");
        final YearMonth month;try{month=YearMonth.parse(value);}catch(Exception bad){throw new PoisonJobException("Invalid monthly report period");}
        if(!month.isBefore(YearMonth.now(reports.zone())))throw new PoisonJobException("Monthly report period is not complete");
        Prepared prepared=tx.execute(t->{
            var user=users.findByUserId(id).orElse(null);
            if(user==null||!MonthlyReports.eligible(user)||!user.getEmail().equals(job.payload().get("to")))return null;
            String scope=user.isAdmin()?"":" AND p.seller_fk=?";
            Object[] args=user.isAdmin()?new Object[]{}:new Object[]{user.getId()};
            long count=jdbc.queryForObject("SELECT COUNT(*) FROM products p WHERE p.active=true AND (p.listing_status IS NULL OR p.listing_status='APPROVED')"+scope,Long.class,args);
            long low=jdbc.queryForObject("SELECT COUNT(*) FROM products p WHERE p.active=true AND (p.listing_status IS NULL OR p.listing_status='APPROVED') AND p.physical_qty-p.reserved_qty<=5"+scope,Long.class,args);
            return new Prepared(user.getEmail(),MonthlyReportContent.build(month,user.isAdmin(),finance.monthlyReport(id,month,reports.zone()),count,low),user.isAdmin());
        });
        if(prepared==null){activity.record(job,"SKIPPED",null);return;}
        mail.send(job,prepared.to(),"Your Mercato report · "+month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",java.util.Locale.ENGLISH)),prepared.text(),new BrandedEmail.Action("View dashboard",prepared.admin()?"/admin/dashboard":"/seller/dashboard"));
    }
}
