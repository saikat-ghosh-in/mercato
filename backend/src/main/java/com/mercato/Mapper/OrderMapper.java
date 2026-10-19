package com.mercato.Mapper;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Payloads.Response.OrderResponseDTO;

import java.util.Comparator;
import java.util.List;

public class OrderMapper {

    public static OrderResponseDTO toDTO(Order order) {
        List<OrderLineResponseDTO> orderLineDTOs = order.getOrderLines().stream()
                .sorted(Comparator.comparing(OrderLine::getOrderLineNumber))
                .map(OrderLineMapper::toDTO)
                .toList();

        return new OrderResponseDTO(
                order.getOrderId(),
                order.getOrderStatus().toString(),
                new OrderResponseDTO.Customer(
                        order.getCustomerName(),
                        order.getCustomerEmail()
                ),
                new OrderResponseDTO.DeliveryAddress(
                        order.getRecipientName(),
                        order.getRecipientPhone(),
                        order.getDeliveryAddressLine1(),
                        order.getDeliveryAddressLine2(),
                        order.getDeliveryCity(),
                        order.getDeliveryState(),
                        order.getDeliveryPincode()
                ),
                PaymentMapper.toDTO(order.getPayment()),
                RefundMapper.toDTO(order.getPayment().getRefund()),
                orderLineDTOs,
                order.getCurrency(),
                order.getSubtotal(),
                order.getCharges(),
                order.getTotalAmount(),
                order.isInventoryFinalizationFailed(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getDeliveryMethod(),
                order.getDeliveryEstimate()
        );
    }
}
