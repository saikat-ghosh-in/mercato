package com.mercato.hardening;

import com.mercato.Controller.CartController;
import com.mercato.Controller.ProductController;
import com.mercato.ExceptionHandler.GlobalExceptionHandler;
import com.mercato.Service.CartService;
import com.mercato.Service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiBoundaryTest {
    @Test
    void malformedCatalogParametersReturnBadRequest() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/public/products?pageNumber=banana")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedErrorsDoNotExposeInternalDetails() throws Exception {
        ProductService service = mock(ProductService.class);
        when(service.getActiveProducts(any())).thenThrow(new IllegalStateException("jdbc:postgresql://private-host password=secret"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/public/products")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Something went wrong. Please try again later."));
    }

    @Test
    void invalidCartBodiesNeverReachTheService() throws Exception {
        CartService service = mock(CartService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CartController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content("{\"cartItems\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content("{\"cartItems\":[null]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":-2}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
