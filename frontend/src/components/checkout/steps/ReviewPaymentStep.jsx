import { Link } from "react-router-dom";
import { FiArrowUpRight, FiCreditCard, FiLock, FiPackage } from "react-icons/fi";
import { formatCurrency } from "../../../utils/formatCurrency";
import DeliveryDetails from "../DeliveryDetails";
import DeliveryMethods from "../../shared/DeliveryMethods";

export default function ReviewPaymentStep({ selectedAddress, cartWithProducts, onBack, onEditAddress, onSubmit, total, loading, disabled, error }) {
  return <>
    <div className="checkout-section-heading"><div><p className="eyebrow">REVIEW & PAY</p><h2 id="checkout-step-heading" tabIndex={-1}>Ready when you are.</h2><p>Your delivery, items and payment in one place.</p></div></div>
    <section className="checkout-delivery-card"><div className="checkout-card-title"><h3>Delivering to {selectedAddress.defaultAddress && <span className="status-tag">Default</span>}</h3><div className="checkout-inline-actions"><button className="button checkout-edit" disabled={loading} onClick={() => onEditAddress(selectedAddress)}>Edit address</button><button className="button checkout-edit" disabled={loading} onClick={onBack}>Change</button></div></div><DeliveryDetails address={selectedAddress} /></section>
    <DeliveryMethods disabled={loading} />
    <div className="checkout-card-title checkout-items-title"><h3>Your items</h3><Link className="button checkout-edit" to="/cart">Edit bag</Link></div>
    <ul className="checkout-review-items">{cartWithProducts.map(item => <li key={item.productId}><Link className="checkout-item-image" to={`/products/${item.productId}`} aria-label={`View ${item.product?.productName || "product"}`}>{item.product?.primaryImageUrl ? <img src={item.product.primaryImageUrl} alt="" width="96" height="112" /> : <FiPackage />}</Link><div className="checkout-item-info"><Link to={`/products/${item.productId}`}>{item.product?.productName || "Product information unavailable"}</Link>{item.product?.sellerName && <p>Sold by {item.product.sellerName}</p>}<p>Quantity {item.quantity}</p><strong>{formatCurrency(item.lineTotal)}</strong></div></li>)}</ul>
    <section className="checkout-payment-card"><div className="checkout-payment-mark"><FiCreditCard aria-hidden="true" /><span>Pay securely with Cashfree<span>Choose your payment method on the next screen</span></span><FiLock aria-hidden="true" /></div><div className="checkout-payment-amount"><span>Amount to pay</span><strong>{formatCurrency(total)}</strong></div><p>You’ll return to Mercato for your order status after payment.</p></section>
    {error && <div className="checkout-payment-error" role="alert"><strong>We couldn’t start your payment.</strong><p>{error}</p><p>Your details are retained. Please try again.</p></div>}
    <div className="checkout-step-actions"><Link className="button checkout-back" to="/cart">Back to bag</Link><button className="button button-dark button-prominent checkout-pay" onClick={onSubmit} disabled={loading || disabled} aria-busy={loading}><span>Continue to Cashfree</span>{loading ? <span className="checkout-spinner" aria-hidden="true" /> : <FiArrowUpRight aria-hidden="true" />}</button></div><p className="checkout-payment-note" role="status">{loading ? "Preparing your secure payment…" : "Your latest bag total and delivery are confirmed before payment."}</p>
  </>;
}
