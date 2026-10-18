package com.mercato.Controller;

import com.mercato.Payloads.Request.CartItemRequestDTO;
import com.mercato.Payloads.Request.CartRequestDTO;
import com.mercato.Payloads.Response.CartResponseDTO;
import com.mercato.Service.CartService;
import com.mercato.Utils.CartContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/cart/add")
    public ResponseEntity<CartResponseDTO> addProductToCart(HttpServletRequest request,
                                                            @Valid @RequestBody CartRequestDTO cartRequestDTO) {
        CartContext context = CartContext.resolve(request);
        CartResponseDTO cartResponseDTO = cartService.addProductToCart(cartRequestDTO, context);
        return new ResponseEntity<>(cartResponseDTO, HttpStatus.OK);
    }

    @GetMapping("/cart")
    public ResponseEntity<CartResponseDTO> getCart(HttpServletRequest request) {
        CartContext context = CartContext.resolve(request);
        CartResponseDTO cartResponseDTO = cartService.getCart(context);
        return new ResponseEntity<>(cartResponseDTO, cartResponseDTO == null ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @PutMapping("/cart")
    public ResponseEntity<CartResponseDTO> updateProductQuantityInCart(HttpServletRequest request,
                                                                       @Valid @RequestBody CartItemRequestDTO cartItemRequestDTO) {
        CartContext context = CartContext.resolve(request);
        CartResponseDTO cartResponseDTO = cartService.updateProductQuantityInCart(cartItemRequestDTO, context);
        return new ResponseEntity<>(cartResponseDTO, HttpStatus.OK);
    }

    @DeleteMapping("/cart/remove/{productId}")
    public ResponseEntity<String> deleteProductFromCart(HttpServletRequest request,
                                                        @PathVariable String productId) {
        CartContext context = CartContext.resolve(request);
        cartService.deleteProductFromCart(productId, context);
        return new ResponseEntity<>("Product removed from the cart.", HttpStatus.OK);
    }

    @DeleteMapping("/cart")
    public ResponseEntity<String> clearCart(HttpServletRequest request) {
        CartContext context = CartContext.resolve(request);
        cartService.clearCart(context);
        return new ResponseEntity<>("Cart has been cleared.", HttpStatus.OK);
    }

    @GetMapping("/admin/carts")
    public ResponseEntity<List<CartResponseDTO>> getAllCarts() {
        List<CartResponseDTO> cartResponseDTOList = cartService.getAllCarts();
        return new ResponseEntity<>(cartResponseDTOList, HttpStatus.OK);
    }

    @GetMapping("/admin/carts/{cartId}")
    public ResponseEntity<CartResponseDTO> getCartById(@PathVariable String cartId) {
        CartResponseDTO cartResponseDTO = cartService.getCartById(cartId);
        return new ResponseEntity<>(cartResponseDTO, HttpStatus.OK);
    }
}
