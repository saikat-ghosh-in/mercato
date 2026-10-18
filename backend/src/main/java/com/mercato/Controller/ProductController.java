package com.mercato.Controller;

import com.mercato.Payloads.Request.ProductFilterRequestDTO;
import com.mercato.Payloads.Request.ProductRequestDTO;
import com.mercato.Payloads.Request.ProductSupplyUpdateRequestDTO;
import com.mercato.Payloads.Request.SellerProductFilterRequestDTO;
import com.mercato.Payloads.Response.ProductResponseDTO;
import com.mercato.Payloads.Response.ProductResponse;
import com.mercato.Payloads.Response.ProductSupplyUpdateResponseDTO;
import com.mercato.Service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/seller/categories/{categoryId}/product")
    public ResponseEntity<ProductResponseDTO> addProduct(@Valid @RequestBody ProductRequestDTO productRequestDTO,
                                                         @PathVariable String categoryId) {
        ProductResponseDTO savedProductResponseDTO = productService.addProduct(categoryId, productRequestDTO);
        return new ResponseEntity<>(savedProductResponseDTO, HttpStatus.CREATED);
    }

    @GetMapping("/public/products")
    public ResponseEntity<ProductResponse> getActiveProducts(@ModelAttribute ProductFilterRequestDTO filter) {
        ProductResponse response = productService.getActiveProducts(filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/seller/products")
    public ResponseEntity<List<ProductResponseDTO>> getSellerProducts(@ModelAttribute SellerProductFilterRequestDTO filter) {
        List<ProductResponseDTO> sellerProducts = productService.getSellerProducts(filter);
        return new ResponseEntity<>(sellerProducts, HttpStatus.OK);
    }

    @GetMapping("/admin/products")
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts() {
        List<ProductResponseDTO> allProducts = productService.getAllProducts();
        return new ResponseEntity<>(allProducts, HttpStatus.OK);
    }

    @GetMapping("/public/products/{productId}")
    public ResponseEntity<ProductResponseDTO> getActiveProduct(@PathVariable String productId) {
        ProductResponseDTO productResponseDTO = productService.getActiveProduct(productId);
        return new ResponseEntity<>(productResponseDTO, HttpStatus.OK);
    }

    @GetMapping("/seller/products/{productId}")
    public ResponseEntity<ProductResponseDTO> getSellerProduct(@PathVariable String productId) {
        ProductResponseDTO productResponseDTO = productService.getSellerProduct(productId);
        return new ResponseEntity<>(productResponseDTO, HttpStatus.OK);
    }

    @GetMapping("/admin/products/{productId}")
    public ResponseEntity<ProductResponseDTO> getAdminProduct(@PathVariable String productId) {
        ProductResponseDTO productResponseDTO = productService.getAdminProduct(productId);
        return new ResponseEntity<>(productResponseDTO, HttpStatus.OK);
    }

    @PutMapping("/seller/products/{productId}")
    public ResponseEntity<ProductResponseDTO> updateProduct(@PathVariable String productId,
                                                            @RequestParam(required = false) String categoryId,
                                                            @Valid @RequestBody ProductRequestDTO productRequestDTO) {
        ProductResponseDTO updatedProductResponseDTO = productService.updateProduct(productId, categoryId, productRequestDTO);
        return new ResponseEntity<>(updatedProductResponseDTO, HttpStatus.OK);
    }

    @DeleteMapping("/seller/products/{productId}")
    public ResponseEntity<String> deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
        return new ResponseEntity<>("Product deleted successfully", HttpStatus.OK);
    }

    @PostMapping("/seller/products/supplyUpdate")
    public ResponseEntity<List<ProductSupplyUpdateResponseDTO>> productSupplyUpdate(@RequestBody List<ProductSupplyUpdateRequestDTO> productSupplyUpdateRequestDTOs) {
        List<ProductSupplyUpdateResponseDTO> productSupplyUpdateResponseDTOs = productService.updateProductInventory(productSupplyUpdateRequestDTOs);
        return new ResponseEntity<>(productSupplyUpdateResponseDTOs, HttpStatus.OK);
    }
}
