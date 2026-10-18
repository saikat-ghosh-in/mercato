import { normalizeQuantity } from "../../utils/cartState.js";
const initialState = {
    pendingCount: 0,
    pendingProducts: {},
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
            return { ...state, pendingCount: state.pendingCount + 1, pendingProducts: { ...state.pendingProducts, [action.productId]: (state.pendingProducts[action.productId] || 0) + 1 }, cartLoading: true, cartError: null };

        case "CART_IDLE":
            return { ...state, pendingCount: Math.max(0, state.pendingCount - 1), pendingProducts: { ...state.pendingProducts, [action.productId]: Math.max(0, (state.pendingProducts[action.productId] || 0) - 1) }, cartLoading: state.pendingCount > 1 };
        case "CART_ERROR":
            return { ...state, cartLoading: state.pendingCount > 0, cartLoaded: true, cartError: action.payload };

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
                return { ...state, cartLoaded: true, cartError: "Invalid bag response. Please refresh." };
            }

            const cartItems = cart.cartItems.map((item) => ({
                cartItemId: item.cartItemId,
                productId: item.productId,
                quantity: normalizeQuantity(item.quantity),
                itemPrice: item.itemPrice,
                lineTotal: item.lineTotal,
                canAddMore: item.canAddMore ?? true,
                outOfStock: item.outOfStock ?? false,
            })).filter(item => item.productId && item.quantity > 0);

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
                cartLoading: state.pendingCount > 0,
                cartError: null,
                updateSeq: state.updateSeq + 1
            };
        }

        case "RESET_CART":
            return { ...initialState };

        case "CLEAR_CART":
            return { ...initialState, cartLoaded: true };

        default:
            return state;
    }
};
