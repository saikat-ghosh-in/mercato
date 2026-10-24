import api, { session } from "../../backend/api.js";
import toast from "react-hot-toast";

export const placeOrder = (orderPayload) => async (dispatch) => {
    const epoch = session.getSnapshot().epoch;
    try {
        const { data } = await api.post("/orders/capture", orderPayload);
        session.assertCurrent({ sessionEpoch: epoch });
        dispatch({ type: "SET_ORDER_RESPONSE", payload: data });
        return data;
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to place order");
        throw error;
    }
};
