import api, { session } from "../../backend/api";
import toast from "../../utils/toastUtils.js";
import { showWarningToast } from "../../utils/toastUtils";
import { createSerialQueue } from "../../utils/cartState.js";

const requests = createSerialQueue();
let epoch = 0;
export const waitForCartRequests = () => requests.drain();
export const invalidateCartRequests = () => { epoch += 1; };
let sessionEpoch = session.getSnapshot().epoch;
session.subscribe(snapshot => {
  if (snapshot.epoch !== sessionEpoch) { sessionEpoch = snapshot.epoch; invalidateCartRequests(); }
});

const acceptCart = (dispatch, data) => {
  if (!data || !Array.isArray(data.cartItems)) throw new Error("The server returned an invalid bag. Please refresh.");
  dispatch({ type: "SYNC_CART_SUCCESS", payload: data });
};
export const syncCartFromBackend = () => dispatch => {
  const current = epoch;
  return requests.run(async () => {
    if (current !== epoch) return;
    try { const { data } = await api.get("/cart"); if (current === epoch) acceptCart(dispatch, data); }
    catch (error) { if (current === epoch) dispatch({ type: "CART_ERROR", payload: error?.response?.data?.message || "Your bag couldn’t be refreshed. Please try again." }); }
  });
};
const mutate = (productId, execute) => dispatch => {
  const current = epoch;
  dispatch({ type: "CART_PENDING", productId });
  return requests.run(async () => {
    if (current !== epoch) return;
    try {
      const data = await execute();
      if (current !== epoch) return;
      acceptCart(dispatch, data);
      if (data.warning) showWarningToast(data.warning);
      return true;
    } catch (error) {
      if (current === epoch) { dispatch({ type: "CART_ERROR", payload: error?.response?.data?.message || error.message || "Couldn’t update your bag. Please try again." }); toast.error(error?.response?.data?.message || "Couldn’t update your bag. Please try again."); }
      return false;
    } finally { if (current === epoch) dispatch({ type: "CART_IDLE", productId }); }
  });
};
export const addToCart = (productId, quantity = 1) => mutate(productId, async () => {
  const { data } = await api.post("/cart/add", { cartItems: [{ productId, quantity }] }); return data;
});
export const updateCartItemQuantity = (productId, newQuantity) => (dispatch, getState) => {
  if (!Number.isSafeInteger(newQuantity) || newQuantity < 1) return Promise.resolve(false);
  if (newQuantity === getState().cart.cartItems.find(i => i.productId === productId)?.quantity) return Promise.resolve(true);
  return dispatch(mutate(productId, async () => { const { data } = await api.put("/cart", { productId, quantity: newQuantity }); return data; }));
};
export const removeFromCart = productId => mutate(productId, async () => {
  await api.delete("/cart/remove/" + productId); return (await api.get("/cart")).data;
});
export const clearCart = () => mutate("*", async () => {
  await api.delete("/cart"); return (await api.get("/cart")).data;
});

export const selectDeliveryMethod = deliveryMethod => mutate("delivery", async () => {
  const { data } = await api.put("/cart/delivery-method", { deliveryMethod }); return data;
});
