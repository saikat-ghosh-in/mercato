package com.mercato.Controller;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class FulfillmentWorkflowController {
    private final FulfillmentSlaMetrics slaMetrics;private final WorkflowAdoption adoption;private final FulfillmentCancellation cancellation;private final OrderLineUpdateService updates;private final AuthUtil auth;private final FulfillmentDeadlines deadlines;
    @PostMapping("/fulfillment/commands") public OrderLineResponseDTO command(@RequestBody OrderLineUpdateRequestDTO request) {
        WorkflowCommands.requireExternalOperation(request.getOperationId());
        if(request.getOperationId()==null||request.getExpectedVersion()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"operationId and expectedVersion are required");
        return updates.updateOrderLine(request,auth.resolveTransitionTrigger());
    }
    @PostMapping("/seller/fulfillment/commands") public OrderLineResponseDTO sellerCommand(@RequestBody OrderLineUpdateRequestDTO request) {
        WorkflowCommands.requireExternalOperation(request.getOperationId());
        return updates.updateOrderLine(request,com.mercato.Entity.fulfillment.TransitionTrigger.SELLER);
    }
    @PostMapping("/seller/fulfillment/cancellations") public Object sellerCancel(@RequestBody com.mercato.Payloads.Request.FulfillmentCancelRequest request){WorkflowCommands.requireExternalOperation(request.getOperationId());return cancellation.cancel(request,com.mercato.Entity.fulfillment.TransitionTrigger.SELLER);}
    @PostMapping("/user/fulfillment/commands") public OrderLineResponseDTO customerCommand(@RequestBody OrderLineUpdateRequestDTO request) {
        WorkflowCommands.requireExternalOperation(request.getOperationId());
        return updates.updateOrderLine(request,com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);
    }
    @PostMapping("/fulfillment/cancellations") public Object cancel(@RequestBody com.mercato.Payloads.Request.FulfillmentCancelRequest request){WorkflowCommands.requireExternalOperation(request.getOperationId());return cancellation.cancel(request,auth.resolveTransitionTrigger());}
    @PostMapping("/user/fulfillment/cancellations") public Object customerCancel(@RequestBody com.mercato.Payloads.Request.FulfillmentCancelRequest request){WorkflowCommands.requireExternalOperation(request.getOperationId());return cancellation.cancel(request,com.mercato.Entity.fulfillment.TransitionTrigger.CUSTOMER);}
    @PostMapping("/admin/fulfillment/adopt") public Object adopt(@RequestBody WorkflowAdoption.Request request){WorkflowCommands.requireExternalOperation(request.operationId());return adoption.adopt(request,auth.resolveTransitionTrigger());}
    @GetMapping("/admin/fulfillment/work-items") public Object workItems(){return deadlines.workItems();}
    @GetMapping("/admin/fulfillment/metrics") public Object metrics(@RequestParam(required=false) java.time.Instant from,@RequestParam(required=false) java.time.Instant to){return slaMetrics.report(null,from,to);}
    @GetMapping("/seller/fulfillment/metrics") public Object sellerMetrics(@RequestParam(required=false) java.time.Instant from,@RequestParam(required=false) java.time.Instant to){return slaMetrics.report(auth.getLoggedInUser().getEmail(),from,to);}
}
