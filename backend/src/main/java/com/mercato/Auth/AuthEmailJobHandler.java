package com.mercato.Auth;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;import org.springframework.mail.SimpleMailMessage;import org.springframework.mail.javamail.JavaMailSender;import org.springframework.beans.factory.annotation.Value;
@Component
public class AuthEmailJobHandler implements JobHandler {
 private final JavaMailSender mail;private final String from;
 public AuthEmailJobHandler(JavaMailSender mail,@Value("${mercato.email.from}")String from){this.mail=mail;this.from=from;}
 @Override public String type(){return "AUTH_EMAIL";}
 @Override public void handle(JobRecord job){var p=job.payload();if(!(p.get("to") instanceof String to)||!(p.get("subject") instanceof String subject)||!(p.get("text") instanceof String text))throw new PoisonJobException("Invalid authentication mail payload");var message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);message.setSubject(subject);message.setText(text);try{mail.send(message);}catch(org.springframework.mail.MailException e){throw new TransientJobException("Authentication mail delivery failed",e);}}
}
