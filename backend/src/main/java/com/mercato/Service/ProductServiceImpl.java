package com.mercato.Service;

import com.mercato.Configuration.AppConstants;
import com.mercato.Entity.Category;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Entity.SupplyType;
import com.mercato.Entity.cart.CartReservation;
import com.mercato.Entity.fulfillment.OrderReservation;
import com.mercato.ExceptionHandler.ForbiddenOperationException;
import com.mercato.ExceptionHandler.ResourceAlreadyExistsException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.ProductMapper;
import com.mercato.Payloads.Request.ProductFilterRequestDTO;
import com.mercato.Payloads.Request.ProductRequestDTO;
import com.mercato.Payloads.Request.ProductSupplyUpdateRequestDTO;
import com.mercato.Payloads.Request.SellerProductFilterRequestDTO;
import com.mercato.Payloads.Response.ProductResponseDTO;
import com.mercato.Payloads.Response.ProductResponse;
import com.mercato.Payloads.Response.ProductSupplyUpdateResponseDTO;
import com.mercato.Repository.CartReservationRepository;
import com.mercato.Repository.CategoryRepository;
import com.mercato.Repository.OrderReservationRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuthUtil authUtil;
    private final ProductMapper productMapper;
    private final OrderReservationRepository orderReservationRepository;
    private final CartReservationRepository cartReservationRepository;
    private final FileService fileService;

    @Override
    @Transactional
    public ProductResponseDTO addProduct(String categoryId, ProductRequestDTO productRequestDTO) {
        productRequestDTO.validate();
        productRequestDTO.requirePackageMeasurements();

        Category category = getCategoryByCategoryId(categoryId);
        EcommUser seller = authUtil.getLoggedInUser();
        validateIfAlreadyExists(productRequestDTO.getProductName(), seller.getUserId());

        if (productRequestDTO.getPhysicalQty() == null) productRequestDTO.setPhysicalQty(0);

        Product product = productMapper.toEntity(productRequestDTO);
        product.setCategory(category);
        product.setSeller(seller);
        product.setListingStatus("PENDING");

        Product savedProduct = productRepository.save(product);
        return productMapper.toDTO(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getActiveProducts(ProductFilterRequestDTO filter) {
        filter.validate();
        Sort sort = buildSort(filter.getSortBy(), filter.getSortingOrder()).and(Sort.by("productId"));
        Specification<Product> spec = buildActiveSpec(filter);

        long totalElements = productRepository.count(spec);
        int totalPages = (int) Math.ceil((double) totalElements / filter.getPageSize());

        int pageNumber = filter.getPageNumber();
        if (totalPages == 0) {
            pageNumber = 0;
        } else if (pageNumber >= totalPages) {
            pageNumber = totalPages - 1;
        }

        Pageable pageable = PageRequest.of(pageNumber, filter.getPageSize(), sort);
        Page<Product> productsPage = productRepository.findAll(spec, pageable);

        return new ProductResponse(
                productsPage.getContent().stream().map(productMapper::toDTO).toList(),
                productsPage.getNumber(),
                productsPage.getSize(),
                productsPage.getTotalElements(),
                productsPage.getTotalPages(),
                productsPage.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getActiveProduct(String productId) {
        if (productId == null)
            throw new IllegalArgumentException("productId must not be null!");
        Product product = productRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        if (!product.isPurchasable()) throw new ResourceNotFoundException("Product", "productId", productId);
        return productMapper.toDTO(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getSellerProducts(SellerProductFilterRequestDTO filter) {
        EcommUser seller = authUtil.getLoggedInUser();
        Sort sort = buildSort(filter.getSortBy(), filter.getSortingOrder());

        return productRepository.findAllBySellerUserId(seller.getUserId(), sort)
                .stream()
                .map(productMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getSellerProduct(String productId) {
        EcommUser seller = authUtil.getLoggedInUser();
        if (productId == null)
            throw new IllegalArgumentException("productId must not be null!");
        Product product = productRepository.findByProductIdAndSellerUserId(productId, seller.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return productMapper.toDTO(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(productMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getAdminProduct(String productId) {
        if (productId == null)
            throw new IllegalArgumentException("productId must not be null!");
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return productMapper.toDTO(product);
    }

    @Override
    @Transactional
    public ProductResponseDTO updateProduct(String productId, String categoryId,
                                            ProductRequestDTO productRequestDTO) {
        productRequestDTO.validate();

        EcommUser seller = authUtil.getLoggedInUser();
        Product product = getProductByIdForUpdate(productId);
        validateAuthority(product, seller);

        if (productRequestDTO.getProductName() != null
                && !productRequestDTO.getProductName().equals(product.getProductName())) {
            validateIfAlreadyExists(productRequestDTO.getProductName(), seller.getUserId());
        }

        if (StringUtils.isNotEmpty(categoryId)) {
            Category category = getCategoryByCategoryId(categoryId);
            product.setCategory(category);
        }

        if (productRequestDTO.getPhysicalQty() != null && productRequestDTO.getPhysicalQty() != product.getPhysicalQty()) {
            forceSetInventory(product, productRequestDTO.getPhysicalQty());
        }

        try {
            if (!productRequestDTO.getPrimaryImageUrl().equals(product.getPrimaryImageUrl())) {
                fileService.deleteImage(product.getPrimaryImageUrl());
            }

            if (product.getSecondaryImageUrls() != null) {
                for (String oldUrl : product.getSecondaryImageUrls()) {
                    if (productRequestDTO.getSecondaryImageUrls() == null || !productRequestDTO.getSecondaryImageUrls().contains(oldUrl)) {
                        fileService.deleteImage(oldUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete old images for product: {}", productId, e);
        }

        productMapper.updateEntity(product, productRequestDTO);
        if (!seller.isAdmin()) {
            product.setListingStatus("PENDING");
            product.setListingReviewNote(null);
            product.setListingReviewedAt(null);
            product.setListingReviewedBy(null);
        }

        Product savedProduct = productRepository.save(product);
        return productMapper.toDTO(savedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(String productId) {
        EcommUser seller = authUtil.getLoggedInUser();
        Product product = getProductByIdForUpdate(productId);
        validateAuthority(product, seller);

        try {
            fileService.deleteImage(product.getPrimaryImageUrl());
            if (product.getSecondaryImageUrls() != null) {
                for (String imageUrl : product.getSecondaryImageUrls()) {
                    fileService.deleteImage(imageUrl);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete images for product: {}", productId, e);
        }

        productRepository.delete(product);
    }

    @Override
    @Transactional
    public List<ProductSupplyUpdateResponseDTO> updateProductInventory(List<ProductSupplyUpdateRequestDTO> productSupplyUpdateRequestDTOs) {
        if (productSupplyUpdateRequestDTOs == null || productSupplyUpdateRequestDTOs.isEmpty() || productSupplyUpdateRequestDTOs.size() > 100)
            throw new IllegalArgumentException("Provide between 1 and 100 stock updates");
        for (ProductSupplyUpdateRequestDTO dto : productSupplyUpdateRequestDTOs) {
            if (dto == null || dto.getProductId() == null || dto.getProductId().isBlank()
                    || dto.getQuantity() == null || dto.getSupplyType() == null)
                throw new IllegalArgumentException("Each stock update requires a product, quantity and supply type");
        }
        List<ProductSupplyUpdateResponseDTO> responses = new ArrayList<>();
        EcommUser seller = authUtil.getLoggedInUser();

        productSupplyUpdateRequestDTOs.forEach(dto -> {
            boolean isError = false;
            String errorMessage = null;
            try {
                Product product = getProductByIdForUpdate(dto.getProductId());
                validateAuthority(product, seller);

                if (SupplyType.ABSOLUTE.equals(dto.getSupplyType())) {
                    forceSetInventory(product, dto.getQuantity());
                } else {
                    product.adjustInventory(dto.getQuantity());
                    productRepository.save(product);
                }
            } catch (Exception ex) {
                isError = true;
                errorMessage = ex.getMessage();
            } finally {
                responses.add(new ProductSupplyUpdateResponseDTO(
                        dto.getProductId(), dto.getSupplyType(),
                        dto.getQuantity(), isError, errorMessage));
            }
        });

        return responses;
    }

    private Specification<Product> buildActiveSpec(ProductFilterRequestDTO filter) {
        Specification<Product> activeOnly = (root, query, cb) -> cb.and(cb.isTrue(root.get("active")),
                cb.or(cb.isNull(root.get("listingStatus")), cb.equal(root.get("listingStatus"), "APPROVED")),
                cb.isTrue(root.get("seller").get("enabled")), cb.isFalse(root.get("seller").get("accountLocked")),
                cb.or(cb.isNull(root.get("seller").get("sellerStatus")), cb.equal(root.get("seller").get("sellerStatus"), com.mercato.Entity.SellerStatus.APPROVED)));
        Specification<Product> filters = buildSpec(
                filter.getCategory(), filter.getSellers(),
                filter.getKeyword(), filter.getMinPrice(), filter.getMaxPrice(), filter.isInStock()
        );
        return activeOnly.and(filters);
    }

    @Transactional
    private void forceSetInventory(Product product, int newPhysicalQty) {
        if (newPhysicalQty < 0)
            throw new IllegalArgumentException("Physical qty cannot be negative");

        int paidReservedQty = orderReservationRepository
                .findAllByProductIdOrderByCreatedAtDesc(product.getProductId()).stream()
                .mapToInt(OrderReservation::getReservedQty).sum();
        if (newPhysicalQty < paidReservedQty)
            throw new IllegalArgumentException("Stock cannot be reduced below quantities reserved for paid orders");

        int excess = product.getReservedQty() - newPhysicalQty;
        if (excess > 0) excess = releaseCartReservations(product, excess);
        if (excess > 0)
            throw new IllegalArgumentException("Stock cannot be reduced while inventory reservations are inconsistent");

        product.setInventoryAbsolute(newPhysicalQty);
        productRepository.save(product);
    }

    private int releaseCartReservations(Product product, int excess) {
        List<CartReservation> cartReservations = cartReservationRepository
                .findAllByProductIdOrderByCreatedAtDesc(product.getProductId());
        if (cartReservations.stream().mapToInt(CartReservation::getReservedQty).sum() < excess)
            throw new IllegalArgumentException("Inventory reservations are inconsistent; stock correction was not applied");

        for (CartReservation reservation : cartReservations) {
            if (excess <= 0) break;
            int toRelease = Math.min(reservation.getReservedQty(), excess);
            product.adjustReservedQty(-toRelease);
            excess -= toRelease;

            if (toRelease == reservation.getReservedQty()) {
                cartReservationRepository.delete(reservation);
            } else {
                reservation.updateReservedQuantity(reservation.getReservedQty() - toRelease);
                cartReservationRepository.save(reservation);
            }
        }
        return excess;
    }

    @Transactional
    private Product getProductByIdForUpdate(String productId) {
        if (productId == null)
            throw new IllegalArgumentException("productId must not be null!");
        return productRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
    }

    public Category getCategoryByCategoryId(String categoryId) {
        if (categoryId == null)
            throw new IllegalArgumentException("categoryId must not be null");
        return categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "categoryId", categoryId));
    }

    private void validateIfAlreadyExists(String productName, String sellerId) {
        if (productRepository.existsByProductNameAndSellerUserId(productName, sellerId))
            throw new ResourceAlreadyExistsException("Product", "productName", productName);
    }

    private void validateAuthority(Product product, EcommUser user) {
        if (!user.isAdmin() && !user.getUserId().equals(product.getSeller().getUserId()))
            throw new ForbiddenOperationException("You are not authorized to perform this action.");
    }

    private Sort buildSort(String sortBy, String sortingOrder) {
        String validSortBy = isSortByAllowed(sortBy) ? sortBy : AppConstants.SORT_PRODUCTS_BY;
        return "desc".equalsIgnoreCase(sortingOrder)
                ? Sort.by(validSortBy).descending()
                : Sort.by(validSortBy).ascending();
    }

    private Specification<Product> buildSpec(String category, String sellers,
                                             String keyword, BigDecimal minPrice,
                                             BigDecimal maxPrice, boolean inStock) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.isNotEmpty(category)) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("category").get("categoryName"), category));
        }

        if (StringUtils.isNotEmpty(sellers)) {
            List<String> sellerList = Arrays.asList(sellers.split(","));
            spec = spec.and((root, query, cb) ->
                    root.get("seller").get("sellerDisplayName").in(sellerList));
        }

        if (StringUtils.isNotEmpty(keyword)) {
            spec = spec.and((root, query, cb) -> {
                String pattern = "%" + keyword.toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(root.get("productName")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                );
            });
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("sellingPrice"), minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("sellingPrice"), maxPrice));
        }

        if (inStock) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThan(cb.diff(root.get("physicalQty"), root.get("reservedQty")), 0));
        }

        return spec;
    }

    private boolean isSortByAllowed(String sortBy) {
        return AppConstants.ALLOWED_SORT_PRODUCT_FIELDS.contains(sortBy);
    }
}
