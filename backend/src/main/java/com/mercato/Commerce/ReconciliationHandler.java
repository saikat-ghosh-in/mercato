package com.mercato.Commerce;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;
@Component
public class ReconciliationHandler implements JobHandler {
    private final CashfreeGateway gateway;private final PaymentInbox inbox;
    public ReconciliationHandler(CashfreeGateway gateway,PaymentInbox inbox){this.gateway=gateway;this.inbox=inbox;}
    public String type(){return "RECONCILE_ORDER";}
    public void handle(JobRecord job){
        String id=(String)job.payload().get("orderId");
        try{var payments=gateway.get("/orders/"+id+"/payments");if(!payments.isArray())throw new PoisonJobException("Invalid payment attempts response");for(var attempt:payments)inbox.polled(id,attempt);}
        catch(CashfreeGateway.GatewayFailure e){if(e.temporary())throw new TransientJobException("Payment reconciliation temporarily unavailable");if(e.missing())return;throw new ManualInterventionJobException("Payment reconciliation rejected");}
    }
}
