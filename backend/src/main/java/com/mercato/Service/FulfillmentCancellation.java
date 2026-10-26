package com.mercato.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Payloads.Request.*;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Repository.OrderRepository;
import com.mercato.Utils.AuthUtil;
import com.mercato.ExceptionHandler.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FulfillmentCancellation {
    private final OrderRepository orders;private final FulfillmentWorkflow policy;private final WorkflowCommands commands;
    private final OrderLineUpdateService updates;private final AuthUtil auth;private final ObjectMapper json;
    public FulfillmentCancellation(OrderRepository orders,FulfillmentWorkflow policy,WorkflowCommands commands,OrderLineUpdateService updates,AuthUtil auth,ObjectMapper json){this.orders=orders;this.policy=policy;this.commands=commands;this.updates=updates;this.auth=auth;this.json=json;}
    @Transactional public List<OrderLineResponseDTO> cancel(FulfillmentCancelRequest request,TransitionTrigger actor) {
        if(request==null||request.getOrderId()==null||request.getReason()==null||request.getReason().isBlank()||request.getReason().length()>500)
            throw new CustomBadRequestException("Order and cancellation reason are required");
        var o=orders.lockByOrderId(request.getOrderId()).orElseThrow(()->new ResourceNotFoundException("Order","orderId",request.getOrderId()));
        String identity=auth.getLoggedInUser().getEmail();
        if(actor==TransitionTrigger.CUSTOMER && !identity.equals(o.getCustomerEmail()))throw new ForbiddenOperationException("Customer does not own order");
        if(actor==TransitionTrigger.SYSTEM)throw new ForbiddenOperationException("System cancellation requires deadline handler");
        boolean explicit=request.getUnits()!=null&&!request.getUnits().isEmpty();
        if(actor==TransitionTrigger.ADMIN && !explicit && request.getFulfillmentId()==null)throw new CustomBadRequestException("Admin must select fulfillment or explicit units");
        if(actor==TransitionTrigger.SELLER && request.getFulfillmentId()==null)throw new CustomBadRequestException("Seller must select a fulfillment");
        List<OrderLine> scoped=o.getOrderLines().stream().filter(l->request.getFulfillmentId()==null||request.getFulfillmentId().equals(l.getFulfillmentId())).toList();
        if(scoped.isEmpty()||actor==TransitionTrigger.SELLER&&scoped.stream().anyMatch(l->!identity.equals(l.getSellerEmail())))throw new ForbiddenOperationException("Seller does not own fulfillment");
        String body=encode(Arrays.asList("CANCEL_SCOPE",request,actor,identity));
        String replay=commands.stored(o,request.getOperationId(),request.getExpectedVersion(),body);
        if(replay!=null){try{return json.readValue(replay,new TypeReference<List<OrderLineResponseDTO>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
        Map<Integer,Integer> selected=new HashMap<>();
        if(explicit)for(var unit:request.getUnits()) {
            if(unit==null||unit.getOrderLineNumber()<1||unit.getQty()<1)throw new CustomBadRequestException("Selected lines require positive line numbers and quantities");
            if(selected.put(unit.getOrderLineNumber(),unit.getQty())!=null)throw new CustomBadRequestException("Duplicate selected line");
        }
        if(explicit&&selected.keySet().stream().anyMatch(n->scoped.stream().noneMatch(l->l.getOrderLineNumber()==n)))throw new CustomBadRequestException("Selected line is outside scope");
        var lines=scoped.stream().filter(l->explicit?selected.containsKey(l.getOrderLineNumber()):l.getPendingQty()>0).sorted(Comparator.comparing(OrderLine::getProductId).thenComparingInt(OrderLine::getOrderLineNumber)).toList();
        if(lines.isEmpty())throw new CustomBadRequestException("No cancellable units");
        for(var l:lines)policy.require(l,actor,OrderLineAction.CANCEL,explicit?selected.get(l.getOrderLineNumber()):l.getPendingQty());
        var results=new ArrayList<OrderLineResponseDTO>();int qty=0;
        for(var l:lines){
            var r=new OrderLineUpdateRequestDTO();r.setFulfillmentId(l.getFulfillmentId());r.setOrderLineNumber(l.getOrderLineNumber());r.setQty(explicit?selected.get(l.getOrderLineNumber()):l.getPendingQty());r.setAction(OrderLineAction.CANCEL);r.setReason(request.getReason());
            r.setOperationId("scope:"+UUID.nameUUIDFromBytes(request.getOperationId().getBytes(StandardCharsets.UTF_8))+":"+l.getOrderLineNumber());r.setExpectedVersion(o.getWorkflowRevision());
            qty+=r.getQty();results.add(updates.updateOrderLine(r,actor));
        }
        for(var result:results)result.setWorkflow(policy.project(lines.stream().filter(l->l.getOrderLineNumber()==result.getOrderLineNumber()).findFirst().orElseThrow(),actor));
        commands.recordScope(o,request.getOperationId(),body,actor,identity,request.getReason(),qty,encode(results));return results;
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
}
