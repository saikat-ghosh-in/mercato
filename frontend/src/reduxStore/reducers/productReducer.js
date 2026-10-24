const initialState = {
    products: [],
    pagination: {},
    productToDisplay: null,
    byId: {},
    loading: false,
    error: null,
    requestId: null,
};

export const productReducer = (state = initialState, action) => {
    switch (action.type) {
        case "PRODUCTS_LOADING":
            return { ...state, loading: true, error: null, requestId: action.requestId };
        case "PRODUCTS_ERROR":
            if (state.requestId !== action.requestId) return state;
            return { ...state, loading: false, error: action.payload };
        case "FETCH_PRODUCTS":
            if (state.requestId !== action.requestId) return state;
            return {
                ...state,
                loading: false,
                error: null,
                products: action.payload,
                pagination: {
                    ...state.pagination,
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };
        case "SET_PRODUCT_TO_DISPLAY":
            return {
                ...state,
                byId: action.payload ? { ...state.byId, [action.payload.productId]: action.payload } : state.byId,
                productToDisplay: action.payload
            };
        default:
            return state;
    }
};
