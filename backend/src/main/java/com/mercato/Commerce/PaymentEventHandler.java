package com.mercato.Commerce;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;
@Component
public class PaymentEventHandler implements JobHandler {
    private final PaymentReconciler reconciliation;
    public PaymentEventHandler(PaymentReconciler reconciliation){this.reconciliation=reconciliation;}
    public String type(){return "PAYMENT_EVENT";}
    public void handle(JobRecord job){reconciliation.process((String)job.payload().get("eventId"));}
}
