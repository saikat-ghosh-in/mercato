package com.mercato.Payloads.Request;

import com.mercato.Configuration.AppConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ProductFilterRequestDTO {
    private Integer pageNumber = 0;
    private Integer pageSize = 24;
    private String sortBy = AppConstants.SORT_PRODUCTS_BY;
    private String sortingOrder = AppConstants.SORTING_ORDER;
    private String category;
    private String sellers;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private boolean inStock;

    public void validate() {
        if (pageNumber == null || pageNumber < 0)
            throw new IllegalArgumentException("Page number must be zero or greater");
        if (pageSize == null || pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0))
            throw new IllegalArgumentException("Prices cannot be negative");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price");
        if (keyword != null && keyword.length() > 150)
            throw new IllegalArgumentException("Search must be 150 characters or fewer");
        if (category != null && category.length() > 150)
            throw new IllegalArgumentException("Category must be 150 characters or fewer");
        if (sellers != null && sellers.length() > 2000)
            throw new IllegalArgumentException("Too many seller filters");
    }
}
