import api, { session } from "../../backend/api.js";

export const placeOrder = (orderPayload) => async (dispatch) => {
    const epoch = session.getSnapshot().epoch;
    const { data } = await api.post("/orders/capture", orderPayload);
    session.assertCurrent({ sessionEpoch: epoch });
    dispatch({ type: "SET_ORDER_RESPONSE", payload: data });
    return data;
};
