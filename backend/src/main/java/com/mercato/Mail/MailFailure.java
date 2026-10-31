package com.mercato.Mail;
import jakarta.mail.MessagingException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import java.util.*;
import java.util.regex.Pattern;

/** Only fixed, reviewed diagnostics may leave the SMTP boundary. Never persist provider text. */
public record MailFailure(String code, Integer smtpCode, boolean retryable, String message) {
    private static final Pattern SMTP=Pattern.compile("(?<![0-9])([245][0-9]{2})(?![0-9])");
    public static MailFailure classify(Throwable failure) {
        if(failure instanceof org.springframework.mail.MailPreparationException)return new MailFailure("EMAIL_COMPOSITION_FAILED",null,false,"Email could not be prepared. Review the email template and sender configuration.");
        var pending=new ArrayDeque<Throwable>();pending.add(failure);
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        Integer code=null;boolean auth=false;
        while(!pending.isEmpty()&&seen.size()<32){
            var current=pending.remove();if(!seen.add(current))continue;
            auth|=current instanceof MailAuthenticationException;
            var match=SMTP.matcher(Objects.toString(current.getMessage(),""));
            if(match.find()){int candidate=Integer.parseInt(match.group(1));if(code==null||candidate==525)code=candidate;}
            if(current.getCause()!=null)pending.add(current.getCause());
            if(current instanceof MessagingException mail&&mail.getNextException()!=null)pending.add(mail.getNextException());
            if(current instanceof MailSendException mail)pending.addAll(mail.getFailedMessages().values());
        }
        if(Objects.equals(code,525))return new MailFailure("SMTP_IP_NOT_AUTHORIZED",code,false,"Sending server IP is not authorized by the email provider.");
        if(code!=null&&code>=400&&code<500)return new MailFailure("SMTP_TEMPORARILY_UNAVAILABLE",code,true,"Email service is temporarily unavailable. A retry is scheduled.");
        if(auth||Objects.equals(code,535))return new MailFailure("SMTP_AUTH_FAILED",code,false,"Email provider authentication failed. Check the SMTP key and account settings.");
        if(code!=null&&code>=500)return new MailFailure("SMTP_REJECTED",code,false,"Email provider rejected this message. Review the recipient, sender and provider logs before retrying.");
        return new MailFailure("SMTP_TEMPORARILY_UNAVAILABLE",code,true,"Email service is temporarily unavailable. A retry is scheduled.");
    }
}
