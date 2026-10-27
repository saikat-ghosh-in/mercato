package com.mercato.Service;

import com.mercato.Entity.fulfillment.*;
import com.mercato.ExceptionHandler.*;
import com.mercato.Mapper.OrderLineMapper;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderLineUpdateServiceImpl implements OrderLineUpdateService {
    private final OrderLineRepository orderLineRepository;
    private final OrderRepository orderRepository;
    private final OrderReservationService orderReservationService;
    private final RefundService refundService;
    private final ShipmentNotificationService shipmentNotificationService;
    private final AuthUtil authUtil;
    private final FulfillmentWorkflow workflow;
    private final WorkflowCommands commands;

    @Override @Transactional
    public OrderLineResponseDTO updateOrderLine(OrderLineUpdateRequestDTO request, TransitionTrigger trigger) {
        if(request==null||request.getFulfillmentId()==null||request.getFulfillmentId().isBlank()||request.getOrderLineNumber()==null||request.getOrderLineNumber()<1||request.getAction()==null||request.getQty()==null||request.getQty()<=0)
            throw new CustomBadRequestException("Fulfillment, line, action and positive quantity are required");
        if(request.getReason()!=null&&request.getReason().length()>500)throw new CustomBadRequestException("Reason is too long");
        if(request.getAction()==OrderLineAction.CANCEL&&(request.getReason()==null||request.getReason().isBlank()))throw new CustomBadRequestException("Cancellation reason is required");
        var order=orderRepository.lockByFulfillment(request.getFulfillmentId(),request.getOrderLineNumber())
            .orElseThrow(()->new ResourceNotFoundException("OrderLine","fulfillmentId",request.getFulfillmentId()));
        var line=order.getOrderLines().stream().filter(l->request.getFulfillmentId().equals(l.getFulfillmentId()) && request.getOrderLineNumber()==l.getOrderLineNumber()).findFirst()
            .orElseThrow(()->new ResourceNotFoundException("OrderLine","fulfillmentId",request.getFulfillmentId()));
        authorize(line,trigger);
        String actor=trigger==TransitionTrigger.SYSTEM?"SYSTEM":authUtil.getLoggedInUser().getEmail();
        var repeated=commands.replay(order,request,trigger,actor);
        if(repeated!=null)return repeated;
        int qty=request.getQty();
        workflow.require(line,trigger,request.getAction(),qty);
        var from=line.getOrderLineStatus();
        int unaccepted=line.getOrderedQty()-line.getCancelledQty()-line.getAcceptedQty();
        int acceptedOnly=line.getAcceptedQty()-line.getPreparingQty();
        int preparingOnly=line.getPreparingQty()-line.getPackedQty();
        com.mercato.Commerce.UnitFinancials.transition(line,request.getAction(),qty,trigger,request.getOperationId(),workflow.now());
        switch(request.getAction()) {
            case ACCEPT -> line.accept(qty);
            case START_PREPARING -> line.startPreparing(qty);
            case MARK_PACKED -> line.markPacked(qty);
            case SHIP -> {line.ship(qty);orderReservationService.settleQty(line,qty,OrderLineAction.SHIP);}
            case CANCEL -> {line.cancel(qty);line.setOrderLineStatus(line.deriveStatus());orderReservationService.settleQty(line,qty,OrderLineAction.CANCEL);}
            default -> throw new CustomBadRequestException("Unsupported workflow action");
        }
        line.setOrderLineStatus(line.getAcceptedQty()>0 && line.getPendingQty()>0?OrderLineStatus.PROCESSING:line.deriveStatus());
        line.addStateTransition(StateTransition.builder().fromStatus(from).toStatus(line.getOrderLineStatus()).action(request.getAction()).triggeredBy(trigger)
            .qtyAffected(qty).reason(request.getReason()).occurredAt(workflow.now()).build());
        order.workflowChanged();syncOrderStatus(order);
        orderRepository.saveAndFlush(order);
        var result=OrderLineMapper.toDTO(line);result.setWorkflow(workflow.project(line,trigger));
        commands.record(order,line,request,trigger,actor,qty,unaccepted,acceptedOnly,preparingOnly,result);
        if(request.getAction()==OrderLineAction.CANCEL)refundService.processRefundIfRequired(order);
        if(request.getAction()==OrderLineAction.SHIP)shipmentNotificationService.notifyShippedIfNeeded(order);
        return result;
    }
    private void authorize(OrderLine line,TransitionTrigger trigger) {
        if(trigger==TransitionTrigger.SELLER && !line.getSellerEmail().equals(authUtil.getLoggedInUser().getEmail()))throw new ForbiddenOperationException("Seller does not own this line");
        if(trigger==TransitionTrigger.CUSTOMER && !line.getOrder().getCustomerEmail().equals(authUtil.getLoggedInUser().getEmail()))throw new ForbiddenOperationException("Customer does not own this order");
    }
    private void syncOrderStatus(Order order) {
        boolean cancelled=order.getOrderLines().stream().allMatch(l->l.getCancelledQty()==l.getOrderedQty());
        boolean closed=order.getOrderLines().stream().allMatch(l->l.getPendingQty()==0);
        if(cancelled)order.setOrderStatus(OrderStatus.CANCELLED);
        else if(closed)order.setOrderStatus(OrderStatus.FULFILLMENT_COMPLETE);
        else if(order.getOrderLines().stream().anyMatch(l->l.getAcceptedQty()>0))order.setOrderStatus(OrderStatus.FULFILLMENT_PROCESSING);
    }
}
