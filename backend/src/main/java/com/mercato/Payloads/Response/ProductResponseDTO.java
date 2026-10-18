package com.mercato.Payloads.Response;

import lombok.*;
import com.mercato.Entity.ProductSpecification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDTO {

    private String productId;
    private String productName;
    private String description;

    private String brand;
    private String productCode;
    private String materials;
    private String care;
    private String warranty;
    private String packageContents;
    private String deliveryReturns;
    @Builder.Default
    private List<String> highlights = new ArrayList<>();
    @Builder.Default
    private List<ProductSpecification> specifications = new ArrayList<>();

    private BigDecimal retailPrice;
    private BigDecimal discountPercent;
    private BigDecimal sellingPrice;

    private String primaryImageUrl;

    @Builder.Default
    private List<String> secondaryImageUrls = new ArrayList<>();

    @Builder.Default
    private List<String> allImages = new ArrayList<>();

    private String categoryId;
    private String categoryName;

    private String sellerId;
    private String sellerName;

    private Integer physicalQty;
    private Integer reservedQty;
    private Integer availableQty;

    private Boolean active;

    private Instant createdAt;
    private Instant updatedAt;
}
