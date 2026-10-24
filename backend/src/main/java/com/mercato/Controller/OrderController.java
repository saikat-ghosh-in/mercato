package com.mercato.Controller;

import com.mercato.Entity.fulfillment.TransitionTrigger;
import com.mercato.Payloads.Request.*;
import com.mercato.Payloads.Response.*;
import com.mercato.Service.CashfreeService;
import com.mercato.Service.FulfillmentService;
import com.mercato.Service.OrderLineUpdateService;
import com.mercato.Service.OrderService;
import com.mercato.Utils.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final FulfillmentService fulfillmentService;
    private final CashfreeService cashfreeService;
    private final OrderLineUpdateService orderLineUpdateService;
    private final AuthUtil authUtil;
    private final com.mercato.Commerce.PaymentInbox paymentInbox;
    private final com.mercato.Commerce.PaymentSessionRecovery recovery;

    @PostMapping("/orders/capture")
    public ResponseEntity<OrderPlacementResponseDTO> placeOrder(
            @Valid @RequestBody OrderCaptureRequestDTO orderCaptureRequestDTO) {

        OrderPlacementResponseDTO order = orderService.placeOrder(orderCaptureRequestDTO);
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @PostMapping(value = {"/public/cashfree/webhook","/public/cashfree/webhook-v2"}, consumes = "application/json")
    public ResponseEntity<String> handleWebhook(
            @RequestBody byte[] payload,
            @RequestHeader("x-webhook-signature") String signature,
            @RequestHeader("x-webhook-timestamp") String timestamp) {
        paymentInbox.receive(payload, signature, timestamp);
        return ResponseEntity.ok("Webhook received");
    }

    @org.springframework.beans.factory.annotation.Value("${frontend.base.url}") private String frontendOrigin;
    @GetMapping("/public/cashfree/return")
    public ResponseEntity<Void> legacyReturn(@RequestParam(name="order_id")String orderId){
        if(!orderId.matches("[A-Za-z0-9][A-Za-z0-9-]{0,29}"))throw new IllegalArgumentException("Invalid order identity");
        return ResponseEntity.status(303).location(java.net.URI.create(frontendOrigin+"/checkout/payment-confirmation?order_id="+orderId)).build();
    }

    @PostMapping("/orders/{orderId}/retry-payment")
    public ResponseEntity<PaymentRetryResponseDTO> retryPayment(@PathVariable String orderId) {
        String paymentSessionId = orderService.retryPayment(orderId);
        return ResponseEntity.ok(new PaymentRetryResponseDTO(paymentSessionId));
    }

    @PostMapping("/orders/{orderId}/sync-payment-refund")
    public ResponseEntity<String> syncPaymentAndRefund(@PathVariable String orderId) {
        return ResponseEntity.ok(cashfreeService.syncPaymentAndRefund(orderId));
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<List<OrderResponseDTO>> getAllOrders() {

        List<OrderResponseDTO> orders = orderService.getAllOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/user/orders")
    public ResponseEntity<List<OrderResponseDTO>> getCurrentUserOrders() {

        List<OrderResponseDTO> orders = orderService.getCurrentUserOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/user/orders/{orderId}")
    public ResponseEntity<OrderResponseDTO> getCurrentUserOrder(@PathVariable String orderId) {

        OrderResponseDTO order = orderService.getCurrentUserOrder(orderId);
        return new ResponseEntity<>(order, HttpStatus.OK);
    }

    @GetMapping("/user/orders/{orderId}/payment-session")
    public Object paymentSession(@PathVariable String orderId) {return recovery.recover(orderId);}

    @PutMapping("/user/orders/{orderId}")
    public ResponseEntity<OrderResponseDTO> editCurrentUserOrder(@PathVariable String orderId) {

        OrderResponseDTO order = orderService.getCurrentUserOrder(orderId);
        return new ResponseEntity<>(order, HttpStatus.OK);
    }

    @GetMapping("/user/orders/summary")
    public ResponseEntity<List<OrderSummaryDTO>> getCurrentUserOrderSummaries() {
        return ResponseEntity.ok(orderService.getCurrentUserOrderSummaries());
    }

    @GetMapping("/admin/orders/summary")
    public ResponseEntity<List<AdminOrderSummaryDTO>> getAllOrderSummaries() {
        return ResponseEntity.ok(orderService.getAllOrderSummaries());
    }

    @GetMapping("/admin/orders/{orderId}")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable String orderId) {

        OrderResponseDTO order = orderService.getOrder(orderId);
        return new ResponseEntity<>(order, HttpStatus.OK);
    }

    @GetMapping("/seller/open-fulfillment-orders")
    public ResponseEntity<List<FulfillmentOrderResponseDTO>> getOpenFulfillmentOrders() {
        return ResponseEntity.ok(fulfillmentService.getOpenFulfillmentOrders());
    }

    @GetMapping("/seller/closed-fulfillment-orders")
    public ResponseEntity<List<FulfillmentOrderResponseDTO>> getClosedFulfillmentOrders() {
        return ResponseEntity.ok(fulfillmentService.getClosedFulfillmentOrders());
    }

    @GetMapping("/seller/fulfillment-orders/{fulfillmentId}")
    public ResponseEntity<FulfillmentOrderResponseDTO> getFulfillmentOrder(@PathVariable String fulfillmentId) {
        return ResponseEntity.ok(fulfillmentService.getFulfillmentOrder(fulfillmentId));
    }

    @PostMapping("/order-line/update")
    public ResponseEntity<OrderLineResponseDTO> updateOrderLine(@RequestBody OrderLineUpdateRequestDTO request) {
        TransitionTrigger trigger = authUtil.resolveTransitionTrigger();
        OrderLineResponseDTO response = orderLineUpdateService.updateOrderLine(request, trigger);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/user/orders/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(@RequestBody @Valid OrderCancelRequestDTO request) {
        TransitionTrigger trigger = authUtil.resolveTransitionTrigger();
        OrderResponseDTO response = orderService.cancelOrder(request, trigger);
        return ResponseEntity.ok(response);
    }
}
