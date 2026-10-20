package com.mercato.Payloads.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminUserActivityDTO(boolean sellerAccount,String storeName,String sellerStatus,
        long orderCount,long productCount,long requestCount,long openRequestCount,
        CartSnapshot cart,PageBlock<?> results) {
    public record PageBlock<T>(List<T> content,int page,int totalPages,long totalElements) {}
    public record CartSnapshot(String cartId,Instant updatedAt,int units,BigDecimal subtotal,BigDecimal charges,BigDecimal total,String deliveryMethod) {}
    public record CartLine(String productId,String productName,int quantity,BigDecimal unitPrice,BigDecimal lineTotal,boolean available) {}
    public record ProductLine(String productId,String productName,String category,BigDecimal price,String status,boolean active,Instant createdAt) {}
}
