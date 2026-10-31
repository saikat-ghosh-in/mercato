package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.Payment;
import com.mercato.Entity.fulfillment.payment.PaymentMethod;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.OrderMapper;
import com.mercato.Payloads.Request.OrderCancelRequestDTO;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Payloads.Response.*;
import com.mercato.Payloads.Request.OrderCaptureRequestDTO;
import com.mercato.Repository.AddressRepository;
import com.mercato.Repository.OrderRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    public static final int MAX_CHECKOUT_UNITS=10_000;

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final FulfillmentWorkflow workflow;
    private final OrderProductImages images;
    private final FulfillmentCancellation cancellation;
    private final WorkflowCommands workflowCommands;
    private final CashfreeService cashfreeService;
    private final AddressRepository addressRepository;
    private final AuthUtil authUtil;
    private final CartReservationService cartReservationService;
    private final OrderLineUpdateService orderLineUpdateService;
    private final OrderRetryHoldService orderRetryHoldService;
    private final CommissionService commissions;
    private final com.mercato.Repository.SellerApplicationRepository sellerApplications;
    private final org.springframework.transaction.PlatformTransactionManager transactions;
    private final com.mercato.Commerce.RefundObligations refundObligations;

    @Override
    public OrderPlacementResponseDTO placeOrder(OrderCaptureRequestDTO request) {
        if(request==null || request.getOperationId()==null || request.getOperationId().isBlank() || request.getOperationId().length()>120
            || request.getExpectedCartVersion()==null || request.getExpectedCartVersion()<0)
            throw new CustomBadRequestException("operationId and non-negative expectedCartVersion are required");
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        EcommUser buyer = authUtil.getLoggedInUser();
        String orderId=tx.execute(status -> prepareOrder(request,buyer));
        // Intent lookup may hold the user/cart mutex, but never locks an existing
        // order. Commit that transaction before entering the order-first protocol.
        Order draft=tx.execute(status->{var order=ownedOrder(orderId,buyer);requirePayable(order);
            orderRetryHoldService.ensureCartHoldsForRetry(order,buyer);
            org.hibernate.Hibernate.initialize(order.getPayment());return order;});
        if(draft.getPayment().getPaymentSessionId()!=null && !draft.getPayment().getPaymentSessionId().isBlank())
            return tx.execute(status->{var order=ownedOrder(orderId,buyer);requirePayable(order);return placement(order);});
        CashfreeOrderResponse gateway;
        try {gateway=cashfreeService.createOrder(draft,buyer);}
        catch(RuntimeException failure){tx.executeWithoutResult(status->cashfreeService.closePaymentAfterIo(ownedOrder(orderId,buyer)));throw failure;}
        var result=tx.execute(status -> {
            Order order=ownedOrder(draft.getOrderId(),buyer);
            if(!com.mercato.Commerce.CheckoutPayability.payable(order)) {
                cashfreeService.closePaymentAfterIo(order);
                return null;
            }
            if(order.getPayment().getCapturedAt()==null && (order.getPayment().getPaymentSessionId()==null || order.getPayment().getPaymentSessionId().isBlank()))
                cashfreeService.initiatePayment(order,PaymentMethod.UNKNOWN,gateway);
            return placement(order);
        });
        if(result==null)throw new CustomBadRequestException("Order changed while opening payment. Review its current status.");
        return result;
    }

    private void requirePayable(Order order) {
        if(!com.mercato.Commerce.CheckoutPayability.payable(order))
            throw new CustomBadRequestException("Order is no longer payable. Review its current status.");
    }

    private Order ownedOrder(String orderId,EcommUser buyer) {
        var order=orderRepository.lockByOrderId(orderId).orElseThrow();
        if(!buyer.getUserId().equals(order.getCustomerUserId()))throw new ResourceNotFoundException("Order","orderId",orderId);
        return order;
    }
    private OrderPlacementResponseDTO placement(Order order){return new OrderPlacementResponseDTO(order.getPayment().getPaymentSessionId(),detail(order,TransitionTrigger.CUSTOMER));}
    private String existingIntent(EcommUser user,OrderCaptureRequestDTO request,String body) {
        var prior=orderRepository.checkoutIntent(user.getUserId(),request.getOperationId());
        if(prior.isEmpty())return null;
        if(!body.equals(prior.get(0)[1]))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Checkout operationId was used for a different request");
        return (String)prior.get(0)[0];
    }
    private String prepareOrder(OrderCaptureRequestDTO request,EcommUser user) {
        String body=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Arrays.asList(request.getAddressId(),
            request.getExpectedTotal()==null?null:request.getExpectedTotal().stripTrailingZeros().toPlainString(),request.getDeliveryMethod(),request.getExpectedCartVersion())).toString();
        String prior=existingIntent(user,request,body);if(prior!=null)return prior;
        Cart cart = cartService.getCartByUser(user);
        // Recheck after waiting for the cart mutex; scalar queries take no order lock.
        prior=existingIntent(user,request,body);if(prior!=null)return prior;

        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CustomBadRequestException("Cart is empty");
        }
        if(request.getExpectedCartVersion()!=cart.getVersion())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Your bag changed or this quote already opened a checkout. Refresh your bag.");
        long quantity=0;
        for(var item:cart.getCartItems()) {
            if(item.getQuantity()<=0)throw new CustomBadRequestException("Bag quantities must be positive");
            quantity=Math.addExact(quantity,(long)item.getQuantity());
            if(quantity>MAX_CHECKOUT_UNITS)throw new CustomBadRequestException("A checkout supports at most 10000 total units. Reduce quantities before paying.");
        }
        if ((request.getExpectedTotal() != null && request.getExpectedTotal().compareTo(cart.getTotal()) != 0)
                || (request.getDeliveryMethod() != null && request.getDeliveryMethod() != cart.getDeliveryMethod()))
            throw new CustomBadRequestException("Your bag or delivery price changed. Refresh your bag and review the latest total before paying.");

        // A saved bag outlives its temporary holds. Reacquire them atomically before
        // persisting a payable order; a genuine shortage rolls this transaction back.
        cart.getCartItems().stream().sorted(Comparator.comparing(item -> item.getProduct().getProductId())).forEach(item -> {
            if (!item.getProduct().isPurchasable()) throw new CustomBadRequestException("An item in your cart is no longer available");
            cartReservationService.reserve(item);
        });

        Address address = addressRepository.findByAddressIdAndUser_UserId(
                        request.getAddressId(), user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address", "addressId", request.getAddressId())
                );
        Order order = buildOrder(cart, user, address);
        order.setCheckoutOperationId(request.getOperationId());order.setCheckoutRequestBody(body);

        // Commit a local payable aggregate before external I/O. Repeated gateway creation uses
        // this immutable merchant order identity; callbacks can always find the payment.
        Payment payment=new Payment();payment.setAmount(order.getTotalAmount());payment.setCurrency(order.getCurrency());
        payment.setPaymentMethod(PaymentMethod.UNKNOWN);payment.setStatus(PaymentStatus.INITIATED);payment.setGatewayName("cashfree");payment.setInitiatedAt(Instant.now());
        order.attachPayment(payment);order.setPaymentStatus(PaymentStatus.INITIATED);
        orderRepository.saveAndFlush(order);cart.advanceVersion();
        return order.getOrderId();
    }

    @Override
    public String retryPayment(String orderId) {
        EcommUser currentUser = authUtil.getLoggedInUser();
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        Order order = tx.execute(status -> {
          Order found = orderRepository.lockByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        if(!currentUser.getEmail().equals(found.getCustomerEmail()))throw new ResourceNotFoundException("Order", "orderId", orderId);
        requirePayable(found);
        orderRetryHoldService.ensureCartHoldsForRetry(found, currentUser);
        // Gateway I/O runs after commit with OSIV disabled. Resolve the only lazy
        // association needed by the detached payment snapshot before leaving here.
        org.hibernate.Hibernate.initialize(found.getPayment());
        return found;
        });
        return cashfreeService.retryPayment(order, currentUser);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(OrderCancelRequestDTO request, TransitionTrigger trigger) {

        // Lock before loading mutable state/lines. Locking an already managed entity
        // does not refresh a snapshot read before a concurrent allocation/shipment.
        Order order=orderRepository.lockByOrderId(request.getOrderId())
                .orElseThrow(()->new ResourceNotFoundException("Order","orderId",request.getOrderId()));
        if (trigger != TransitionTrigger.ADMIN) {
            EcommUser currentUser = authUtil.getLoggedInUser();
            if(!currentUser.getEmail().equals(order.getCustomerEmail()))
                throw new ResourceNotFoundException("Order", "orderId", request.getOrderId());
        }

        if (trigger == TransitionTrigger.SELLER || trigger == TransitionTrigger.SYSTEM)
            throw new CustomBadRequestException("Whole-order cancellation requires customer ownership or explicit admin scope");
        if (order.getOrderLines().isEmpty())
            throw new CustomBadRequestException("Order has no cancellable units");
        String identity=authUtil.getLoggedInUser().getEmail();
        String body=workflowCommands.encode(Arrays.asList("CANCEL_ORDER",request,trigger,identity));
        String stored=workflowCommands.stored(order,request.getOperationId(),request.getExpectedVersion(),body);
        if(stored!=null)return workflowCommands.decode(stored,OrderResponseDTO.class);
        int affected=order.getOrderLines().stream().mapToInt(OrderLine::getPendingQty).sum();
        var scope=new com.mercato.Payloads.Request.FulfillmentCancelRequest();scope.setOrderId(order.getOrderId());
        scope.setReason(request.getReason());scope.setOperationId("whole:"+UUID.nameUUIDFromBytes(request.getOperationId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));scope.setExpectedVersion(request.getExpectedVersion());
        if(trigger==TransitionTrigger.ADMIN) {
            var selections=new ArrayList<com.mercato.Payloads.Request.FulfillmentCancelRequest.Selection>();
            for(var line:order.getOrderLines())if(line.getPendingQty()>0){var unit=new com.mercato.Payloads.Request.FulfillmentCancelRequest.Selection();unit.setOrderLineNumber(line.getOrderLineNumber());unit.setQty(line.getPendingQty());selections.add(unit);}scope.setUnits(selections);
        }
        cancellation.cancel(scope,trigger);
        var outcome=detail(order,trigger);
        workflowCommands.recordScope(order,request.getOperationId(),body,trigger,identity,request.getReason(),affected,workflowCommands.encode(outcome));
        return outcome;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        return details(orderRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt","id")),TransitionTrigger.ADMIN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getCurrentUserOrders() {
        EcommUser currentUser = authUtil.getLoggedInUser();
        return details(orderRepository.findByCustomerEmailOrderByCreatedAtDescIdDesc(currentUser.getEmail()),TransitionTrigger.CUSTOMER);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getCurrentUserOrder(String orderId) {
        EcommUser currentUser = authUtil.getLoggedInUser();
        Order order = orderRepository.findByOrderIdAndCustomerEmail(orderId, currentUser.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        return detail(order,TransitionTrigger.CUSTOMER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryDTO> getCurrentUserOrderSummaries() {
        EcommUser currentUser = authUtil.getLoggedInUser();
        return orderRepository.findByCustomerEmailOrderByCreatedAtDescIdDesc(currentUser.getEmail()).stream().map(o->{var d=new OrderSummaryDTO(o.getOrderId(),o.getOrderStatus(),o.getPaymentStatus(),o.getTotalAmount(),o.getCreatedAt());d.setWorkflow(workflow.summarize(o));return d;}).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminOrderSummaryDTO> getAllOrderSummaries() {
        return orderRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt","id")).stream().map(o->{var d=new AdminOrderSummaryDTO(o.getOrderId(),o.getCustomerName(),o.getCustomerEmail(),o.getOrderStatus(),o.getTotalAmount(),o.getCreatedAt());d.setWorkflow(workflow.summarize(o));return d;}).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        return detail(order,TransitionTrigger.ADMIN);
    }


    private Order buildOrder(Cart cart, EcommUser customer, Address address) {
        List<CartItem> cartItems = cart.getCartItems();

        Order order = new Order();
        order.setOrderId(generateOrderId());
        order.setCustomerName(customer.getUsername());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerUserId(customer.getUserId());
        order.setOrderStatus(OrderStatus.CREATED);
        order.setRecipientName(address.getRecipientName());
        order.setRecipientPhone(address.getRecipientPhone());
        order.setDeliveryAddressLine1(address.getAddressLine1());
        order.setDeliveryAddressLine2(address.getAddressLine2());
        order.setDeliveryCity(address.getCity());
        order.setDeliveryState(address.getState());
        order.setDeliveryPincode(address.getPincode());
        order.setDeliveryMethod(cart.getDeliveryMethod());
        cart.getDeliveryOptions().stream().filter(option -> option.code() == cart.getDeliveryMethod())
                .findFirst().ifPresent(option -> order.setDeliveryEstimate(option.estimate()));

        Map<String, String> sellerFulfillmentIds = new HashMap<>();

        java.math.BigDecimal commissionRate = commissions.currentRate();
        int lineNumber = 1;
        for (CartItem cartItem : cartItems) {
            EcommUser seller = cartItem.getProduct().getSeller();
            sellerApplications.findByUser_UserId(seller.getUserId()).ifPresent(application -> {
                if ("ENROLLED_UNREGISTERED".equals(application.getTaxRegistrationType())
                        && !application.getPickupState().trim().equalsIgnoreCase(address.getState().trim()))
                    throw new CustomBadRequestException("This seller currently delivers only within " + application.getPickupState());
            });

            String fulfillmentId = sellerFulfillmentIds.computeIfAbsent(
                    seller.getEmail(), k -> generateFulfillmentId()
            );

            OrderLine orderLine = new OrderLine();
            orderLine.setFulfillmentId(fulfillmentId);
            orderLine.setOrderLineNumber(lineNumber++);
            orderLine.setProductId(cartItem.getProduct().getProductId());
            orderLine.setProductName(cartItem.getProduct().getProductName());
            orderLine.setUnitPrice(cartItem.getItemPrice());
            orderLine.setOrderedQty(cartItem.getQuantity());
            orderLine.setLineTotal(cartItem.getLineTotal());
            orderLine.setSellerName(seller.getSellerDisplayName());
            orderLine.setSellerEmail(seller.getEmail());
            orderLine.setCommissionRatePercent(commissionRate);
            orderLine.setCommissionCapPerItem(com.mercato.Utils.CommissionMath.CAP_PER_ITEM);
            orderLine.setSellerDeliveryCharge(cartItem.getSellerDeliveryCharge());
            orderLine.setSourceCartItemId(cartItem.getCartItemId());
            orderLine.setSourceSelectionGeneration(cartItem.getSelectionGeneration());
            orderLine.setOrderLineStatus(OrderLineStatus.CREATED);

            order.addOrderLine(orderLine);
        }

        order.setCurrency("INR");
        order.setSubtotal(cart.getSubtotal());
        order.setCharges(cart.getTotalCharges());
        order.setShippingCharge(cart.getChargeAmount(com.mercato.Entity.cart.ChargeType.SHIPPING));
        order.setPlatformFee(cart.getChargeAmount(com.mercato.Entity.cart.ChargeType.PLATFORM_FEE));
        order.setHandlingCharge(cart.getChargeAmount(com.mercato.Entity.cart.ChargeType.PROCESSING_AND_HANDLING));
        order.setTotalAmount(order.getSubtotal().add(order.getCharges()));
        com.mercato.Commerce.OrderMoneySnapshot.snapshot(order,cartItems);

        return order;
    }

    private OrderResponseDTO detail(Order order,TransitionTrigger actor) {
        var dto=OrderMapper.toDTO(order,workflow,actor);images.enrich(dto.getOrderLines());return dto;
    }
    private List<OrderResponseDTO> details(List<Order> orders,TransitionTrigger actor){
        var result=orders.stream().map(o->OrderMapper.toDTO(o,workflow,actor)).toList();
        images.enrich(result.stream().flatMap(o->o.getOrderLines().stream()).toList());return result;
    }

    private String generateOrderId() {
        String datePart = LocalDate.now().toString().replace("-", "");
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    private String generateFulfillmentId() {
        String datePart = LocalDate.now().toString().replace("-", "");
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        return "FUL-" + datePart + "-" + randomPart;
    }
}
