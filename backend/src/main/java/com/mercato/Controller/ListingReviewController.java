package com.mercato.Controller;

import com.mercato.Mapper.ProductMapper;
import com.mercato.Repository.ProductRepository;
import com.mercato.Utils.AuthUtil;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequiredArgsConstructor
public class ListingReviewController {
    private final ProductRepository products;
    private final AuthUtil auth;
    private final ProductMapper mapper;
    public record Review(boolean approve, @NotBlank @Size(min=10,max=1000) String note) {}
    @PostMapping("/api/admin/products/{id}/review") @Transactional
    public Object review(@PathVariable String id, @Valid @RequestBody Review request) {
        var admin = auth.getLoggedInUser();
        if (!admin.isAdmin()) throw new AccessDeniedException("Admin access required");
        var product = products.findByProductIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        if (!"PENDING".equals(product.getListingStatus())) throw new IllegalArgumentException("Only pending listings can be reviewed");
        var seller=product.getSeller();
        if (request.approve() && (seller==null || !seller.isEnabled() || seller.isAccountLocked() || (seller.getSellerStatus()!=null && seller.getSellerStatus()!=com.mercato.Entity.SellerStatus.APPROVED))) throw new IllegalArgumentException("The seller must be approved and active before publishing a listing");
        product.setListingStatus(request.approve() ? "APPROVED" : "REJECTED");
        product.setListingReviewNote(request.note().trim());
        product.setListingReviewedAt(Instant.now()); product.setListingReviewedBy(admin.getUserId());
        return mapper.toDTO(product);
    }
}
