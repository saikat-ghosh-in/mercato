import api from "../../backend/api";
import toast from "react-hot-toast";
import { showWarningToast } from "../../utils/toastUtils";

export const syncCartFromBackend = () => async dispatch => {
  try {
    const { data } = await api.get("/cart");
    dispatch({ type: "SYNC_CART_SUCCESS", payload: data });
  } catch (error) {
    dispatch({ type: "CART_ERROR", payload: error?.response?.data?.message || "Your bag couldn’t be refreshed. Please try again." });
    if (error?.response?.status === 404) dispatch({ type: "CLEAR_CART" });
  }
};

export const addToCart = (productId, quantity = 1) => async dispatch => {
  dispatch({ type: "CART_PENDING" });
  try {
    const { data } = await api.post("/cart/add", { cartItems: [{ productId, quantity }] });
    dispatch({ type: "SYNC_CART_SUCCESS", payload: data });
    toast.success("Added to your bag");
  } catch (error) {
    dispatch({ type: "CART_IDLE" });
    toast.error(error?.response?.data?.message || "Couldn’t add this item. Please try again.");
  }
};

export const updateCartItemQuantity = (productId, newQuantity) => async (dispatch, getState) => {
  if (!Number.isInteger(newQuantity) || newQuantity < 1) return;
  const currentQty = getState().cart.cartItems.find(i => i.productId === productId)?.quantity;
  if (newQuantity === currentQty) return;
  dispatch({ type: "CART_PENDING" });
  try {
    const { data } = await api.put("/cart", { productId, quantity: newQuantity });
    dispatch({ type: "SYNC_CART_SUCCESS", payload: data });
    if (data.warning) showWarningToast(data.warning);
  } catch (error) {
    dispatch({ type: "CART_ITEM_UPDATE_FAILED", payload: productId });
    toast.error(error?.response?.data?.message || "Couldn’t update quantity. Please try again.");
  }
};

export const removeFromCart = productId => async dispatch => {
  dispatch({ type: "CART_PENDING" });
  try {
    // DELETE returns a text acknowledgement, not the updated cart.
    await api.delete("/cart/remove/" + productId);
    await dispatch(syncCartFromBackend());
    toast.success("Removed from your bag");
  } catch (error) {
    dispatch({ type: "CART_IDLE" });
    toast.error(error?.response?.data?.message || "Couldn’t remove this item. Please try again.");
  }
};

export const clearCart = () => async dispatch => {
  dispatch({ type: "CART_PENDING" });
  try {
    await api.delete("/cart");
    dispatch({ type: "CLEAR_CART" });
  } catch (error) {
    dispatch({ type: "CART_IDLE" });
    toast.error(error?.response?.data?.message || "Couldn’t clear your bag. Please try again.");
  }
};
