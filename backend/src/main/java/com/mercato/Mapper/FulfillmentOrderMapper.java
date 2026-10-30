package com.mercato.Mapper;

import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Payloads.Response.FulfillmentOrderResponseDTO;

import java.util.List;

public class FulfillmentOrderMapper {

    public static FulfillmentOrderResponseDTO toDTO(String id,List<OrderLine> lines,com.mercato.Service.FulfillmentWorkflow policy) {
        var dto=toDTO(id,lines);dto.setWorkflow(policy.summarize(lines.get(0).getOrder(),lines));dto.getOrderLines().forEach(d->d.setWorkflow(policy.project(lines.stream().filter(l->l.getOrderLineNumber()==d.getOrderLineNumber()).findFirst().orElseThrow(),com.mercato.Entity.fulfillment.TransitionTrigger.SELLER)));return dto;
    }
    public static FulfillmentOrderResponseDTO toDTO(
            String fulfillmentId,
            List<OrderLine> orderLines
    ) {
        OrderLine first = orderLines.get(0);

        return new FulfillmentOrderResponseDTO(
                fulfillmentId,
                first.getOrder().getOrderId(),
                new FulfillmentOrderResponseDTO.Customer(
                        first.getOrder().getCustomerName(),
                        first.getOrder().getCustomerEmail()
                ),
                new FulfillmentOrderResponseDTO.DeliveryAddress(
                        first.getOrder().getRecipientName(),
                        first.getOrder().getRecipientPhone(),
                        first.getOrder().getDeliveryAddressLine1(),
                        first.getOrder().getDeliveryAddressLine2(),
                        first.getOrder().getDeliveryCity(),
                        first.getOrder().getDeliveryState(),
                        first.getOrder().getDeliveryPincode()
                ),
                orderLines.stream().map(OrderLineMapper::toDTO).toList(),
                first.getOrder().getCreatedAt()
        );
    }
}
