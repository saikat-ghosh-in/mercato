package com.mercato.Service;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.*;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.ExceptionHandler.ForbiddenOperationException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.OrderLineMapper;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Repository.OrderLineRepository;
import com.mercato.Repository.OrderRepository;
import com.mercato.Utils.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderLineUpdateServiceImpl implements OrderLineUpdateService {

    private final OrderLineRepository orderLineRepository;
    private final OrderRepository orderRepository;
    private final OrderReservationService orderReservationService;
    private final RefundService refundService;
    private final ShipmentNotificationService shipmentNotificationService;
    private final AuthUtil authUtil;

    @Override
    @Transactional
    @Validated
    public OrderLineResponseDTO updateOrderLine(@Valid OrderLineUpdateRequestDTO request, TransitionTrigger trigger) {
        orderRepository.lockByFulfillment(request.getFulfillmentId(),request.getOrderLineNumber())
            .orElseThrow(()->new ResourceNotFoundException("OrderLine","fulfillmentId",request.getFulfillmentId()));
        OrderLine orderLine = orderLineRepository
                .findByFulfillmentIdAndOrderLineNumberForUpdate(
                        request.getFulfillmentId(),
                        request.getOrderLineNumber()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OrderLine", "fulfillmentId", request.getFulfillmentId()
                ));
        OrderLineStatus lineStatus = orderLine.getOrderLineStatus();

        if (trigger == TransitionTrigger.SELLER) {
            EcommUser seller = authUtil.getLoggedInUser();
            if (!orderLine.getSellerEmail().equals(seller.getEmail())) {
                throw new ForbiddenOperationException(
                        "You are not authorized to update this order line"
                );
            }
        }

        if (trigger == TransitionTrigger.CUSTOMER) {
            EcommUser customer = authUtil.getLoggedInUser();
            if (!orderLine.getOrder().getCustomerEmail().equals(customer.getEmail())) {
                throw new ForbiddenOperationException(
                        "You are not authorized to update this order line"
                );
            }
            if (request.getAction() != OrderLineAction.CANCEL) {
                throw new CustomBadRequestException(
                        "Customers can only cancel order lines"
                );
            }

            if (lineStatus == OrderLineStatus.CONFIRMED) {
                Instant confirmedAt = orderLine.getOrder().getPayment().getCompletedAt();
                if (confirmedAt == null) {
                    throw new CustomBadRequestException("Order confirmation time is unavailable");
                }
                if (Instant.now().isAfter(confirmedAt.plusSeconds(60 * 60))) {
                    throw new CustomBadRequestException(
                            "Cancellation window has expired. Order lines can only be cancelled within 1 hour of confirmation"
                    );
                }
            } else if (lineStatus != OrderLineStatus.CREATED) {
                throw new CustomBadRequestException(
                        "Order line cannot be cancelled at this stage"
                );
            }
        }

        if (orderLine.isTerminal()) {
            throw new CustomBadRequestException(
                    "Order line is already in terminal status: " + orderLine.getOrderLineStatus()
            );
        }

        OrderLineStatus fromStatus = orderLine.getOrderLineStatus();
        int qty = request.getQty() != null ? request.getQty() : 0;

        switch (request.getAction()) {
            case ACCEPT -> handleAccept(orderLine, qty);
            case SHIP -> handleShip(orderLine, qty);
            case CANCEL -> handleCancel(orderLine, qty);
            default -> throw new IllegalArgumentException(
                    "Unknown action: " + request.getAction()
            );
        }

        OrderLineStatus toStatus;
        if (request.getAction() == OrderLineAction.ACCEPT) {
            toStatus = OrderLineStatus.PROCESSING;
        } else if (request.getAction() == OrderLineAction.CANCEL) {
            toStatus = orderLine.deriveStatus();
        } else {
            toStatus = orderLine.deriveStatus();
        }
        orderLine.setOrderLineStatus(toStatus);

        orderLine.addStateTransition(
                StateTransition.builder()
                        .orderLine(orderLine)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .action(request.getAction())
                        .triggeredBy(trigger)
                        .qtyAffected(request.getAction() == OrderLineAction.ACCEPT ? null : qty)
                        .reason(request.getReason())
                        .occurredAt(Instant.now())
                        .build()
        );

        syncOrderStatus(orderLine.getOrder());
        orderLineRepository.save(orderLine);

        if (request.getAction() == OrderLineAction.SHIP) {
            shipmentNotificationService.notifyShippedIfNeeded(orderLine.getOrder());
        }

        return OrderLineMapper.toDTO(orderLine);
    }

    private void handleAccept(OrderLine orderLine, int qty) {
        validateProcessingState(orderLine);
        int acceptQty = qty > 0 ? qty : orderLine.getOrderedQty();
        orderLine.accept(acceptQty);
    }

    private void handleShip(OrderLine orderLine, int qty) {
        Order order = orderLine.getOrder();
        if (order.getPaymentStatus() != com.mercato.Entity.fulfillment.payment.PaymentStatus.SUCCESS
                || order.getOrderStatus() == OrderStatus.CREATED || order.getOrderStatus() == OrderStatus.CANCELLED
                || order.isInventoryFinalizationFailed()
                || order.getAllocationCompletedAt()==null || order.getPayment()==null || order.getPayment().getCapturedAt()==null
                || (orderLine.getOrderLineStatus() != OrderLineStatus.PROCESSING
                    && orderLine.getOrderLineStatus() != OrderLineStatus.PARTIALLY_PROCESSED)
                || qty > orderLine.getAcceptedQty() - orderLine.getShippedQty()) {
            throw new CustomBadRequestException("Only paid, allocated and accepted units can be shipped");
        }
        validateQty(qty, "SHIP", orderLine);
        orderLine.ship(qty);
        orderReservationService.settleQty(orderLine, qty, OrderLineAction.SHIP);
    }

    private void handleCancel(OrderLine orderLine, int qty) {
        validateQty(qty, "CANCEL", orderLine);
        orderLine.cancel(qty);
        orderReservationService.settleQty(orderLine, qty, OrderLineAction.CANCEL);
    }

    private void validateProcessingState(OrderLine orderLine) {
        if (orderLine.getOrderLineStatus() != OrderLineStatus.CONFIRMED
                && orderLine.getOrderLineStatus() != OrderLineStatus.PROCESSING) {
            throw new CustomBadRequestException(
                    "ACCEPT action requires line to be in CONFIRMED or PROCESSING status"
            );
        }
    }

    private void validateQty(int qty, String action, OrderLine orderLine) {
        if (qty <= 0) {
            throw new CustomBadRequestException(
                    action + " action requires qty > 0"
            );
        }
        if (orderLine.getPendingQty() < qty) {
            throw new CustomBadRequestException(
                    MessageFormat.format("Cannot {0} {1} units, only {2} pending",
                            action, qty, orderLine.getPendingQty())
            );
        }
    }

    private void syncOrderStatus(Order order) {
        List<OrderLine> lines = order.getOrderLines();

        boolean allCancelled = lines.stream()
                .allMatch(l -> l.getOrderLineStatus() == OrderLineStatus.CANCELLED);

        boolean allClosed = lines.stream()
                .allMatch(l -> l.getOrderLineStatus() == OrderLineStatus.FULFILLED
                        || l.getOrderLineStatus() == OrderLineStatus.PARTIALLY_FULFILLED
                        || l.getOrderLineStatus() == OrderLineStatus.CANCELLED);

        boolean anyProcessing = lines.stream()
                .anyMatch(l -> (l.getOrderLineStatus() == OrderLineStatus.PROCESSING
                        || l.getOrderLineStatus() == OrderLineStatus.PARTIALLY_PROCESSED)
                        && l.getAcceptedQty() > 0);

        boolean allConfirmed = lines.stream()
                .allMatch(l -> l.getOrderLineStatus() == OrderLineStatus.CONFIRMED);

        if (allCancelled) {
            order.setOrderStatus(OrderStatus.CANCELLED);
        } else if (allClosed) {
            order.setOrderStatus(OrderStatus.FULFILLMENT_COMPLETE);
        } else if (anyProcessing) {
            order.setOrderStatus(OrderStatus.FULFILLMENT_PROCESSING);
        } else if (allConfirmed) {
            order.setOrderStatus(OrderStatus.CONFIRMED);
        }

        orderRepository.save(order);

        if (allCancelled || allClosed) {
            refundService.processRefundIfRequired(order);
        }
    }
}
