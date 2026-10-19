package com.mercato.Mapper;

import com.mercato.Entity.Product;
import com.mercato.Payloads.Request.ProductRequestDTO;
import com.mercato.Payloads.Response.ProductResponseDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ProductMapper {

    public Product toEntity(ProductRequestDTO dto) {
        Product product = Product.builder()
                .productName(dto.getProductName())
                .description(dto.getDescription())
                .retailPrice(dto.getRetailPrice())
                .discountPercent(dto.getDiscountPercent())
                .primaryImageUrl(dto.getPrimaryImageUrl())
                .secondaryImageUrls(dto.getSecondaryImageUrls() != null
                        ? new ArrayList<>(dto.getSecondaryImageUrls())
                        : new ArrayList<>())
                .physicalQty(dto.getPhysicalQty())
                .reservedQty(0)
                .active(dto.isActive())
                .build();
        updateDetails(product, dto);
        return product;
    }

    public ProductResponseDTO toDTO(Product product) {
        ProductResponseDTO dto = ProductResponseDTO.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .brand(product.getBrand()).productCode(product.getProductCode())
                .weightKg(product.getWeightKg()).lengthCm(product.getLengthCm()).widthCm(product.getWidthCm()).heightCm(product.getHeightCm())
                .materials(product.getMaterials()).care(product.getCare()).warranty(product.getWarranty())
                .packageContents(product.getPackageContents()).deliveryReturns(product.getDeliveryReturns())
                .highlights(new ArrayList<>(product.getHighlights()))
                .specifications(product.getSpecifications().stream().map(spec -> new com.mercato.Entity.ProductSpecification(spec.getLabel(), spec.getValue())).toList())
                .retailPrice(product.getRetailPrice())
                .discountPercent(product.getDiscountPercent())
                .sellingPrice(product.getSellingPrice())
                .primaryImageUrl(product.getPrimaryImageUrl())
                .secondaryImageUrls(product.getSecondaryImageUrls() != null
                        ? new ArrayList<>(product.getSecondaryImageUrls())
                        : new ArrayList<>())
                .allImages(product.getAllImages())
                .physicalQty(product.getPhysicalQty())
                .reservedQty(product.getReservedQty())
                .availableQty(product.getAvailableQty())
                .active(product.isActive())
                .listingStatus(product.getListingStatus() == null ? "APPROVED" : product.getListingStatus())
                .listingReviewNote(product.getListingReviewNote())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getCategoryId());
            dto.setCategoryName(product.getCategory().getCategoryName());
        }

        if (product.getSeller() != null) {
            dto.setSellerId(product.getSeller().getUserId());
            dto.setSellerName(product.getSeller().getSellerDisplayName() != null
                    ? product.getSeller().getSellerDisplayName()
                    : product.getSeller().getUsername());
        }

        return dto;
    }

    public void updateEntity(Product product, ProductRequestDTO dto) {
        product.setProductName(dto.getProductName());
        product.setDescription(dto.getDescription());
        product.setRetailPrice(dto.getRetailPrice());
        product.setDiscountPercent(dto.getDiscountPercent());
        product.setPrimaryImageUrl(dto.getPrimaryImageUrl());

        product.clearSecondaryImages();
        if (dto.getSecondaryImageUrls() != null) {
            dto.getSecondaryImageUrls().forEach(product::addSecondaryImage);
        }

        if (dto.getPhysicalQty() != null) product.setPhysicalQty(dto.getPhysicalQty());
        product.setActive(dto.isActive());
        updateDetails(product, dto);
    }
    private void updateDetails(Product product, ProductRequestDTO dto) {
        if (dto.getWeightKg() != null) {
            product.setWeightKg(dto.getWeightKg()); product.setLengthCm(dto.getLengthCm());
            product.setWidthCm(dto.getWidthCm()); product.setHeightCm(dto.getHeightCm());
        }
        if (dto.getBrand() != null) product.setBrand(dto.getBrand().trim());
        if (dto.getProductCode() != null) product.setProductCode(dto.getProductCode().trim());
        if (dto.getMaterials() != null) product.setMaterials(dto.getMaterials().trim());
        if (dto.getCare() != null) product.setCare(dto.getCare().trim());
        if (dto.getWarranty() != null) product.setWarranty(dto.getWarranty().trim());
        if (dto.getPackageContents() != null) product.setPackageContents(dto.getPackageContents().trim());
        if (dto.getDeliveryReturns() != null) product.setDeliveryReturns(dto.getDeliveryReturns().trim());
        if (dto.getHighlights() != null) {
            product.getHighlights().clear();
            dto.getHighlights().stream().map(String::trim).forEach(product.getHighlights()::add);
        }
        if (dto.getSpecifications() != null) {
            product.getSpecifications().clear();
            dto.getSpecifications().forEach(spec -> product.getSpecifications().add(new com.mercato.Entity.ProductSpecification(spec.getLabel().trim(), spec.getValue().trim())));
        }
    }
}
