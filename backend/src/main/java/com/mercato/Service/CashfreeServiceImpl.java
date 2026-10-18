package com.mercato.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.ExceptionHandler.InsufficientInventoryException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Payloads.Response.CashfreeOrderResponse;
import com.mercato.Repository.OrderRepository;
import com.mercato.Repository.PaymentRepository;
import com.mercato.Repository.RefundRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Utils.AuthUtil;
import com.mercato.Utils.CartContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashfreeServiceImpl implements CashfreeService {

    @Value("${cashfree.app.id}")
    private String appId;

    @Value("${cashfree.secret.key}")
    private String secretKey;

    @Value("${cashfree.base.url}")
    private String baseUrl;

    @Value("${cashfree.api.version}")
    private String apiVersion;

    @Value("${cashfree.return.url}")
    private String returnUrl;

    @Value("${cashfree.notify.url}")
    private String notifyUrl;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderReservationService orderReservationService;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final AuthUtil authUtil;
    private final EmailService emailService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RefundRepository refundRepository;

    @Override
    public CashfreeOrderResponse createOrder(Order order, EcommUser user) {
        Map<String, Object> body = buildOrderRequestBody(order, user);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());
            String cfOrderId = json.get("cf_order_id").asText();
            String paymentSessionId = json.get("payment_session_id").asText();

            log.info("Cashfree order created: cfOrderId={}, orderId={}", cfOrderId, order.getOrderId());
            return new CashfreeOrderResponse(cfOrderId, paymentSessionId);

        } catch (Exception e) {
            log.error("Failed to create Cashfree order for {}: {}", order.getOrderId(), e.getMessage());
            throw new CustomBadRequestException("Payment initiation failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void initiatePayment(Order order, PaymentMethod paymentMethod,
                                CashfreeOrderResponse cashfreeOrder) {
        Payment payment = new Payment();
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(order.getCurrency());
        payment.setPaymentMethod(paymentMethod);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setCfOrderId(cashfreeOrder.getCfOrderId());
        payment.setPaymentSessionId(cashfreeOrder.getPaymentSessionId());
        payment.setGatewayName("cashfree");
        payment.setInitiatedAt(Instant.now());

        order.attachPayment(payment);
        order.setPaymentStatus(PaymentStatus.INITIATED);
    }

    @Override
    public void terminateOrder(String cfOrderId) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("order_status", "TERMINATED");

            restTemplate.exchange(
                    baseUrl + "/orders/" + cfOrderId,
                    HttpMethod.PATCH,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class
            );
            log.info("Terminated Cashfree order: {}", cfOrderId);
        } catch (Exception e) {
            log.warn("Failed to terminate Cashfree order {}: {}", cfOrderId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String signature, String timestamp) {
        if (!verifyWebhookSignature(payload, signature, timestamp)) {
            log.error("Webhook signature verification failed");
            throw new CustomBadRequestException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.get("type").asText();
            log.info("Received Cashfree webhook: {}", eventType);

            switch (eventType) {
                case "PAYMENT_SUCCESS_WEBHOOK" -> handlePaymentSuccess(root);
                case "PAYMENT_FAILED_WEBHOOK" -> handlePaymentFailed(root);
                case "PAYMENT_USER_DROPPED_WEBHOOK" -> handlePaymentUserDropped(root);
                case "REFUND_STATUS_WEBHOOK" -> handleRefundStatus(root);
                default -> log.warn("Unhandled webhook event: {}", eventType);
            }
        } catch (CustomBadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String retryPayment(Order order, EcommUser user) {
        Payment payment = order.getPayment();

        if (payment == null) {
            throw new CustomBadRequestException("No payment found for this order");
        }
        if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            throw new CustomBadRequestException("Order is already paid");
        }

        CashfreeOrderResponse cashfreeOrder = fetchExistingOrCreateOrder(order, user);

        payment.setStatus(PaymentStatus.INITIATED);
        payment.setCfOrderId(cashfreeOrder.getCfOrderId());
        payment.setPaymentSessionId(cashfreeOrder.getPaymentSessionId());
        payment.setGatewayReference(null);
        payment.setGatewayResponseMessage(null);
        payment.setCompletedAt(null);
        payment.setInitiatedAt(Instant.now());
        paymentRepository.save(payment);

        log.info("Retry payment initiated for order: {}", order.getOrderId());
        return cashfreeOrder.getPaymentSessionId();
    }

    @Override
    @Transactional
    public String syncPaymentAndRefund(String orderId) {
        EcommUser currentUser = authUtil.getLoggedInUser();
        orderRepository.findByOrderIdAndCustomerEmail(orderId, currentUser.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/orders/" + orderId + "/payments",
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());

            if (!json.isArray() || json.isEmpty()) {
                log.warn("No payment attempts found for orderId={}, syncing refund only", orderId);
                syncRefund(orderId, payment);
                return "No payment attempts found, refund status synced";
            }

            JsonNode latestAttempt = json.get(0);
            String paymentStatus = latestAttempt.get("payment_status").asText();
            String cfPaymentId = latestAttempt.get("cf_payment_id").asText();
            String paymentGroup = latestAttempt.has("payment_group")
                    ? latestAttempt.get("payment_group").asText() : "cod";
            String message = latestAttempt.has("payment_message")
                    ? latestAttempt.get("payment_message").asText() : null;

            String result = switch (paymentStatus.toUpperCase()) {
                case "SUCCESS" -> {
                    payment.setStatus(PaymentStatus.SUCCESS);
                    payment.setGatewayReference(cfPaymentId);
                    payment.setGatewayResponseMessage("Payment verified via polling");
                    payment.setPaymentMethod(resolvePaymentMethod(paymentGroup));
                    if (payment.getCompletedAt() == null) payment.setCompletedAt(Instant.now());
                    paymentRepository.save(payment);

                    log.info("Payment synced to SUCCESS for orderId={}", orderId);
                    yield "Payment synced successfully";
                }
                case "FAILED" -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setGatewayReference(cfPaymentId);
                    payment.setGatewayResponseMessage(message);
                    if (payment.getCompletedAt() == null) payment.setCompletedAt(Instant.now());
                    paymentRepository.save(payment);

                    log.warn("Payment synced to FAILED for orderId={}", orderId);
                    yield "Payment has failed";
                }
                case "USER_DROPPED" -> {
                    payment.setStatus(PaymentStatus.USER_DROPPED);
                    payment.setGatewayReference(cfPaymentId);
                    payment.setGatewayResponseMessage("User dropped payment");
                    paymentRepository.save(payment);

                    log.warn("Payment synced to USER_DROPPED for orderId={}", orderId);
                    yield "Payment was dropped by user";
                }
                case "PENDING" -> {
                    payment.setStatus(PaymentStatus.PENDING);
                    paymentRepository.save(payment);

                    log.info("Payment still PENDING for orderId={}", orderId);
                    yield "Payment is still pending";
                }
                default -> {
                    log.warn("Unhandled payment status={} for orderId={}", paymentStatus, orderId);
                    yield "Unknown payment status: " + paymentStatus;
                }
            };

            syncOrderStatus(order, payment);
            syncRefund(orderId, payment);
            return result;

        } catch (Exception e) {
            log.error("Failed to sync payment for orderId={}: {}", orderId, e.getMessage());
            throw new CustomBadRequestException("Payment sync failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String initiateRefund(Order order, Refund refund) {

        Map<String, Object> body = new HashMap<>();
        body.put("refund_amount", refund.getAmount());
        body.put("refund_id", refund.getRefundId());
        body.put("refund_note", "Order cancellation - " + order.getOrderId());
        body.put("refund_speed", "STANDARD");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/orders/" + order.getOrderId() + "/refunds",
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());
            String cfRefundId = json.get("cf_refund_id").asText();

            log.info("Refund initiated for orderId={}, cfRefundId={}, amount={}",
                    order.getOrderId(), cfRefundId, refund.getRefundId());
            return cfRefundId;
        } catch (Exception e) {
            log.error("Failed to initiate refund for orderId={}: {}", order.getOrderId(), e.getMessage());
            throw new CustomBadRequestException("Refund initiation failed: " + e.getMessage());
        }
    }

    private @NonNull Map<String, Object> buildOrderRequestBody(Order order, EcommUser user) {
        Map<String, Object> body = new HashMap<>();
        body.put("order_id", order.getOrderId());
        body.put("order_amount", order.getTotalAmount());
        body.put("order_currency", order.getCurrency());
        body.put("order_note", "Mercato order: " + order.getOrderId());

        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("customer_id", user.getUserId());
        customerDetails.put("customer_name", user.getUsername());
        customerDetails.put("customer_email", user.getEmail());
        customerDetails.put("customer_phone", order.getRecipientPhone());
        body.put("customer_details", customerDetails);

        Map<String, Object> orderMeta = new HashMap<>();
        orderMeta.put("return_url", returnUrl + "?order_id=" + order.getOrderId());
        orderMeta.put("notify_url", notifyUrl);
        body.put("order_meta", orderMeta);
        return body;
    }

    private CashfreeOrderResponse fetchExistingOrCreateOrder(Order order, EcommUser user) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/orders/" + order.getOrderId(),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());
            String orderStatus = json.get("order_status").asText();

            if ("ACTIVE".equalsIgnoreCase(orderStatus)) {
                String cfOrderId = json.get("cf_order_id").asText();
                String paymentSessionId = json.get("payment_session_id").asText();
                log.info("Reusing existing Cashfree order for orderId={}", order.getOrderId());
                return new CashfreeOrderResponse(cfOrderId, paymentSessionId);
            }

            log.info("Cashfree order exists but status={}, creating new for orderId={}",
                    orderStatus, order.getOrderId());
            return createOrder(order, user);

        } catch (Exception e) {
            log.info("Cashfree order not found for orderId={}, creating new", order.getOrderId());
            return createOrder(order, user);
        }
    }

    private void syncRefund(String orderId, Payment payment) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/orders/" + orderId + "/refunds",
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());

            if (!json.isArray() || json.isEmpty()) {
                log.info("No refunds found on Cashfree for orderId={}", orderId);
                return;
            }

            for (JsonNode refundNode : json) {
                String cfRefundId = refundNode.get("cf_refund_id").asText();
                String refundStatus = refundNode.get("refund_status").asText();
                String refundNote = refundNode.has("refund_note")
                        ? refundNode.get("refund_note").asText() : "Refund from Cashfree";
                BigDecimal refundAmount = refundNode.has("refund_amount")
                        ? BigDecimal.valueOf(refundNode.get("refund_amount").asDouble()) : null;

                Optional<Refund> existing = refundRepository.findByGatewayReference(cfRefundId);

                if (existing.isPresent()) {
                    Refund refund = existing.get();
                    syncRefundStatus(refund, refundStatus);
                    refundRepository.save(refund);
                    log.info("Refund {} status synced to {}", cfRefundId, refundStatus);
                } else {
                    if (refundAmount == null) {
                        log.warn("Refund amount missing for cfRefundId={}, skipping", cfRefundId);
                        continue;
                    }
                    Refund refund = Refund.builder()
                            .payment(payment)
                            .gatewayReference(cfRefundId)
                            .amount(refundAmount)
                            .currency(payment.getCurrency())
                            .reason(refundNote)
                            .status(RefundStatus.PENDING)
                            .build();
                    syncRefundStatus(refund, refundStatus);
                    refundRepository.save(refund);
                    log.info("Refund {} created locally from Cashfree dashboard sync", cfRefundId);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to sync refunds for orderId={}: {}", orderId, e.getMessage());
        }
    }

    private void syncRefundStatus(Refund refund, String refundStatus) {
        switch (refundStatus.toUpperCase()) {
            case "SUCCESS" -> {
                refund.setStatus(RefundStatus.SUCCESS);
                refund.setGatewayResponseMessage("Refund successful");
            }
            case "FAILED", "CANCELLED" -> {
                refund.setStatus(RefundStatus.FAILED);
                refund.setFailureReason("Refund " + refundStatus.toLowerCase());
            }
            case "PENDING" -> refund.setStatus(RefundStatus.PENDING);
            case "ONHOLD" -> {
                refund.setStatus(RefundStatus.PENDING);
                refund.setGatewayResponseMessage("Refund on hold - under review by Cashfree");
            }
            default -> log.warn("Unhandled refund status: {}", refundStatus);
        }
    }

    private void syncOrderStatus(Order order, Payment payment) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/orders/" + order.getOrderId(),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());
            String cfOrderStatus = json.get("order_status").asText();

            switch (cfOrderStatus.toUpperCase()) {
                case "PAID" -> {
                    if (!PaymentStatus.SUCCESS.equals(payment.getStatus())) {
                        payment.setStatus(PaymentStatus.SUCCESS);
                        payment.setGatewayResponseMessage("Synced from Cashfree order status");
                        payment.setCompletedAt(Instant.now());
                        paymentRepository.save(payment);
                    }
                    finalizeOrderAfterPaymentSuccess(order);
                    log.info("Order {} synced to PAID/CONFIRMED from Cashfree", order.getOrderId());
                }
                case "ACTIVE" -> log.info("Order {} still ACTIVE on Cashfree", order.getOrderId());
                case "EXPIRED" -> {
                    if (!PaymentStatus.FAILED.equals(payment.getStatus())) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setGatewayResponseMessage("Order expired on Cashfree");
                        paymentRepository.save(payment);
                        log.warn("Order {} synced to EXPIRED", order.getOrderId());
                    }
                }
                case "TERMINATED" -> {
                    if (!PaymentStatus.CANCELLED.equals(payment.getStatus())) {
                        payment.setStatus(PaymentStatus.CANCELLED);
                        payment.setGatewayResponseMessage("Order terminated on Cashfree");
                        paymentRepository.save(payment);
                        log.warn("Order {} synced to TERMINATED", order.getOrderId());
                    }
                }
                default -> log.warn("Unhandled Cashfree order status={} for orderId={}",
                        cfOrderStatus, order.getOrderId());
            }

        } catch (Exception e) {
            log.warn("Failed to sync order status for orderId={}: {}", order.getOrderId(), e.getMessage());
        }
    }

    private void handlePaymentSuccess(JsonNode root) {
        JsonNode data = root.get("data");
        String orderId = data.get("order").get("order_id").asText();
        String cfPaymentId = data.get("payment").get("cf_payment_id").asText();
        String paymentGroup = data.get("payment").get("payment_group").asText();

        log.info("Processing PAYMENT_SUCCESS for orderId={}, cfPaymentId={}", orderId, cfPaymentId);

        Payment payment = findPaymentWithRetry(orderId);

        if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            log.info("Payment already confirmed for orderId={}, reconciling inventory", orderId);
            Order order = orderRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
            finalizeOrderAfterPaymentSuccess(order);
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayReference(cfPaymentId);
        payment.setGatewayResponseMessage("Payment succeeded");
        payment.setPaymentMethod(resolvePaymentMethod(paymentGroup));
        payment.setCompletedAt(Instant.now());
        paymentRepository.save(payment);

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        if (finalizeOrderAfterPaymentSuccess(order)) {
            emailService.sendOrderConfirmationEmail(
                    order.getCustomerEmail(),
                    order.getCustomerName(),
                    order.getOrderId(),
                    order.getSubtotal(),
                    order.getCharges(),
                    order.getTotalAmount()
            );
        }

        log.info("Order confirmed via webhook: {}", orderId);
    }

    /**
     * @return true when this invocation created the order reservations and should send the confirmation email
     */
    boolean finalizeOrderAfterPaymentSuccess(Order order) {
        boolean wasAlreadyConfirmed = OrderStatus.CONFIRMED.equals(order.getOrderStatus());
        if (order.getOrderStatus() != OrderStatus.CREATED && !order.isInventoryFinalizationFailed()) return false;

        Optional<EcommUser> buyer = userRepository.findByEmail(order.getCustomerEmail());
        Cart cart = buyer.map(cartService::getCartByUser).orElse(null);

        boolean inventoryOwned;
        try {
            inventoryOwned = orderReservationService.finalizeInventoryForPaidOrder(order, cart);
        } catch (InsufficientInventoryException ex) {
            log.warn("Inventory finalization failed for order {}: {}", order.getOrderId(), ex.getMessage());
            order.setInventoryFinalizationFailed(true);
            orderRepository.save(order);
            return false;
        }

        order.setInventoryFinalizationFailed(false);

        if (!wasAlreadyConfirmed) {
            order.confirmOrder();
            recordConfirmationTransitions(order);
        }

        if (inventoryOwned && cart != null && !cart.isEmpty()) {
            cartService.removePurchasedItems(cart, order);
        }

        orderRepository.save(order);

        return inventoryOwned;
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode data = root.get("data");
        String orderId = data.get("order").get("order_id").asText();
        String cfPaymentId = data.get("payment").get("cf_payment_id").asText();
        String message = data.get("payment").get("payment_message").asText();

        log.warn("Processing PAYMENT_FAILED for orderId={}, cfPaymentId={}", orderId, cfPaymentId);

        paymentRepository.findByOrder_OrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayReference(cfPaymentId);
            payment.setGatewayResponseMessage(message);
            payment.setCompletedAt(Instant.now());
            paymentRepository.save(payment);

            orderRepository.findByOrderId(orderId).ifPresent(order -> {
                order.setPaymentStatus(PaymentStatus.FAILED);
                orderRepository.save(order);
            });

            log.warn("Payment failed for order: {}", orderId);
        });
    }

    private void handlePaymentUserDropped(JsonNode root) {
        JsonNode data = root.get("data");
        String orderId = data.get("order").get("order_id").asText();
        String cfPaymentId = data.get("payment").get("cf_payment_id").asText();

        log.warn("Processing PAYMENT_USER_DROPPED for orderId={}", orderId);

        paymentRepository.findByOrder_OrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.USER_DROPPED);
            payment.setGatewayReference(cfPaymentId);
            payment.setGatewayResponseMessage("User dropped payment");
            paymentRepository.save(payment);

            orderRepository.findByOrderId(orderId).ifPresent(order -> {
                order.setPaymentStatus(PaymentStatus.USER_DROPPED);
                orderRepository.save(order);
            });
        });
    }

    private void handleRefundStatus(JsonNode root) {
        JsonNode refundNode = root.get("data").get("refund");
        String merchantRefundId = refundNode.get("refund_id").asText();
        String cfRefundId = refundNode.has("cf_refund_id")
                ? refundNode.get("cf_refund_id").asText() : null;
        String status = refundNode.get("refund_status").asText();
        String message = refundNode.has("refund_message")
                ? refundNode.get("refund_message").asText() : null;

        log.info("Processing REFUND_STATUS_WEBHOOK for refundId={}, status={}",
                merchantRefundId, status);

        Optional<Refund> refundLookup = refundRepository.findByRefundId(merchantRefundId);
        if (refundLookup.isEmpty() && cfRefundId != null) {
            refundLookup = refundRepository.findByGatewayReference(cfRefundId);
        }

        refundLookup.ifPresent(refund -> {
            switch (status.toUpperCase()) {
                case "SUCCESS" -> {
                    refund.setStatus(RefundStatus.SUCCESS);
                    refund.setGatewayResponseMessage("Refund successful");
                }
                case "FAILED", "CANCELLED" -> {
                    refund.setStatus(RefundStatus.FAILED);
                    refund.setFailureReason(message);
                }
                default -> log.warn("Unhandled refund status: {} for refundId={}",
                        status, merchantRefundId);
            }
            refundRepository.save(refund);
            log.info("Refund {} updated to status={}", merchantRefundId, status);
        });
    }

    private Payment findPaymentWithRetry(String orderId) {
        int maxAttempts = 5;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Optional<Payment> payment = paymentRepository.findByOrder_OrderId(orderId);
            if (payment.isPresent()) return payment.get();
            log.warn("Payment not found for orderId={} (attempt {}/{})", orderId, attempt, maxAttempts);
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new ResourceNotFoundException("Payment", "orderId", orderId);
    }

    private boolean verifyWebhookSignature(String payload, String signature, String timestamp) {
        try {
            String data = timestamp + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", apiVersion);
        return headers;
    }

    private PaymentMethod resolvePaymentMethod(String paymentGroup) {
        return switch (paymentGroup) {
            case "credit_card", "debit_card" -> PaymentMethod.CARD;
            case "upi" -> PaymentMethod.UPI;
            case "net_banking" -> PaymentMethod.NET_BANKING;
            case "wallet" -> PaymentMethod.WALLET;
            case "credit_card_emi", "debit_card_emi" -> PaymentMethod.CREDIT_CARD_EMI;
            case "cardless_emi" -> PaymentMethod.CARDLESS_EMI;
            case "pay_later" -> PaymentMethod.PAY_LATER;
            default -> PaymentMethod.UNKNOWN;
        };
    }

    private void recordConfirmationTransitions(Order order) {
        order.getOrderLines().forEach(orderLine ->
                orderLine.addStateTransition(
                        StateTransition.builder()
                                .orderLine(orderLine)
                                .fromStatus(OrderLineStatus.CREATED)
                                .toStatus(OrderLineStatus.CONFIRMED)
                                .action(OrderLineAction.CONFIRM)
                                .triggeredBy(TransitionTrigger.SYSTEM)
                                .reason("Payment confirmed via Cashfree")
                                .build()
                )
        );
    }
}
