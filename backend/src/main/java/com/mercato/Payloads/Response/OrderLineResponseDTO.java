package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@lombok.Setter
@lombok.NoArgsConstructor
@AllArgsConstructor
public class OrderLineResponseDTO {
    private String fulfillmentId;
    private String orderId;
    private int orderLineNumber;
    private String orderLineStatus;

    private ProductDetails product;
    private Seller seller;

    private int orderedQty;
    private int acceptedQty;
    private int shippedQty;
    private int cancelledQty;
    private int pendingQty;

    private BigDecimal lineTotal;
    private List<StateTransitionResponseDTO> stateTransitions;

    private Instant createdAt;
    private Instant updatedAt;

    @lombok.Setter
    private com.mercato.Service.FulfillmentWorkflow.Projection workflow;

    @Getter
    @lombok.NoArgsConstructor
    @lombok.Setter
    @AllArgsConstructor
    public static class ProductDetails {
        private String productId;
        private String productName;
        private BigDecimal unitPrice;
        private String primaryImageUrl;
        public ProductDetails(String id,String name,BigDecimal price){this(id,name,price,null);}
    }

    @Getter
    @lombok.NoArgsConstructor
    @lombok.Setter
    @AllArgsConstructor
    public static class Seller {
        private String name;
        private String email;
    }
}
