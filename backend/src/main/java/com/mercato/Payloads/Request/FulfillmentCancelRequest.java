package com.mercato.Payloads.Request;
import lombok.Data;
import java.util.List;
@Data
public class FulfillmentCancelRequest {
    private String orderId;
    private String fulfillmentId;
    private List<Selection> units;
    private String operationId;
    private Long expectedVersion;
    private String reason;
    @Data public static class Selection { private int orderLineNumber; private int qty; }
}
