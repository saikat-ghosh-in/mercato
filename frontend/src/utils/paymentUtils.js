import api, { session } from "../backend/api.js";
import toast from "./toastUtils.js";
import { setPaymentData } from "../reduxStore/actions/paymentActions";

export const syncPayment = async ({ orderId, onSuccess, setLoading }) => {
  const epoch = session.getSnapshot().epoch;
  setLoading(true);
  try {
    await api.post(`/orders/${orderId}/sync-payment-refund`);
    session.assertCurrent({ sessionEpoch: epoch });
    onSuccess?.();
  } catch (err) {
    toast.error(err?.response?.data?.message || "Failed to sync payment");
  } finally {
    setLoading(false);
  }
};

export const retryPayment = async ({ order, dispatch, navigate, setLoading }) => {
  const epoch = session.getSnapshot().epoch;
  setLoading(true);
  try {
    const { data } = await api.post(`/orders/${order.orderId}/retry-payment`);
    session.assertCurrent({ sessionEpoch: epoch });
    dispatch(
      setPaymentData({
        paymentSessionId: data.paymentSessionId,
        orderId: order.orderId,
        totalAmount: order.totalAmount,
      })
    );
    navigate(`/checkout/cashfree-payment?orderId=${encodeURIComponent(order.orderId)}`);
  } catch (err) {
    const data = err?.response?.data;
    const message = data?.productId
      ? `Stock is no longer held for this order (${data.availableQty} available). Add items to your cart and try again.`
      : data?.message || "Failed to retry payment. Please try again.";
    toast.error(message);
  } finally {
    setLoading(false);
  }
};
