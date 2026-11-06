package com.mercato.Controller;

import com.mercato.Service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductReviewController {
    private final ProductReviewService reviews;

    @GetMapping("/public/products/{productId}/reviews")
    public ProductReviewService.ProductReviews forProduct(@PathVariable String productId,
                                                           @RequestParam(defaultValue = "0") int page) {
        return reviews.forProduct(productId, page);
    }

    @GetMapping("/user/orders/{orderId}/reviews")
    public Map<Integer, ProductReviewService.ReviewStatus> forOrder(@PathVariable String orderId) {
        return reviews.statusForOrder(orderId);
    }

    @PostMapping(value = "/user/orders/{orderId}/lines/{lineNumber}/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewService.Review submit(@PathVariable String orderId, @PathVariable int lineNumber,
                                               @RequestParam int rating, @RequestParam(defaultValue = "") String text,
                                               @RequestParam(name = "photos", required = false) List<MultipartFile> photos,
                                               @RequestParam(name = "video", required = false) List<MultipartFile> videos) {
        return reviews.submit(orderId, lineNumber, rating, text,
                photos == null ? List.of() : photos, videos == null ? List.of() : videos);
    }
}
