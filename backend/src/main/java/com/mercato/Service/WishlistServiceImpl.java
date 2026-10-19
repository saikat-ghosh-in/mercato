package com.mercato.Service;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Entity.WishlistItem;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.ProductMapper;
import com.mercato.Payloads.Response.ProductResponseDTO;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.WishlistRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final AuthUtil authUtil;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getWishlist() {
        EcommUser user = authUtil.getLoggedInUser();
        return wishlistRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(WishlistItem::getProduct)
                .filter(Product::isPurchasable)
                .map(productMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getWishlistProductIds() {
        EcommUser user = authUtil.getLoggedInUser();
        return wishlistRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(item -> item.getProduct().getProductId())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void add(String productId) {
        EcommUser user = authUtil.getLoggedInUser();
        if (wishlistRepository.existsByUser_UserIdAndProduct_ProductId(user.getUserId(), productId)) {
            return;
        }

        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        if (!product.isPurchasable()) {
            throw new CustomBadRequestException("Product is not available");
        }

        wishlistRepository.save(WishlistItem.builder()
                .user(user)
                .product(product)
                .build());
    }

    @Override
    @Transactional
    public void remove(String productId) {
        EcommUser user = authUtil.getLoggedInUser();
        WishlistItem item = wishlistRepository
                .findByUser_UserIdAndProduct_ProductId(user.getUserId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("WishlistItem", "productId", productId));
        wishlistRepository.delete(item);
    }
}
