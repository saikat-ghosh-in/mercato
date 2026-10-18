import { FiArrowLeft, FiArrowUpRight, FiCreditCard, FiLock, FiMapPin } from "react-icons/fi";
import { formatCurrency } from "../../../utils/formatCurrency";
import DeliveryDetails from "../DeliveryDetails";
export default function PaymentStep({ onBack, onSubmit, onChangeAddress, selectedAddress, total, loading, disabled, error }) {
  return <>
    <div className="checkout-section-heading"><div><p className="eyebrow">STEP 03 · PAYMENT</p><h2 id="checkout-step-heading" tabIndex={-1}>One last step. Make it yours.</h2><p>Your payment details are entered securely with Cashfree.</p></div></div>
    <div className="checkout-payment-card"><div className="checkout-payment-mark"><FiCreditCard aria-hidden="true" /><span>Cashfree<span>Secure payment partner</span></span><FiLock aria-hidden="true" /></div><h3>Choose how you pay, next.</h3><p>Continue to Cashfree to see available payment methods and complete your purchase. You’ll return to Mercato for your order status.</p><div className="checkout-payment-amount"><span>Amount to pay</span><strong>{formatCurrency(total)}</strong></div></div>
    <section className="checkout-payment-delivery"><div className="checkout-card-title"><h3><FiMapPin aria-hidden="true" /> Delivery address</h3><button className="button checkout-edit" disabled={loading} onClick={onChangeAddress}>Change</button></div><DeliveryDetails address={selectedAddress} /></section>
    {error && <div className="checkout-payment-error" role="alert"><strong>We couldn’t start your payment.</strong><p>{error}</p><p>Your delivery details are still here. Please try again.</p></div>}
    <div className="checkout-step-actions"><button className="button checkout-back" disabled={loading} onClick={onBack}><FiArrowLeft /> Review</button><button className="button button-dark button-prominent checkout-pay" onClick={onSubmit} disabled={loading || disabled} aria-busy={loading}><span>Continue to Cashfree</span>{loading ? <span className="checkout-spinner" aria-hidden="true" /> : <FiArrowUpRight aria-hidden="true" />}</button></div><p className="checkout-payment-note" role="status">{loading ? "Preparing your secure payment…" : "Your order total is confirmed again before payment."}</p>
  </>;
}
