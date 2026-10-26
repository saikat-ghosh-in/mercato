package com.mercato.Mapper;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Payloads.Response.OrderLineResponseDTO;
import com.mercato.Payloads.Response.OrderResponseDTO;

import java.util.Comparator;
import java.util.List;

public class OrderMapper {

    public static OrderResponseDTO toDTO(Order order,com.mercato.Service.FulfillmentWorkflow policy,com.mercato.Entity.fulfillment.TransitionTrigger actor) {
        var dto=toDTO(order);dto.setWorkflow(policy.summarize(order));
        for(int i=0;i<dto.getOrderLines().size();i++) {
            var lineDto=dto.getOrderLines().get(i);
            var line=order.getOrderLines().stream().filter(l->l.getOrderLineNumber()==lineDto.getOrderLineNumber()).findFirst().orElseThrow();
            lineDto.setWorkflow(policy.project(line,actor));
        }
        return dto;
    }
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
