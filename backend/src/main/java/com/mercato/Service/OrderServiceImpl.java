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

    private final OrderRepository orderRepository;
    private final CartService cartService;
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
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        Order draft = tx.execute(status -> prepareOrder(request));
        EcommUser buyer = authUtil.getLoggedInUser();
        CashfreeOrderResponse gateway = cashfreeService.createOrder(draft, buyer);
        return tx.execute(status -> {
            Order order=orderRepository.lockByOrderId(draft.getOrderId()).orElseThrow();
            if(order.getOrderStatus()==OrderStatus.CANCELLED) {
                cashfreeService.terminateOrder(order.getOrderId());
                throw new CustomBadRequestException("Order was cancelled while opening payment");
            }
            if(order.getPayment().getCapturedAt()==null)cashfreeService.initiatePayment(order,PaymentMethod.UNKNOWN,gateway);
            return new OrderPlacementResponseDTO(order.getPayment().getPaymentSessionId(),OrderMapper.toDTO(order));
        });
    }

    private Order prepareOrder(OrderCaptureRequestDTO request) {
        EcommUser user = authUtil.getLoggedInUser();
        Cart cart = cartService.getCartByUser(user);

        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CustomBadRequestException("Cart is empty");
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

        // Commit a local payable aggregate before external I/O. Repeated gateway creation uses
        // this immutable merchant order identity; callbacks can always find the payment.
        Payment payment=new Payment();payment.setAmount(order.getTotalAmount());payment.setCurrency(order.getCurrency());
        payment.setPaymentMethod(PaymentMethod.UNKNOWN);payment.setStatus(PaymentStatus.INITIATED);payment.setGatewayName("cashfree");payment.setInitiatedAt(Instant.now());
        order.attachPayment(payment);order.setPaymentStatus(PaymentStatus.INITIATED);
        return orderRepository.saveAndFlush(order);
    }

    @Override
    public String retryPayment(String orderId) {
        EcommUser currentUser = authUtil.getLoggedInUser();
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        Order order = tx.execute(status -> {
          Order found = orderRepository.lockByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        if(!currentUser.getEmail().equals(found.getCustomerEmail()))throw new ResourceNotFoundException("Order", "orderId", orderId);
        if (!OrderStatus.CREATED.equals(found.getOrderStatus())) {
            throw new CustomBadRequestException("Order is not in a payable state");
        }
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

        Payment payment = order.getPayment();

        if (order.getOrderStatus() == OrderStatus.CANCELLED
                || order.getOrderStatus() == OrderStatus.FULFILLMENT_COMPLETE) {
            throw new CustomBadRequestException("Order is already closed and cannot be cancelled");
        }
        if (order.getOrderStatus() != OrderStatus.CREATED
                && order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new CustomBadRequestException(
                    "Order cannot be cancelled at this stage"
            );
        }

        if (trigger != TransitionTrigger.ADMIN
                && order.getOrderStatus() == OrderStatus.CONFIRMED) {
            Instant confirmedAt = payment.getCompletedAt();
            if (confirmedAt == null) {
                throw new CustomBadRequestException("Order confirmation time is unavailable");
            }
            if (Instant.now().isAfter(confirmedAt.plusSeconds(60 * 60))) {
                throw new CustomBadRequestException(
                        "Cancellation window has expired. Orders can only be cancelled within 1 hour of confirmation"
                );
            }
        }

        if (order.getOrderStatus() == OrderStatus.CREATED) {
            cashfreeService.terminateOrder(order.getOrderId());
            if(payment.getCapturedAt()==null)payment.setStatus(PaymentStatus.CANCELLED);
            payment.setGatewayResponseMessage("Cancelled before payment");
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.getOrderLines().forEach(line->{line.cancel(line.getPendingQty());line.setOrderLineStatus(line.deriveStatus());});
            if(payment.getCapturedAt()==null)order.setPaymentStatus(PaymentStatus.CANCELLED);
            else refundObligations.full(order,"Captured order cancelled before allocation");
            orderRepository.save(order);
            return OrderMapper.toDTO(order);
        }

        if (trigger != TransitionTrigger.ADMIN) {
            Instant confirmedAt = payment.getCompletedAt();
            if (confirmedAt == null) {
                throw new CustomBadRequestException("Order has not been confirmed yet");
            }
            if (Instant.now().isAfter(confirmedAt.plusSeconds(6 * 60 * 60))) {
                throw new CustomBadRequestException(
                        "Cancellation window has expired. Orders can only be cancelled within 6 hours of confirmation"
                );
            }
        }

        List<OrderLine> lines = new ArrayList<>(order.getOrderLines());
        cashfreeService.terminateOrder(order.getOrderId());
        lines.forEach(line -> {
            if (!line.isTerminal() && line.hasPendingQty()) {
                OrderLineUpdateRequestDTO lineRequest = new OrderLineUpdateRequestDTO();
                lineRequest.setFulfillmentId(line.getFulfillmentId());
                lineRequest.setOrderLineNumber(line.getOrderLineNumber());
                lineRequest.setAction(OrderLineAction.CANCEL);
                lineRequest.setQty(line.getPendingQty());
                lineRequest.setReason(request.getReason());
                orderLineUpdateService.updateOrderLine(lineRequest, trigger);
            }
        });

        return OrderMapper.toDTO(
                orderRepository.findByOrderId(request.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", request.getOrderId()))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getCurrentUserOrders() {
        EcommUser currentUser = authUtil.getLoggedInUser();
        return orderRepository.findByCustomerEmail(currentUser.getEmail())
                .stream()
                .map(OrderMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getCurrentUserOrder(String orderId) {
        EcommUser currentUser = authUtil.getLoggedInUser();
        Order order = orderRepository.findByOrderIdAndCustomerEmail(orderId, currentUser.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        return OrderMapper.toDTO(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryDTO> getCurrentUserOrderSummaries() {
        EcommUser currentUser = authUtil.getLoggedInUser();
        return orderRepository.findOrderSummariesByCustomerEmail(currentUser.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminOrderSummaryDTO> getAllOrderSummaries() {
        return orderRepository.findAllOrderSummaries();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        return OrderMapper.toDTO(order);
    }


    private Order buildOrder(Cart cart, EcommUser customer, Address address) {
        List<CartItem> cartItems = cart.getCartItems();

        Order order = new Order();
        order.setOrderId(generateOrderId());
        order.setCustomerName(customer.getUsername());
        order.setCustomerEmail(customer.getEmail());
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
            orderLine.setOrderLineStatus(OrderLineStatus.CREATED);

            order.addOrderLine(orderLine);
        }

        order.setCurrency("INR");
        order.setSubtotal(cart.getSubtotal());
        order.setCharges(cart.getTotalCharges());
        order.setShippingCharge(cart.getChargeAmount(com.mercato.Entity.cart.ChargeType.SHIPPING));
        order.setPlatformFee(cart.getChargeAmount(com.mercato.Entity.cart.ChargeType.PLATFORM_FEE));
        order.setTotalAmount(order.getSubtotal().add(order.getCharges()));

        return order;
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
