package com.mercato.Payloads.Request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import com.mercato.Entity.ProductSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ProductRequestDTO {

    @NotBlank(message = "Product name is required")
    @Size(min = 4, max = 100, message = "Product name must be between 4 and 100 characters")
    private String productName;

    @NotBlank(message = "Description is required")
    @Size(min = 7, max = 2000, message = "Description must be between 7 and 2000 characters")
    private String description;

    @Size(max = 100)
    private String brand;
    @Size(max = 100)
    private String productCode;
    @Size(max = 2000)
    private String materials;
    @Size(max = 2000)
    private String care;
    @Size(max = 2000)
    private String warranty;
    @Size(max = 2000)
    private String packageContents;
    @Size(max = 2000)
    private String deliveryReturns;
    @Size(max = 10)
    private List<@NotBlank @Size(max = 300) String> highlights;
    @Size(max = 30)
    private List<@NotNull @Valid ProductSpecification> specifications;

    @NotNull(message = "Retail price is required")
    @DecimalMin(value = "0.01", message = "Retail price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Invalid price format")
    private BigDecimal retailPrice;

    @DecimalMin(value = "0.00", message = "Discount must be at least 0%")
    @DecimalMax(value = "99.99", message = "Discount cannot exceed 99.99%")
    @Digits(integer = 2, fraction = 2, message = "Invalid discount format")
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @NotBlank(message = "Primary image is required")
    private String primaryImageUrl;

    @Size(max = 3, message = "Maximum 3 secondary images allowed")
    private List<String> secondaryImageUrls = new ArrayList<>();

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer physicalQty;

    private boolean active = true;


    public void validate() {
        if (primaryImageUrl == null || primaryImageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary image is required");
        }
        if (secondaryImageUrls != null && secondaryImageUrls.size() > 3) {
            throw new IllegalArgumentException("Maximum 3 secondary images allowed");
        }
        if (secondaryImageUrls != null) {
            secondaryImageUrls.removeIf(url -> url == null || url.trim().isEmpty());
        }
    }
}