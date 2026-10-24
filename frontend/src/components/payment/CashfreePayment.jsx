import ActionArrow from "../shared/ActionArrow";
import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { load } from "@cashfreepayments/cashfree-js";
import { FiLock } from "react-icons/fi";
import { formatCurrency } from "../../utils/formatCurrency";
import DeliveryDetails from "../checkout/DeliveryDetails";
import { usePaymentSessionQuery } from "../../reduxStore/services/privateApi.js";
import { recoveredPayment, validatedOrderId } from "../../utils/accountState.js";

export default function CashfreePayment() {
  const [params] = useSearchParams();
  const pendingPayment = useSelector(s => s.auth.paymentData);
  const orderId = params.get("orderId");
  const valid = Boolean(validatedOrderId(orderId));
  const { data, isLoading, isFetching, error, refetch } = usePaymentSessionQuery(orderId, { skip: !valid, refetchOnMountOrArgChange: true });
  if (!orderId && pendingPayment?.orderId) return <Navigate to={`?orderId=${encodeURIComponent(pendingPayment.orderId)}`} replace />;
  if (isLoading || isFetching) return <div className="site-container route-loading" role="status" aria-label="Recovering your payment"><div /><div /></div>;
  let payment;
  let message = error ? "Your payment session couldn’t load. Please try again." : "Open an order to continue its payment.";
  if (valid && !error) { try { payment = recoveredPayment(data, orderId); } catch (err) { message = err.message; } }
  if (!payment) return <div className="site-container state-panel"><h1>Check your payment</h1><p role="alert">{message}</p>{valid && <button className="button button-dark" onClick={refetch}>Try again</button>}<Link className="button button-outline" to={valid ? `/orders?orderId=${encodeURIComponent(orderId)}` : "/orders"}>View order status</Link></div>;
  return <PaymentSession key={String(payment.orderId)} paymentData={payment} selectedAddressForCheckout={payment.deliveryAddress} />;
}

function PaymentSession({ paymentData, selectedAddressForCheckout }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState("opening");
  const sessionId = paymentData?.paymentSessionId;
  useEffect(() => {
    if (!sessionId) return;
    let active = true;
    const open = async () => {
      try {
        const cashfree = await load({ mode: import.meta.env.VITE_CASHFREE_MODE || "sandbox" });
        if (!active) return;
        const result = await cashfree.checkout({ paymentSessionId: sessionId, redirectTarget: "_self" });
        if (active) setState(result?.error ? "error" : "waiting");
      } catch {
        if (active) setState("error");
      }
    };
    open();
    return () => { active = false; };
  }, [sessionId, attempt]);
  if (!sessionId) return <Navigate to="/checkout" replace />;
  return <div className="checkout-shell"><div className="site-container checkout-handoff"><div className="checkout-handoff-copy"><span className="checkout-result-icon"><FiLock aria-hidden="true" /></span><p className="eyebrow">SECURE PAYMENT</p><h1>{state === "error" ? "Let’s reconnect your payment." : "Your finds are almost yours."}</h1><p role="status">{state === "opening" ? "Opening Cashfree to complete your payment securely…" : state === "error" ? "Cashfree couldn’t open. Your order details are saved below. Retry this payment session or check your order status." : "If you haven’t reached Cashfree, reopen your payment or check your order status before trying again."}</p><div className="checkout-handoff-actions"><button className="button button-dark button-prominent" disabled={state === "opening"} onClick={() => { setState("opening"); setAttempt(a => a + 1); }}>Open Cashfree <ActionArrow /></button><Link className="button button-outline" to={`/orders?orderId=${encodeURIComponent(paymentData.orderId)}`}>View order status</Link></div><Link className="text-link" to="/help">Need help with payment?</Link></div><aside className="checkout-summary"><h2>Payment details</h2><p className="checkout-session-order">Order {paymentData.orderId}</p><div className="checkout-payment-amount"><span>Amount to pay</span><strong>{formatCurrency(paymentData.totalAmount)}</strong></div><div className="checkout-payment-delivery"><h3>Delivering to</h3><DeliveryDetails address={selectedAddressForCheckout} /></div></aside></div></div>;
}
