const initialState = {
    productIds: [],
    loaded: false,
    pending: {},
    readId: null,
};

export const wishlistReducer = (state = initialState, action) => {
    switch (action.type) {
        case "WISHLIST_READ_STARTED":
            return { ...state, readId: action.requestId, error: null };
        case "WISHLIST_READ_ERROR":
            return action.requestId === state.readId ? { ...state, readId: null, error: action.payload } : state;
        case "WISHLIST_PENDING":
            return { ...state, readId: action.value ? null : state.readId, pending: { ...state.pending, [action.productId]: action.value } };
        case "SET_WISHLIST_IDS":
            return action.requestId !== state.readId ? state : { ...state, productIds: action.payload, loaded: true, readId: null, error: null };
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
            return { ...initialState };
        default:
            return state;
    }
};
