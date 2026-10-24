import api, { session } from "../../backend/api";
import toast from "../../utils/toastUtils.js";
import { privateApi } from "../services/privateApi.js";

export const fetchWishlistIds = () => async (dispatch, getState) => {
    if (Object.values(getState().wishlist.pending).some(Boolean)) return;
    const requestId = crypto.randomUUID();
    const epoch = session.getSnapshot().epoch;
    dispatch({ type: "WISHLIST_READ_STARTED", requestId });
    try {
        const { data } = await api.get("/wishlist/ids");
        if (epoch === session.getSnapshot().epoch) dispatch({ type: "SET_WISHLIST_IDS", payload: Array.from(data), requestId });
    } catch {
        if (epoch === session.getSnapshot().epoch) dispatch({ type: "WISHLIST_READ_ERROR", requestId, payload: "Your saved items couldn’t load. Please retry." });
    }
};

export const toggleWishlist = (productId) => async (dispatch, getState) => {
    if (!getState().auth.user || !getState().wishlist.loaded || getState().wishlist.pending?.[productId]) return;
    const epoch = session.getSnapshot().epoch;
    dispatch({ type: "WISHLIST_PENDING", productId, value: true });
    const isWishlisted = getState().wishlist.productIds.includes(productId);
    try {
        if (isWishlisted) {
            await api.delete(`/wishlist/${productId}`);
            if (epoch !== session.getSnapshot().epoch) return;
            dispatch({ type: "REMOVE_WISHLIST_ID", payload: productId });
            } else {
            await api.post(`/wishlist/${productId}`);
            if (epoch !== session.getSnapshot().epoch) return;
            dispatch({ type: "ADD_WISHLIST_ID", payload: productId });
            }
        dispatch(privateApi.util.invalidateTags(["Wishlist"]));
    } catch (err) {
        if (epoch !== session.getSnapshot().epoch) return;
        toast.error(err?.response?.data?.message || "Failed to update wishlist");
    } finally { if (epoch === session.getSnapshot().epoch) dispatch({ type: "WISHLIST_PENDING", productId, value: false }); }
};

export const clearWishlist = () => ({ type: "CLEAR_WISHLIST" });
