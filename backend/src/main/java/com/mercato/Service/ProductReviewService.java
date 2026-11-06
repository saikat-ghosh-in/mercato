package com.mercato.Service;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Entity.ProductReview;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Repository.OrderRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.ProductReviewRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductReviewService {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final ProductReviewRepository reviews;
    private final ReviewMediaStorage media;
    private final AuthUtil auth;

    public enum ReviewStatus { AVAILABLE, SUBMITTED }

    public record Review(long id, int rating, String text, List<String> photos, String video, Instant createdAt) {
        static Review from(ProductReview review) {
            return new Review(review.getId(), review.getRating(), review.getReviewText(),
                    List.copyOf(review.getPhotoUrls()), review.getVideoUrl(), review.getCreatedAt());
        }
    }

    public record ProductReviews(long count, Double average, List<Review> reviews, boolean hasMore) {}

    @Transactional(readOnly = true)
    public ProductReviews forProduct(String productId, int page) {
        if (page < 0 || page > Integer.MAX_VALUE / 10) throw new IllegalArgumentException("Invalid review page.");
        products.findByProductIdAndActiveTrue(productId).filter(Product::isPurchasable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        var summary = reviews.summarize(productId);
        var entries = reviews.findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(page, 10));
        return new ProductReviews(summary.getCount(), summary.getAverage(),
                entries.getContent().stream().map(Review::from).toList(), entries.hasNext());
    }

    @Transactional(readOnly = true)
    public Map<Integer, ReviewStatus> statusForOrder(String orderId) {
        EcommUser buyer = buyer();
        var delivered = ownedOrder(orderId, buyer).getOrderLines().stream().filter(this::wasDelivered).toList();
        if (delivered.isEmpty()) return Map.of();
        var productIds = delivered.stream().map(OrderLine::getProductId).distinct().toList();
        var reviewed = new HashSet<>(reviews.findReviewedProducts(buyer.getUserId(), productIds));
        Map<Integer, ReviewStatus> statuses = new LinkedHashMap<>();
        for (OrderLine line : delivered) {
            statuses.put(line.getOrderLineNumber(), reviewed.contains(line.getProductId()) ? ReviewStatus.SUBMITTED : ReviewStatus.AVAILABLE);
        }
        return statuses;
    }

    @Transactional
    public Review submit(String orderId, int lineNumber, int rating, String text,
                         List<MultipartFile> photos, List<MultipartFile> videos) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Choose a rating from 1 to 5 stars.");
        String comment = text.strip();
        if (comment.length() > 2000) throw new IllegalArgumentException("Keep your review to 2,000 characters or fewer.");

        EcommUser buyer = buyer();
        OrderLine line = ownedOrder(orderId, buyer).getOrderLines().stream()
                .filter(item -> item.getOrderLineNumber() == lineNumber).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found."));
        if (!wasDelivered(line)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can review this item once it has been delivered.");
        if (reviews.existsByProductIdAndBuyerId(line.getProductId(), buyer.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product.");
        }

        var attachments = media.store(photos, videos);
        var review = new ProductReview(line.getProductId(), buyer.getUserId(), line.getId(), rating, comment,
                attachments.photoUrls(), attachments.videoUrl(), Instant.now());
        return Review.from(reviews.saveAndFlush(review));
    }

    private boolean wasDelivered(OrderLine line) {
        return line.getDeliveredQty() > 0;
    }

    private EcommUser buyer() {
        EcommUser buyer = auth.getLoggedInUser();
        if (buyer == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to review an order item.");
        return buyer;
    }

    private Order ownedOrder(String orderId, EcommUser buyer) {
        return orders.findOwnedForShipment(orderId, buyer.getUserId(), buyer.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
    }
}
