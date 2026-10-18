const initialState = {
    cartLoading: false,
    cartLoaded: false,
    cartError: null,
    cartItems: [],
    cartId: null,
    subtotal: 0,
    total: 0,
    shipping: 0,
    platformFee: 0,
    processingAndHandling: 0,
    totalCharges: 0,
    updateSeq: 0
};

export const cartReducer = (state = initialState, action) => {
    switch (action.type) {

        case "CART_PENDING":
            return { ...state, cartLoading: true, cartError: null };

        case "CART_IDLE":
            return { ...state, cartLoading: false };
        case "CART_ERROR":
            return { ...state, cartLoading: false, cartLoaded: true, cartError: action.payload };

        case "CART_ITEM_UPDATE_FAILED":
            return { ...state, cartLoading: false, updateSeq: state.updateSeq + 1 };

        case "UPDATE_CART_LOCAL": {
            const { productId, newQuantity } = action.payload;
            if (!Number.isFinite(newQuantity) || newQuantity < 1) return state;
            return {
                ...state,
                cartItems: state.cartItems.map((item) =>
                    item.productId === productId
                        ? { ...item, quantity: newQuantity, lineTotal: newQuantity * item.itemPrice }
                        : item
                )
            };
        }

        case "SYNC_CART_SUCCESS": {
            const cart = action.payload;
            if (!cart || !Array.isArray(cart.cartItems)) {
                return { ...initialState, cartLoaded: true };
            }

            const cartItems = cart.cartItems.map((item) => ({
                cartItemId: item.cartItemId,
                productId: item.productId,
                quantity: item.quantity,
                itemPrice: item.itemPrice,
                lineTotal: item.lineTotal,
                canAddMore: item.canAddMore ?? true,
                outOfStock: item.outOfStock ?? false,
            }));

            let shipping = 0, platformFee = 0, processingAndHandling = 0;
            (cart.charges || []).forEach((charge) => {
                if (charge.type === "SHIPPING") shipping = charge.amount;
                else if (charge.type === "PLATFORM_FEE") platformFee = charge.amount;
                else if (charge.type === "PROCESSING_AND_HANDLING") processingAndHandling = charge.amount;
            });

            return {
                ...state,
                cartItems,
                cartId: cart.cartId,
                subtotal: cart.subtotal || 0,
                total: cart.total || 0,
                shipping,
                platformFee,
                processingAndHandling,
                totalCharges: cart.totalCharges || 0,
                cartLoaded: true,
                cartLoading: false,
                cartError: null,
                updateSeq: state.updateSeq + 1
            };
        }

        case "CLEAR_CART":
            return { ...initialState, cartLoaded: true };

        default:
            return state;
    }
};
