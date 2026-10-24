package com.mercato.Commerce;
import com.mercato.Jobs.*;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;
@Component
public class TerminationHandler implements JobHandler {
    private final CashfreeGateway gateway;private final DurableJobs jobs;
    public TerminationHandler(CashfreeGateway gateway,DurableJobs jobs){this.gateway=gateway;this.jobs=jobs;}
    public String type(){return "TERMINATE_ORDER";}
    public void handle(JobRecord job){
        String id=(String)job.payload().get("orderId");
        try {
            var state=gateway.get("/orders/"+id).path("order_status").asText();
            if(Set.of("TERMINATED","EXPIRED").contains(state))return;
            if("PAID".equals(state)){jobs.enqueue("RECONCILE_ORDER","reconcile-paid-cancel:"+id,Map.of("orderId",id));return;}
            gateway.patch("/orders/"+id,Map.of("order_status","TERMINATED"),"terminate:"+id);
            throw new WaitingJobException("Verify gateway termination",Duration.ofSeconds(5));
        }catch(CashfreeGateway.GatewayFailure e){if(e.missing())return;if(e.temporary())throw new TransientJobException("Gateway termination pending reconciliation");throw new ManualInterventionJobException("Gateway termination rejected");}
    }
}
