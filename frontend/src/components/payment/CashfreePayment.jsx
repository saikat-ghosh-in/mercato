import { LoadingLayout } from "../shared/LoadingStates";
import ActionArrow from "../shared/ActionArrow";
import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { load } from "@cashfreepayments/cashfree-js";
import { FiLock } from "react-icons/fi";
import { formatCurrency } from "../../utils/formatCurrency";
import DeliveryDetails from "../checkout/DeliveryDetails";
import { usePaymentSessionQuery } from "../../reduxStore/services/privateApi.js";
import { recoveredPayment, validatedOrderId, paymentHandoffAllowed } from "../../utils/accountState.js";

export default function CashfreePayment() {
  const [params] = useSearchParams();
  const pendingPayment = useSelector(s => s.auth.paymentData);
  const orderId = params.get("orderId");
  const valid = Boolean(validatedOrderId(orderId));
  const { data, isLoading, isFetching, error, refetch } = usePaymentSessionQuery(orderId, { skip: !valid, refetchOnMountOrArgChange: true });
  if (!orderId && pendingPayment?.orderId) return <Navigate to={`?orderId=${encodeURIComponent(pendingPayment.orderId)}`} replace />;
  if (!data && (isLoading || isFetching)) return <div className="site-container skeleton-page"><LoadingLayout variant="payment" label="Recovering your payment" /></div>;
  let payment;
  let message = error ? "Your payment session couldn’t load. Please try again." : "Open an order to continue its payment.";
  if (valid) { try { payment = recoveredPayment(data, orderId); } catch (err) { message = err.message; } }
  if (!payment) return <div className="site-container state-panel"><h1>Check your payment</h1><p role="alert">{message}</p>{valid && <button className="button button-dark" onClick={refetch}>Try again</button>}<Link className="button button-outline" to={valid ? `/orders?orderId=${encodeURIComponent(orderId)}` : "/orders"}>View order status</Link></div>;
  return <PaymentSession key={String(payment.orderId)} paymentData={payment} selectedAddressForCheckout={payment.deliveryAddress} verified={paymentHandoffAllowed({ isLoading, isFetching, error })} verificationError={Boolean(error)} onRetryVerification={refetch} />;
}

function PaymentSession({ paymentData, selectedAddressForCheckout, verified, verificationError, onRetryVerification }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState("opening");
  const lastOpened = useRef(null);
  const sessionId = paymentData?.paymentSessionId;
  const reopen = async () => {
    setState("checking");
    const result = await onRetryVerification();
    if (!result.error && result.data?.canResume) {
      setState("opening");
      setAttempt(value => value + 1);
    } else setState("error");
  };
  useEffect(() => {
    if (!sessionId || !verified) return;
    const key = `${sessionId}:${attempt}`;
    if (lastOpened.current === key) return;
    let active = true;
    const open = async () => {
      try {
        const cashfree = await load({ mode: "sandbox" });
        if (!active) return;
        lastOpened.current = key;
        setState("opening");
        const result = await cashfree.checkout({ paymentSessionId: sessionId, redirectTarget: "_self" });
        if (lastOpened.current === key) setState(result?.error ? "error" : "waiting");
      } catch {
        if (active || lastOpened.current === key) setState("error");
      }
    };
    open();
    return () => { active = false; };
  }, [sessionId, attempt, verified]);
  if (!sessionId) return <Navigate to="/checkout" replace />;
  return <div className="checkout-shell"><div className="site-container checkout-handoff"><div className="checkout-handoff-copy"><span className="checkout-result-icon"><FiLock aria-hidden="true" /></span><h1>{state === "error" ? "Payment could not open" : "Complete your payment"}</h1><p role="status">{!verified ? verificationError ? "Your payment details could not be verified. Retry the check before opening payment." : "Checking your payment session…" : state === "opening" ? "Opening your secure payment page…" : state === "error" ? "The payment page couldn’t open. Your order details are saved below. Retry this payment session or check your order status." : "If you haven’t reached the payment page, reopen your payment or check your order status before trying again."}</p>{verificationError && <p className="inline-error" role="alert">Payment verification failed. <button type="button" onClick={onRetryVerification}>Retry verification</button></p>}<div className="checkout-handoff-actions"><button className="button button-dark button-prominent" disabled={!verified || state === "opening" || state === "checking"} onClick={reopen}>Continue to payment <ActionArrow /></button><Link className="button button-outline" to={`/orders?orderId=${encodeURIComponent(paymentData.orderId)}`}>View order status</Link></div><Link className="text-link" to="/help">Need help with payment?</Link></div><aside className="checkout-summary"><h2>Payment details</h2><p className="checkout-session-order">Order {paymentData.orderId}</p><div className="checkout-payment-amount"><span>Amount to pay</span><strong>{formatCurrency(paymentData.totalAmount)}</strong></div><div className="checkout-payment-delivery"><h3>Delivering to</h3><DeliveryDetails address={selectedAddressForCheckout} /></div></aside></div></div>;
}
