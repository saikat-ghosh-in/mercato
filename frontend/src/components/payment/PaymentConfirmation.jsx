import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { Link } from "react-router-dom";
import { FiCheck, FiClock, FiAlertCircle, FiShoppingBag } from "react-icons/fi";

import api from "../../backend/api";
import { syncCartFromBackend } from "../../reduxStore/actions/cartActions";
import { clearPaymentData } from "../../reduxStore/actions/paymentActions";
import { validatedOrderId } from "../../utils/accountState.js";

const TERMINAL_FAILURE = new Set(["FAILED", "USER_DROPPED", "CANCELLED"]);
const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 45000;

const PaymentConfirmation = () => {

  const dispatch = useDispatch();

  const searchParams = new URLSearchParams(window.location.search);
  const orderId = validatedOrderId(searchParams.get("order_id") || searchParams.get("orderId"));

  const [status, setStatus] = useState(orderId ? "loading" : "failed");
  const [paymentStatus, setPaymentStatus] = useState(null);

  useEffect(() => {
    if (!orderId) {
      return;
    }

    let cancelled = false;
    let timeoutId;
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    const controller = new AbortController();

    const resolveStatus = async () => {
      try {
        await api.post(`/orders/${orderId}/sync-payment-refund`, undefined, { signal: controller.signal, timeout: 10000 });
        const { data: order } = await api.get(`/user/orders/${orderId}`, { signal: controller.signal, timeout: 10000 });
        if (cancelled) return true;

        const currentStatus = order?.paymentSummary?.status;
        setPaymentStatus(currentStatus);

        if (currentStatus === "SUCCESS" && order?.inventoryFinalizationFailed) {
          dispatch(clearPaymentData());
          setStatus("inventory_failed");
          return true;
        }

        if (currentStatus === "SUCCESS") {
          dispatch(syncCartFromBackend());
          dispatch(clearPaymentData());
          setStatus("success");
          return true;
        }

        if (TERMINAL_FAILURE.has(currentStatus)) {
          dispatch(clearPaymentData());
          setStatus("failed");
          return true;
        }

        return false;
      } catch (error) {
        if (!cancelled) {
          console.error("Payment status sync failed:", error);
        }
        return false;
      }
    };

    const poll = async () => {
      const done = await resolveStatus();
      if (done || cancelled) return;

      if (Date.now() >= deadline) {
        dispatch(clearPaymentData());
        setStatus("pending");
      } else {
        timeoutId = setTimeout(poll, POLL_INTERVAL_MS);
      }
    };

    poll();


    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timeoutId);
    };
  }, [orderId, dispatch]);

  const content = {
    loading: { title: "Checking your payment.", description: "We’re confirming your payment status. This can take a moment.", icon: FiClock },
    inventory_failed: { title: "Payment received. We’re checking stock.", description: "Your payment went through, but stock could not be confirmed. Check your order for updates and contact support. Avoid paying again for this order.", icon: FiAlertCircle },
    failed: { title: "Payment not completed.", description: paymentStatus ? `Payment status: ${paymentStatus.replaceAll("_", " ").toLowerCase()}. Check your order to retry payment.` : "We couldn’t confirm a payment. Check your orders before trying again.", icon: FiAlertCircle },
    pending: { title: "Your payment is still processing.", description: "We’re still waiting for confirmation. Check your order shortly for the latest status before paying again.", icon: FiClock },
    success: { title: "Payment confirmed.", description: "Your payment is confirmed. You can follow your order and delivery updates in My orders.", icon: FiCheck },
  }[status];
  const Icon = content.icon;
  return <div className="checkout-shell"><section className="site-container checkout-result" aria-labelledby="payment-result-title"><span className="checkout-result-icon"><Icon aria-hidden="true" /></span><h1 id="payment-result-title">{content.title}</h1><p role="status">{content.description}</p>{orderId && <p className="checkout-session-order">Order {orderId}</p>}<div className="checkout-handoff-actions"><Link className="button button-dark button-prominent" to={orderId ? `/orders?orderId=${encodeURIComponent(orderId)}` : "/orders"}><FiShoppingBag /> View your order{!orderId && "s"}</Link><Link className="button button-outline" to={status === "success" ? "/products" : "/cart"}>{status === "success" ? "Keep discovering" : "Return to bag"}</Link></div><Link className="text-link" to="/help">Need a hand? Visit Help</Link></section></div>;
};
export default PaymentConfirmation;
