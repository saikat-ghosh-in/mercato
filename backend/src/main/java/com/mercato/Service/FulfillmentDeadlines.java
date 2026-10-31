package com.mercato.Service;

import com.mercato.Entity.fulfillment.*;
import com.mercato.Jobs.*;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Repository.OrderRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class FulfillmentDeadlines implements JobHandler {
    private final DurableJobs jobs;private final OrderRepository orders;private final FulfillmentWorkflow workflow;
    private final OrderLineUpdateService updates;private final JdbcTemplate jdbc;private final FulfillmentSlaMetrics slaMetrics;private final FulfillmentNoticeGuard notices;
    public FulfillmentDeadlines(DurableJobs jobs,OrderRepository orders,FulfillmentWorkflow workflow,@Lazy OrderLineUpdateService updates,JdbcTemplate jdbc,FulfillmentSlaMetrics slaMetrics,FulfillmentNoticeGuard notices) {
        this.jobs=jobs;this.orders=orders;this.workflow=workflow;this.updates=updates;this.jdbc=jdbc;this.slaMetrics=slaMetrics;this.notices=notices;
    }
    public String type(){return "FULFILLMENT_DEADLINE";}
    public void schedule(Order order) {
        var release=workflow.releaseAt(order);if(release==null)return;
        enqueue(order,"RELEASE",release);enqueue(order,"REMINDER",release.plusSeconds(47*3600));
        enqueue(order,"ACCEPT_TIMEOUT",release.plusSeconds(48*3600));enqueue(order,"PREPARATION_REVIEW",workflow.preparationReviewAt(order));
    }
    private void enqueue(Order o,String kind,Instant due){jobs.enqueueAt(type(),"fulfillment:"+o.getOrderId()+":"+kind,Map.of("orderId",o.getOrderId(),"kind",kind),due);}
    @Transactional public void handle(JobRecord job) {
        var order=orders.lockByOrderId((String)job.payload().get("orderId")).orElseThrow(()->new PoisonJobException("Missing fulfillment order"));
        if(order.isWorkflowReviewRequired()||order.getWorkflowVersion()!=2||order.getConfirmedAt()==null||order.getSellerActionsAvailableAt()==null)
            throw new ManualInterventionJobException("Fulfillment confirmation or prospective SLA adoption requires review");
        String kind=(String)job.payload().get("kind");
        Instant due=switch(kind){case "RELEASE"->workflow.releaseAt(order);case "REMINDER"->workflow.deadline(order).minusSeconds(3600);case "ACCEPT_TIMEOUT"->workflow.deadline(order);case "PREPARATION_REVIEW"->workflow.preparationReviewAt(order);default->throw new PoisonJobException("Unknown fulfillment deadline");};
        if(due==null||workflow.now().isBefore(due))throw new WaitingJobException("Fulfillment deadline is not due",Duration.ofMinutes(1));
        if(order.getOrderLines().stream().allMatch(l->l.getPendingQty()==0))return;
        int affected=0;
        if("ACCEPT_TIMEOUT".equals(kind)) {
            for(var line:order.getOrderLines().stream().sorted(Comparator.comparing(OrderLine::getProductId).thenComparingInt(OrderLine::getOrderLineNumber)).toList()) {
                int qty=line.getOrderedQty()-line.getCancelledQty()-line.getAcceptedQty();if(qty<=0)continue;
                var r=new OrderLineUpdateRequestDTO();r.setFulfillmentId(line.getFulfillmentId());r.setOrderLineNumber(line.getOrderLineNumber());r.setAction(OrderLineAction.CANCEL);r.setQty(qty);
                r.setReason("SYSTEM_SELLER_DELAY: acceptance deadline expired");r.setOperationId("accept-timeout:"+line.getOrderLineNumber());r.setExpectedVersion(order.getWorkflowRevision());
                updates.updateOrderLine(r,TransitionTrigger.SYSTEM);affected+=qty;
            }
        } else if("PREPARATION_REVIEW".equals(kind)) {
            affected=order.getOrderLines().stream().mapToInt(l->l.getOrderedQty()-l.getCancelledQty()-l.getShippedQty()).sum();
        } else {
            for(String seller:order.getOrderLines().stream().map(OrderLine::getSellerEmail).distinct().filter(seller->notices.eligible(order,seller,kind)).toList()) {
                jobs.enqueue("AUTH_EMAIL",FulfillmentNoticeGuard.businessKey(order.getOrderId(),seller,kind),Map.of("to",seller,"orderId",order.getOrderId(),"sellerEmail",seller,"fulfillmentNotificationKind",kind,"acceptDeadline",workflow.deadline(order).toString(),"subject","Mercato order "+("RELEASE".equals(kind)?"ready for action":"acceptance reminder"),"text","Review order "+order.getOrderId()+". Acceptance deadline: "+workflow.deadline(order)));
            }
        }
        jdbc.update("INSERT INTO fulfillment_work_items(order_id,kind,due_at,observed_at,affected_qty) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",order.getOrderId(),kind,Timestamp.from(due),Timestamp.from(workflow.now()),affected);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> workItems(){return jdbc.queryForList("SELECT * FROM fulfillment_work_items ORDER BY due_at,order_id");}
    @Transactional(readOnly=true) public FulfillmentSlaMetrics.Report metrics(){return slaMetrics.report(null,null,null);}
}
