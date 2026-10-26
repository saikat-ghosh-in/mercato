package com.mercato.Service;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLineAction;
import com.mercato.Entity.fulfillment.TransitionTrigger;
import com.mercato.Jobs.JobRecord;
import com.mercato.Repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Checks current facts without holding the aggregate lock across SMTP. */
@Service
public class FulfillmentNoticeGuard {
    private final OrderRepository orders;
    private final FulfillmentWorkflow workflow;

    public FulfillmentNoticeGuard(OrderRepository orders,FulfillmentWorkflow workflow) {
        this.orders=orders;
        this.workflow=workflow;
    }

    public static String businessKey(String orderId,String seller,String kind) {
        try {
            String identity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seller.getBytes(StandardCharsets.UTF_8)));
            return "fulfillment-mail:"+orderId+":"+kind+":"+identity;
        } catch(NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public boolean eligible(Order order,String seller,String kind) {
        if(seller==null||seller.isBlank())return false;
        OrderLineAction action;
        if("REMINDER".equals(kind))action=OrderLineAction.ACCEPT;
        else if("RELEASE".equals(kind))action=OrderLineAction.CANCEL;
        else return false;
        // An accepted survivor can still prepare/pack/cancel after acceptance closes.
        // Cancellation eligibility is positive for these remaining unshipped units.
        return order.getOrderLines().stream().anyMatch(line->seller.equals(line.getSellerEmail())
                && workflow.maximum(line,TransitionTrigger.SELLER,action)>0);
    }

    /** This read transaction ends before the mail handler enters TrackedMailSender. */
    @Transactional(readOnly=true)
    public boolean current(JobRecord job) {
        var payload=job.payload();
        String orderId,seller,kind;
        Instant deadline=null;
        boolean structured=payload.containsKey("orderId")||payload.containsKey("sellerEmail")
                ||payload.containsKey("fulfillmentNotificationKind")||payload.containsKey("acceptDeadline");
        if(structured) {
            if(!(payload.get("orderId") instanceof String id)
                    || !(payload.get("sellerEmail") instanceof String email)
                    || !(payload.get("fulfillmentNotificationKind") instanceof String notificationKind)
                    || !(payload.get("acceptDeadline") instanceof String announcedDeadline)
                    || !email.equals(payload.get("to")))return false;
            orderId=id;seller=email;kind=notificationKind;
            if(!job.businessKey().equals(businessKey(orderId,seller,kind))
                    && !job.businessKey().equals("fulfillment-mail:"+orderId+":"+kind+":"+seller))return false;
            try {deadline=Instant.parse(announcedDeadline);}
            catch(java.time.format.DateTimeParseException invalid){return false;}
        } else {
            // Older generic envelopes still carry exact identity in their bounded key.
            // Remove the full recipient suffix first, so punctuation cannot alter the scope.
            if(!(payload.get("to") instanceof String email)||email.isBlank())return false;
            String prefix="fulfillment-mail:",suffix=":"+email,key=job.businessKey();
            if(key.length()>255||key.length()<=prefix.length()+suffix.length()||!key.startsWith(prefix)||!key.endsWith(suffix))return false;
            String identity=key.substring(prefix.length(),key.length()-suffix.length());
            int split=identity.lastIndexOf(':');
            if(split<=0)return false;
            orderId=identity.substring(0,split);kind=identity.substring(split+1);seller=email;
            if(orderId.isBlank()||orderId.length()>30)return false;
        }
        var order=orders.findByOrderId(orderId).orElse(null);
        return order!=null && (deadline==null||deadline.equals(workflow.deadline(order))) && eligible(order,seller,kind);
    }
}
