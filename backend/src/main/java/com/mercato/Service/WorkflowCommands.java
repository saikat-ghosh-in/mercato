package com.mercato.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.util.*;

/** Caller holds the aggregate lock. Command rows commit atomically with quantity/inventory changes. */
@Service
@org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
public class WorkflowCommands {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final FulfillmentWorkflow workflow; private final org.springframework.context.ApplicationEventPublisher events;
    public WorkflowCommands(JdbcTemplate jdbc,ObjectMapper json,FulfillmentWorkflow workflow,org.springframework.context.ApplicationEventPublisher events){this.jdbc=jdbc;this.json=json;this.workflow=workflow;this.events=events;}
    public static void requireExternalOperation(String operation) {
        if(operation==null||operation.isBlank()||operation.length()>120||operation.startsWith("accept-timeout:")||operation.startsWith("scope:")||operation.startsWith("whole:")||operation.startsWith("parcel:"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A valid client operationId is required; internal command namespaces are reserved");
    }
    private String body(OrderLineUpdateRequestDTO r,TransitionTrigger trigger,String actor) {
        return encode(Arrays.asList(r.getFulfillmentId(),r.getOrderLineNumber(),r.getAction(),r.getQty(),r.getReason(),r.getExpectedVersion(),trigger,actor));
    }
    public OrderLineResponseDTO replay(Order order,OrderLineUpdateRequestDTO r,TransitionTrigger trigger,String actor) {
        String stored=stored(order,r.getOperationId(),r.getExpectedVersion(),body(r,trigger,actor));
        if(stored==null)return null;
        try{return json.readValue(stored,OrderLineResponseDTO.class);}catch(Exception e){throw new IllegalStateException("Persisted workflow outcome unreadable",e);}
    }
    public String stored(Order order,String operation,Long expected,String body) {
        if(operation==null||operation.isBlank()||operation.length()>120||expected==null||expected<0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"operationId and expectedVersion are required");
        var rows=jdbc.queryForList("SELECT request_body,outcome FROM fulfillment_commands WHERE order_id=? AND operation_id=?",order.getOrderId(),operation);
        if(!rows.isEmpty()) {
            if(!rows.get(0).get("request_body").equals(body))throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation ID already used with different request");
            return (String)rows.get(0).get("outcome");
        }
        if(expected!=order.getWorkflowRevision())throw new ResponseStatusException(HttpStatus.CONFLICT,"Stale workflow version");
        return null;
    }
    public <T> T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Persisted workflow outcome unreadable",e);}}
    public void recordScope(Order o,String operation,String body,TransitionTrigger actor,String identity,String reason,int qty,String outcome) {
        recordAggregate(o,operation,body,actor,identity,reason,"CANCEL_SCOPE",qty,outcome);
    }
    public void recordAggregate(Order o,String operation,String body,TransitionTrigger actor,String identity,String reason,String action,int qty,String outcome) {
        jdbc.update("INSERT INTO fulfillment_commands(order_id,operation_id,request_body,outcome,actor_type,actor_identity,reason,action,line_number,qty,unaccepted_qty,accepted_only_qty,preparing_only_qty,packed_only_qty,created_at) VALUES(?,?,?,?,?,?,?,?,0,?,0,0,0,0,?)",
            o.getOrderId(),operation,body,outcome,actor.name(),identity,reason,action,qty,Timestamp.from(workflow.now()));
    }

    public void record(Order order,OrderLine line,OrderLineUpdateRequestDTO r,TransitionTrigger trigger,String actor,int qty,int unaccepted,int acceptedOnly,int preparingOnly,OrderLineResponseDTO outcome) {
        String operation=r.getOperationId();
        int u=0,a=0,p=0,k=0;
        if(r.getAction()==OrderLineAction.CANCEL){u=Math.min(qty,unaccepted);a=Math.min(qty-u,acceptedOnly);p=Math.min(qty-u-a,preparingOnly);k=qty-u-a-p;}
        jdbc.update("INSERT INTO fulfillment_commands(order_id,operation_id,request_body,outcome,actor_type,actor_identity,reason,action,line_number,qty,unaccepted_qty,accepted_only_qty,preparing_only_qty,packed_only_qty,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            order.getOrderId(),operation,body(r,trigger,actor),encode(outcome),trigger.name(),actor,r.getReason(),r.getAction().name(),line.getOrderLineNumber(),qty,u,a,p,k,Timestamp.from(workflow.now()));
        if(r.getAction()==OrderLineAction.CANCEL) {
            String code=switch(trigger){case CUSTOMER->"CUSTOMER_REQUEST";case SELLER->"SELLER_CANCELLATION";case SYSTEM->"SYSTEM_SELLER_DELAY";case ADMIN->"ADMIN_ACTION";};
            int first=line.getCancelledQty()-qty+1;int last=line.getCancelledQty();
            jdbc.update("UPDATE fulfillment_commands SET reason_code=?,fulfillment_id=?,cancellation_ordinal_from=?,cancellation_ordinal_to=? WHERE order_id=? AND operation_id=?",code,line.getFulfillmentId(),first,last,order.getOrderId(),operation);
            events.publishEvent(new FulfillmentCancellationRecorded(order.getOrderId(),line.getFulfillmentId(),line.getOrderLineNumber(),operation,trigger,actor,code,r.getReason(),qty,first,last,u,a,p,k,workflow.now()));
        }
    }
    public String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Cannot encode workflow command",e);}}
}
