package com.mercato.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Entity.fulfillment.OrderLineStatus;
import com.mercato.Entity.fulfillment.OrderStatus;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import com.mercato.Repository.OrderRepository;
import com.mercato.Repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class ProductReviewFlowTest {
    @TempDir static Path uploads;
    @Autowired MockMvc rawMvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("file.upload.dir", () -> uploads.toString().replace('\\', '/'));
    }

    @Test
    void onlyTheBuyerCanReviewDeliveredItemsAndPublicRatingsExcludePrivateData() throws Exception {
        var mvc = new CookieBrowserHarness(rawMvc, json);
        var shopper = users.findByUsername("shopperreview").orElseThrow();
        var other = users.findByUsername("sellerreview").orElseThrow();
        String buyerSession = mvc.login("shopperreview", "ShopOnly!2026");
        String otherSession = mvc.login("sellerreview", "SellerOnly!2026");
        var catalog = mvc.perform(get("/api/public/products")).andExpect(status().isOk()).andReturn();
        String product = json.readTree(catalog.getResponse().getContentAsString())
                .get("content").get(0).get("productId").asText();
        createOrder("REVIEW-PARTIAL", shopper, product, 1);
        createOrder("REVIEW-PENDING", shopper, product, 0);
        createOrder("REVIEW-REPEAT", shopper, product, 2);
        createOrder("REVIEW-OTHER", other, product, 2);

        String publicUrl = "/api/public/products/" + product + "/reviews";
        String reviewUrl = "/api/user/orders/REVIEW-PARTIAL/lines/1/review";
        String statusesUrl = "/api/user/orders/REVIEW-PARTIAL/reviews";
        mvc.perform(get(publicUrl)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get(statusesUrl)).andExpect(status().isUnauthorized());
        mvc.perform(get(statusesUrl).with(mvc.as(otherSession))).andExpect(status().isNotFound());
        mvc.perform(multipart(reviewUrl).param("rating", "5").with(mvc.as(otherSession)))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/user/orders/REVIEW-PENDING/lines/1/review").param("rating", "5").with(mvc.as(buyerSession)))
                .andExpect(status().isForbidden());
        mvc.perform(get(statusesUrl).with(mvc.as(buyerSession))).andExpect(jsonPath("$.1").value("AVAILABLE"));
        mvc.perform(multipart(reviewUrl).param("rating", "6").with(mvc.as(buyerSession)))
                .andExpect(status().isBadRequest());

        var photo = new MockMultipartFile("photos", "photo.png", "image/png", Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jv9sAAAAASUVORK5CYII="));
        var fakePhoto = new MockMultipartFile("photos", "photo.png", "image/png", new byte[]{1, 2, 3});
        var video = new MockMultipartFile("video", "clip.mp4", "video/mp4",
                new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0});
        mvc.perform(multipart(reviewUrl).file(photo).file(photo).file(photo).file(photo)
                .param("rating", "5").with(mvc.as(buyerSession))).andExpect(status().isBadRequest());
        mvc.perform(multipart(reviewUrl).file(video).file(video)
                .param("rating", "5").with(mvc.as(buyerSession))).andExpect(status().isBadRequest());
        mvc.perform(multipart(reviewUrl).file(fakePhoto).param("rating", "5").with(mvc.as(buyerSession)))
                .andExpect(status().isBadRequest());

        var submitted = mvc.perform(multipart(reviewUrl).file(photo).file(photo).file(photo).file(video)
                .param("rating", "5").param("text", "  Just what I needed.  ").with(mvc.as(buyerSession)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.photos.length()").value(3)).andReturn();
        var result = json.readTree(submitted.getResponse().getContentAsString());
        mvc.perform(get(result.get("photos").get(0).asText())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png")).andExpect(content().bytes(photo.getBytes()));
        mvc.perform(get(result.get("video").asText()).header("Range", "bytes=0-7"))
                .andExpect(status().isPartialContent()).andExpect(content().contentTypeCompatibleWith("video/mp4"))
                .andExpect(header().string("Content-Range", "bytes 0-7/16"));

        mvc.perform(get(statusesUrl).with(mvc.as(buyerSession))).andExpect(jsonPath("$.1").value("SUBMITTED"));
        mvc.perform(multipart("/api/user/orders/REVIEW-REPEAT/lines/1/review").file(photo)
                .param("rating", "4").with(mvc.as(buyerSession))).andExpect(status().isConflict());
        try (var files = Files.list(uploads.resolve("reviews"))) {
            assertEquals(4, files.count());
        }
        mvc.perform(multipart("/api/user/orders/REVIEW-OTHER/lines/1/review").param("rating", "2").with(mvc.as(otherSession)))
                .andExpect(status().isCreated());
        mvc.perform(get(publicUrl)).andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.average").value(3.5))
                .andExpect(jsonPath("$.reviews[0].rating").value(2))
                .andExpect(jsonPath("$.reviews[1].text").value("Just what I needed."))
                .andExpect(jsonPath("$.reviews[1].buyerId").doesNotExist())
                .andExpect(jsonPath("$.reviews[1].orderLineId").doesNotExist())
                .andExpect(jsonPath("$.reviews[1].photos.length()").value(3));
    }

    private void createOrder(String id, EcommUser buyer, String product, int delivered) {
        var order = new Order();
        order.setOrderId(id);
        order.setCustomerName("Test buyer");
        order.setCustomerUserId(buyer.getUserId());
        order.setCustomerEmail(buyer.getEmail());
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setRecipientName("Test buyer");
        order.setRecipientPhone("9000000000");
        order.setDeliveryAddressLine1("Synthetic address");
        order.setDeliveryCity("Bengaluru");
        order.setDeliveryState("Karnataka");
        order.setDeliveryPincode("560001");
        order.setCurrency("INR");
        order.setSubtotal(BigDecimal.valueOf(20));
        order.setCharges(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.valueOf(20));
        order.addOrderLine(OrderLine.builder()
                .fulfillmentId(id + "-F").orderLineNumber(1).productId(product).productName("Test item")
                .unitPrice(BigDecimal.TEN).lineTotal(BigDecimal.valueOf(20)).orderedQty(2)
                .acceptedQty(delivered).preparingQty(delivered).packedQty(delivered)
                .shippedQty(delivered).deliveredQty(delivered).orderLineStatus(OrderLineStatus.CONFIRMED).build());
        orders.saveAndFlush(order);
    }
}
