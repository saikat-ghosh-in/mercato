import { countCartUnits } from "../../utils/cartState.js";
import { createSelector } from '@reduxjs/toolkit';

const selectCartItems = (state) => state.cart.cartItems;

export { selectCartItems };
export const selectCartId = (state) => state.cart.cartId;
export const selectSubtotal = (state) => state.cart.subtotal;
export const selectTotal = (state) => state.cart.total;
export const selectCartLoading = (state) => state.cart.cartLoading;
export const selectCartLoaded = (state) => state.cart.cartLoaded;
export const selectCartError = (state) => state.cart.cartError;
export const selectCartPendingSync = (state) => state.cart.cartLoading;

export const selectCharges = createSelector(
    (state) => state.cart.shipping,
    (state) => state.cart.platformFee,
    (state) => state.cart.processingAndHandling,
    (state) => state.cart.totalCharges,
    (shipping, platformFee, processingAndHandling, totalCharges) => ({
        shipping, platformFee, processingAndHandling, totalCharges
    })
);

export const selectCartQty = createSelector(
    selectCartItems,
    countCartUnits
);

export const selectIsCartEmpty = createSelector(
    selectCartItems,
    (items) => items.length === 0
);

const _cartItemSelectorCache = {};
export const selectCartItemByProductId = (productId) => {
    if (!_cartItemSelectorCache[productId]) {
        _cartItemSelectorCache[productId] = createSelector(
            selectCartItems,
            (items) => items.find((item) => item.productId === productId) ?? null
        );
    }
    return _cartItemSelectorCache[productId];
};