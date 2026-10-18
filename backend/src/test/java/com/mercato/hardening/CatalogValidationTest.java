package com.mercato.hardening;

import com.mercato.Payloads.Request.ProductFilterRequestDTO;
import com.mercato.Service.ProductServiceImpl;
import com.mercato.Repository.*;
import com.mercato.Mapper.ProductMapper;
import com.mercato.Service.FileService;
import com.mercato.Utils.AuthUtil;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogValidationTest {
    @Test
    void invalidPagingIsRejectedBeforeQueryingTheDatabase() {
        ProductRepository products = mock(ProductRepository.class);
        ProductServiceImpl service = new ProductServiceImpl(products, mock(CategoryRepository.class), mock(AuthUtil.class), new ProductMapper(), mock(OrderReservationRepository.class), mock(CartReservationRepository.class), mock(FileService.class));
        ProductFilterRequestDTO filter = new ProductFilterRequestDTO();
        filter.setPageSize(1000000);
        assertThrows(IllegalArgumentException.class, () -> service.getActiveProducts(filter));
        filter.setPageSize(0);
        assertThrows(IllegalArgumentException.class, () -> service.getActiveProducts(filter));
        filter.setPageSize(12);
        filter.setPageNumber(-1);
        assertThrows(IllegalArgumentException.class, () -> service.getActiveProducts(filter));
        verifyNoInteractions(products);
    }

    @Test
    void invertedAndNegativePricesAreRejected() {
        ProductFilterRequestDTO filter = new ProductFilterRequestDTO();
        filter.setMinPrice(BigDecimal.TEN);
        filter.setMaxPrice(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, filter::validate);
        filter.setMinPrice(BigDecimal.valueOf(-1));
        assertThrows(IllegalArgumentException.class, filter::validate);
        filter.setMinPrice(BigDecimal.ZERO);
        filter.setMaxPrice(BigDecimal.ZERO);
        assertDoesNotThrow(filter::validate);
    }
}
