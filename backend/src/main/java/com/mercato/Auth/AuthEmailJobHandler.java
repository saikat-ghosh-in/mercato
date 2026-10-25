package com.mercato.Auth;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;
import com.mercato.Mail.*;
@Component
public class AuthEmailJobHandler implements JobHandler {
 private final TrackedMailSender mail;private final AuthenticationMailGuard guard;private final MailActivity activity;
 public AuthEmailJobHandler(TrackedMailSender mail,AuthenticationMailGuard guard,MailActivity activity){this.mail=mail;this.guard=guard;this.activity=activity;}
 @Override public String type(){return "AUTH_EMAIL";}
 @Override public void handle(JobRecord job){var p=job.payload();if(!(p.get("to") instanceof String to)||!(p.get("subject") instanceof String subject)||!(p.get("text") instanceof String text))throw new PoisonJobException("Invalid authentication mail payload");if(!guard.current(job)){activity.record(job,"SKIPPED",null);return;}mail.send(job,to,subject,text);}
}
