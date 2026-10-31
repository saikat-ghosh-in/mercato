// Reject a selection that disappeared during a refresh or deletion.
export function createCheckoutIntent(makeId = () => crypto.randomUUID()) {
  let saved;
  return {
    payload(body) { return saved ??= Object.freeze({ ...body, operationId: makeId() }); },
    restore(request) { saved = Object.freeze({ ...request }); },
    current() { return saved; },
    clear() { saved = undefined; },
  };
}
export function recoveryAction(recovery) {
  if (recovery?.canReplayCapture && recovery.state === "PAYMENT_READY") return "payment";
  if (recovery?.canReplayCapture && recovery.state === "REPLAY_CAPTURE") return "replay";
  return "order";
}
export function currentDeliveryAddress(addresses, selected) {
  return addresses?.find(address => address.addressId === selected?.addressId)
    ?? addresses?.find(address => address.defaultAddress)
    ?? (addresses?.length === 1 ? addresses[0] : null);
}
export function checkoutIssue({ cartLoaded, cartLoading, cartError, loadingProducts, productsUnavailable, cartQty }) {
  if (!cartLoaded || cartLoading || loadingProducts) return "We’re checking your bag. Please wait a moment.";
  if (cartError || productsUnavailable) return "Some items couldn’t be checked. Return to your bag to refresh them before paying.";
  if (!Number.isInteger(cartQty) || cartQty < 1) return "Your bag is empty. Add an item to continue.";
  return "";
}
export function paymentHandoff(response) {
  const order = response?.order;
  if (!order?.orderId || typeof response?.paymentSessionId !== "string" || !response.paymentSessionId.trim() || !Number.isFinite(order.totalAmount) || order.totalAmount < 0) {
    throw new Error("The payment service returned incomplete details. Please check your orders before trying again.");
  }
  return { totalAmount: order.totalAmount, paymentStatus: "initiated", timestamp: Date.now(), paymentId: order.paymentSummary?.paymentId, orderId: order.orderId, paymentSessionId: response.paymentSessionId };
}
