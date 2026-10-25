package com.mercato.Mail;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Service;
import org.springframework.mail.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TrackedMailSender {
    private final BrandedMailSender mail;private final MailActivity activity;
    public TrackedMailSender(BrandedMailSender mail,MailActivity activity){this.mail=mail;this.activity=activity;}
    public void send(JobRecord job,String to,String subject,String text){
        send(job,to,subject,text,BrandedEmail.action(job));
    }
    public void send(JobRecord job,String to,String subject,String text,BrandedEmail.Action action){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SMTP must run outside database transactions");
        if(activity.accepted(job.id()))return;
        if(!activity.record(job,"SENDING",null))throw new TransientJobException("Email lease expired before sending");
        try{mail.send(to,subject,text,action);activity.record(job,"ACCEPTED",null);}
        catch(MailException error){
            var failure=MailFailure.classify(error);activity.record(job,failure.retryable()?"RETRYING":"FAILED",failure);
            if(failure.retryable())throw new TransientJobException(failure.code(),error);
            throw new ManualInterventionJobException(failure.code(),error);
        }
    }
}
