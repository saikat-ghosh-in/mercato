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

    @Override
    @Transactional
    public OrderPlacementResponseDTO placeOrder(OrderCaptureRequestDTO request) {
        EcommUser user = authUtil.getLoggedInUser();
        Cart cart = cartService.getCartByUser(user);

        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CustomBadRequestException("Cart is empty");
        }
        if ((request.getExpectedTotal() != null && request.getExpectedTotal().compareTo(cart.getTotal()) != 0)
                || (request.getDeliveryMethod() != null && request.getDeliveryMethod() != cart.getDeliveryMethod()))
            throw new CustomBadRequestException("Your bag or delivery price changed. Refresh your bag and review the latest total before paying.");

        cart.getCartItems().forEach(item -> {
            if (!item.getProduct().isPurchasable()) throw new CustomBadRequestException("An item in your cart is no longer available");
            cartReservationService.validateHeld(item);
        });

        Address address = addressRepository.findByAddressIdAndUser_UserId(
                        request.getAddressId(), user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address", "addressId", request.getAddressId())
                );
        Order order = buildOrder(cart, user, address);

        CashfreeOrderResponse cashfreeOrder = cashfreeService.createOrder(order, user);
        cashfreeService.initiatePayment(
                order,
                PaymentMethod.UNKNOWN,
                cashfreeOrder
        );

        orderRepository.save(order);

        return new OrderPlacementResponseDTO(
                order.getPayment().getPaymentSessionId(),
                OrderMapper.toDTO(order)
        );
    }

    @Override
    @Transactional
    public String retryPayment(String orderId) {
        EcommUser currentUser = authUtil.getLoggedInUser();
        Order order = orderRepository.findByOrderIdAndCustomerEmail(orderId, currentUser.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        if (!OrderStatus.CREATED.equals(order.getOrderStatus())) {
            throw new CustomBadRequestException("Order is not in a payable state");
        }

        orderRetryHoldService.ensureCartHoldsForRetry(order, currentUser);

        return cashfreeService.retryPayment(order, currentUser);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(OrderCancelRequestDTO request, TransitionTrigger trigger) {

        Order order;
        if (trigger == TransitionTrigger.ADMIN) {
            order = orderRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", request.getOrderId()));
        } else {
            EcommUser currentUser = authUtil.getLoggedInUser();
            order = orderRepository.findByOrderIdAndCustomerEmail(
                            request.getOrderId(), currentUser.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", request.getOrderId()));
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
            cashfreeService.terminateOrder(payment.getCfOrderId());
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setGatewayResponseMessage("Cancelled before payment");
            order.setOrderStatus(OrderStatus.CANCELLED);
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
