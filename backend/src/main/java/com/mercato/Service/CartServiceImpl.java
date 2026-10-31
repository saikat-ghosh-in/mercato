package com.mercato.Service;

import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.CartMapper;
import com.mercato.Payloads.Request.CartItemRequestDTO;
import com.mercato.Payloads.Request.CartRequestDTO;
import com.mercato.Payloads.Response.CartResponseDTO;
import com.mercato.Repository.CartRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Utils.CartContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartPricingService cartPricingService;
    private final UserRepository userRepository;
    private final CartReservationService cartReservationService;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional
    public CartResponseDTO addProductToCart(CartRequestDTO cartRequestDTO, CartContext context) {
        Cart cart = resolveCart(context);
        lockProducts(List.of(cart),cartRequestDTO.getCartItems().stream().map(CartItemRequestDTO::getProductId).toList());

        cartRequestDTO.getCartItems().stream().sorted(Comparator.comparing(CartItemRequestDTO::getProductId)).forEach(dto -> {
            String productId = dto.getProductId();
            Integer requestedQuantity = dto.getQuantity();
            if (requestedQuantity == null || requestedQuantity <= 0)
                throw new IllegalArgumentException("Quantity must be greater than 0");

            Product product = productRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", "productId", productId
                    ));
            if (!product.isPurchasable())
                throw new IllegalArgumentException("This product is no longer available");
            cart.addProduct(product, requestedQuantity);

            CartItem cartItem = cart.findItemByProductId(productId)
                    .orElseThrow(() -> new IllegalStateException("CartItem not found after add"));
            cartReservationService.reserve(cartItem);
        });

        cartPricingService.applyCharges(cart);
        cart.advanceVersion();
        cartRepository.save(cart);
        return CartMapper.toDTO(cart);
    }

    @Override
    @Transactional
    public CartResponseDTO getCart(CartContext context) {
        Cart cart = resolveCart(context);
        lockProducts(List.of(cart),List.of());reprice(cart);
        return CartMapper.toDTO(cart);
    }

    @Override
    @Transactional
    public CartResponseDTO selectDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod method, CartContext context) {
        if (method == null) throw new IllegalArgumentException("Choose an available delivery method");
        Cart cart = resolveCart(context);
        lockProducts(List.of(cart),List.of());
        cart.setDeliveryMethod(method);
        cartPricingService.applyCharges(cart);
        cart.advanceVersion();
        cartRepository.save(cart);
        return CartMapper.toDTO(cart);
    }

    @Override
    @Transactional
    public CartResponseDTO updateProductQuantityInCart(CartItemRequestDTO dto, CartContext context) {
        if (dto.getQuantity() == null || dto.getQuantity() < 0)
            throw new IllegalArgumentException("Quantity cannot be less than 0");

        Cart cart = resolveCart(context);
        lockProducts(List.of(cart),List.of(dto.getProductId()));
        CartItem cartItem = cart.findItemByProductId(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", dto.getProductId()));
        String warning = null;

        if (dto.getQuantity() == 0) {
            cartReservationService.release(cartItem);
            cart.removeCartItem(cartItem);
        } else {
            Product product = productRepository.findByProductIdForUpdate(dto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", dto.getProductId()));

            int delta = dto.getQuantity() - cartItem.getQuantity();
            int effectiveDelta = Math.min(delta, product.getAvailableQty());
            int effectiveQty = cartItem.getQuantity() + effectiveDelta;

            if (effectiveQty < dto.getQuantity()) {
                warning = String.format(
                        "Only %d unit%s available — your cart has been updated",
                        effectiveQty, effectiveQty != 1 ? "s" : ""
                );
            }

            cart.updateProductQuantity(dto.getProductId(), effectiveQty);
            cartReservationService.reserve(cartItem);
        }

        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);

        cart.advanceVersion();
        CartResponseDTO response = CartMapper.toDTO(cart);
        response.setWarning(warning);
        return response;
    }

    @Override
    @Transactional
    public void deleteProductFromCart(String productId, CartContext context) {
        Cart cart = resolveCart(context);
        lockProducts(List.of(cart),List.of(productId));
        CartItem cartItem = cart.findItemByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        cartReservationService.release(cartItem);
        cart.removeCartItem(cartItem);
        cartPricingService.applyCharges(cart);
        cart.advanceVersion();
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void clearCart(CartContext context) {
        Cart cart = resolveCart(context);
        lockProducts(List.of(cart),List.of());
        if (cart.isEmpty()) return;

        List<CartItem> itemsToRelease = List.copyOf(cart.getCartItems());
        itemsToRelease.forEach(cartReservationService::release);
        cart.clear();
        cartPricingService.applyCharges(cart);
        cart.advanceVersion();
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void removePurchasedItems(Cart cart, com.mercato.Entity.fulfillment.Order order) {
        if(entityManager.getLockMode(cart)!=jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
            throw new IllegalStateException("Purchased quantity removal requires the allocation cart mutex");
        boolean changed=false;
        for (var line : order.getOrderLines().stream().sorted(Comparator.comparing(com.mercato.Entity.fulfillment.OrderLine::getProductId)).toList()) {
            var selected=cart.findItemByProductId(line.getProductId());
            if(selected.isEmpty() || !sameSelection(selected.get(),line))continue;
            changed=true;
            cart.findItemByProductId(line.getProductId()).ifPresent(item -> {
                int remaining = Math.max(0, item.getQuantity() - line.getOrderedQty());
                if (remaining == 0) {
                    cartReservationService.release(item);
                    cart.removeCartItem(item);
                } else {
                    item.updateQuantity(remaining);
                    cartReservationService.reserve(item);
                }
            });
        }
        cartPricingService.applyCharges(cart);
        if(changed)cart.advanceVersion();
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void mergeGuestCartOnLogin(String userId, String guestToken) {
        var guestId=cartRepository.findIdByGuestToken(guestToken);
        if(guestId.isEmpty())return;
        var userCartId=cartRepository.findIdByUserId(userId).orElseGet(()->{
            userRepository.findByUserIdForUpdate(userId).orElseThrow();
            return cartRepository.findIdByUserId(userId).orElseGet(()->createUserCart(userId).getId());
        });
        var locked=new TreeMap<Long,Cart>();
        for(var id:new TreeSet<>(List.of(guestId.get(),userCartId)))locked.put(id,lockRow(id));
        Cart guestCart=locked.get(guestId.get()),userCart=locked.get(userCartId);
        lockProducts(locked.values(),List.of());
        if (guestCart.isEmpty()) {
            cartRepository.delete(guestCart);
            return;
        }

        if (userCart.isEmpty()) userCart.setDeliveryMethod(guestCart.getDeliveryMethod());

        // Release guest holds before reserving the merged cart. Both operations are in
        // this transaction, so a failed merge restores the original guest holds.
        cartReservationService.releaseAllForCart(guestCart);

        for (CartItem guestItem : guestCart.getCartItems().stream().sorted(Comparator.comparing(i->i.getProduct().getProductId())).toList()) {
            Product product = productRepository.findByProductIdForUpdate(guestItem.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", guestItem.getProduct().getProductId()));
            if (!product.isPurchasable()) continue;
            userCart.findItemByProductId(product.getProductId())
                    .ifPresentOrElse(
                            existing -> {
                                int mergedQty = Math.max(existing.getQuantity(), guestItem.getQuantity());
                                existing.updateQuantity(mergedQty);
                                cartReservationService.reserve(existing);
                            },
                            () -> {
                                userCart.addCartItem(guestItem.copySelection(userCart,product));
                                CartItem newItem = userCart.findItemByProductId(product.getProductId())
                                        .orElseThrow();
                                cartReservationService.reserve(newItem);
                            }
                    );
        }

        cartPricingService.applyCharges(userCart);
        userCart.advanceVersion();
        cartRepository.save(userCart);
        cartRepository.delete(guestCart);
    }

    @Override
    @Transactional
    public Cart getCartByUser(EcommUser user) {
        Cart cart = cartRepository.findIdByUserId(user.getUserId()).map(this::lockRow).orElse(null);
        if (cart == null) return null;
        lockProducts(List.of(cart),List.of());reprice(cart);
        return cart;
    }

    @Override @Transactional
    public Cart getCartForOrderAllocation(EcommUser user,com.mercato.Entity.fulfillment.Order order) {
        Cart cart=cartRepository.findIdByUserId(user.getUserId()).map(this::lockRow).orElse(null);
        if(cart==null)return null;
        lockProducts(List.of(cart),order.getOrderLines().stream().map(com.mercato.Entity.fulfillment.OrderLine::getProductId).toList());
        return cart;
    }
    @Override @Transactional
    public Cart lockCartForMutation(Long id){var cart=lockRow(id);lockProducts(List.of(cart),List.of());return cart;}

    public static boolean sameSelection(CartItem item,com.mercato.Entity.fulfillment.OrderLine line) {
        return (line.getSourceCartItemId()!=null && line.getSourceCartItemId().equals(item.getCartItemId())
            && line.getSourceSelectionGeneration()!=null && line.getSourceSelectionGeneration().equals(item.getSelectionGeneration()))
            || (line.getRetryCartItemId()!=null && line.getRetryCartItemId().equals(item.getCartItemId())
            && line.getRetrySelectionGeneration()!=null && line.getRetrySelectionGeneration().equals(item.getSelectionGeneration()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartResponseDTO> getAllCarts() {
        return cartRepository.findAll().stream()
                .map(CartMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDTO getCartById(String cartId) {
        if (cartId == null) throw new IllegalArgumentException("cartId must not be null!");
        Cart cart = cartRepository.findByCartId(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));
        return CartMapper.toDTO(cart);
    }

    private Cart resolveCart(CartContext context) {
        if (!context.isGuest()) {
            var id=cartRepository.findIdByUserId(context.userId());
            if(id.isPresent())return lockRow(id.get());
            userRepository.findByUserIdForUpdate(context.userId()).orElseThrow(()->new ResourceNotFoundException("User","userId",context.userId()));
            return cartRepository.findIdByUserId(context.userId()).map(this::lockRow).orElseGet(()->createUserCart(context.userId()));
        }
        if (context.guestToken() != null) {
            return cartRepository.findIdByGuestToken(context.guestToken()).map(this::lockRow).orElseGet(()->createGuestCart(context.guestToken()));
        }
        throw new IllegalStateException("Cannot resolve cart: no userId or guestToken");
    }

    private Cart lockRow(Long id) {
        var cart=cartRepository.lockById(id).orElseThrow(()->new ResourceNotFoundException("Cart","id",id));
        entityManager.refresh(cart,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return cart;
    }
    private void lockProducts(Collection<Cart> carts,Collection<String> additional) {
        var ids=new TreeSet<String>(additional);
        for(var cart:carts)ids.addAll(cartRepository.productIds(cart.getId()));
        for(var id:ids)productRepository.findByProductIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Product","productId",id));
    }
    private void reprice(Cart cart) {
        String before=chargeSignature(cart);cartPricingService.applyCharges(cart);
        if(!before.equals(chargeSignature(cart)))cart.advanceVersion();
    }
    private String chargeSignature(Cart cart){return cart.getCharges().stream().sorted(Comparator.comparing(c->c.getType().name())).map(c->c.getType()+":"+c.getAmount().stripTrailingZeros().toPlainString()).collect(java.util.stream.Collectors.joining("|"));}

    private Cart createUserCart(String userId) {
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        Cart cart = Cart.builder()
                .user(user)
                .build();
        return cartRepository.saveAndFlush(cart);
    }

    private Cart createGuestCart(String guestToken) {
        Cart cart = Cart.builder()
                .guestToken(guestToken)
                .build();
        return cartRepository.saveAndFlush(cart);
    }
}
