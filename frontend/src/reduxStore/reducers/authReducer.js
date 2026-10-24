const initialState = {
    user: null,
    userDetails: null,
    addresses: [],
    selectedAddressForCheckout: null,
    orderResponse: null,
    paymentData: null,
    paymentStatus: "idle",
    sessionStatus: "idle",
    sessionEpoch: 0,
    sessionError: null,
    contextId: null,
    mfa: { state: "NONE" },
};

export const authReducer = (state = initialState, action) => {
    switch (action.type) {
        case "SESSION_STATE":
            return { ...state, user: action.payload.user, userDetails: action.payload.user,
                sessionStatus: action.payload.status, sessionEpoch: action.payload.epoch,
                sessionError: action.payload.error, contextId: action.payload.contextId,
                expiresAt: action.payload.expiresAt, mfa: action.payload.mfa };
        case "LOGIN":
            return { ...state, user: action.payload };
        case "SET_USER_DETAILS":
            return { ...state, userDetails: action.payload };
        case "USER_ADDRESS":
            return { ...state, addresses: action.payload };
        case "SELECT_CHECKOUT_ADDRESS":
            return { ...state, selectedAddressForCheckout: action.payload };
        case "REMOVE_CHECKOUT_ADDRESS":
            return { ...state, selectedAddressForCheckout: null };
        case "SET_ORDER_RESPONSE":
            return { ...state, orderResponse: action.payload };
        case "SET_PAYMENT_DATA":
            return { ...state, paymentData: action.payload };
        case "CLEAR_PAYMENT_DATA":
            return { ...state, paymentData: null, paymentStatus: "idle" };
        case "UPDATE_PAYMENT_STATUS":
            return { ...state, paymentStatus: action.payload };
        case "LOGOUT":
            return initialState;
        default:
            return state;
    }
};
