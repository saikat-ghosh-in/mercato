package com.mercato.Auth;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;
import com.mercato.Mail.*;
@Component
public class AuthEmailJobHandler implements JobHandler {
 private final TrackedMailSender mail;private final AuthenticationMailGuard guard;private final MailActivity activity;
 public AuthEmailJobHandler(TrackedMailSender mail,AuthenticationMailGuard guard,MailActivity activity){this.mail=mail;this.guard=guard;this.activity=activity;}
 @Override public String type(){return "AUTH_EMAIL";}
 @Override public void handle(JobRecord job){
  if(activity.accepted(job.id()))return;
  var p=job.payload();
  if(!(p.get("to") instanceof String to)||!(p.get("subject") instanceof String subject)||!(p.get("text") instanceof String text))throw new PoisonJobException("Invalid authentication mail payload");
  if(!guard.current(job)){
   var reason=job.businessKey().startsWith("fulfillment-mail:")?new MailFailure("FULFILLMENT_NOTICE_INELIGIBLE",null,false,"Fulfillment notice is no longer applicable."):null;
   if(!activity.record(job,"SKIPPED",reason))throw new TransientJobException("Email lease expired before skip was recorded");
   return;
  }
  mail.send(job,to,subject,text);
 }
}
