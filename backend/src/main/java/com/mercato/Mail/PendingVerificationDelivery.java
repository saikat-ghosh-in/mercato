package com.mercato.Mail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import java.util.*;

/** No public recipient lookup: this status belongs only to the browser that created the account. */
@Service
public class PendingVerificationDelivery {
    private static final String EMAIL="pendingVerificationEmail",JOB="pendingVerificationJob";
    private final MailActivity activity;
    public PendingVerificationDelivery(MailActivity activity){this.activity=activity;}
    public void registered(HttpServletRequest request,String email){
        var session=request.getSession(true);session.setAttribute(EMAIL,email.toLowerCase(Locale.ROOT));
        activity.latestVerification(email).ifPresent(id->session.setAttribute(JOB,id.toString()));
    }
    public void resent(HttpServletRequest request,String email){
        var session=request.getSession(false);
        if(session!=null&&email.equalsIgnoreCase(Objects.toString(session.getAttribute(EMAIL),"")))registered(request,email);
    }
    public Map<String,Object> status(HttpServletRequest request){
        var session=request.getSession(false);Object value=session==null?null:session.getAttribute(JOB);
        if(!(value instanceof String id))return Map.of("state","UNKNOWN","message","If your account needs verification, your email request will be processed. Delivery status is available only in the browser used to register.");
        return activity.verificationStatus(UUID.fromString(id));
    }
}
