const initialState = {
    productIds: [],
    loaded: false,
    pending: {},
};

export const wishlistReducer = (state = initialState, action) => {
    switch (action.type) {
        case "WISHLIST_PENDING":
            return { ...state, pending: { ...state.pending, [action.productId]: action.value } };
        case "SET_WISHLIST_IDS":
            return { ...state, productIds: action.payload, loaded: true };
        case "ADD_WISHLIST_ID":
            return state.productIds.includes(action.payload)
                ? state
                : { ...state, productIds: [...state.productIds, action.payload], loaded: true };
        case "REMOVE_WISHLIST_ID":
            return {
                ...state,
                productIds: state.productIds.filter((id) => id !== action.payload),
                loaded: true,
            };
        case "CLEAR_WISHLIST":
            return { productIds: [], loaded: false, pending: {} };
        default:
            return state;
    }
};
