import { Link } from "react-router-dom";
import { FiShoppingBag } from "react-icons/fi";
import { formatCurrency } from "../../utils/formatCurrency";
import PaymentAction from "./PaymentAction";
export default function OrderSummary({ cartWithProducts, cartQty, subtotal, shipping, platformFee, processingAndHandling, total, onPay, paymentLoading, paymentDisabled, paymentError }) {
  return <aside className="checkout-summary" aria-label="Order summary"><div className="checkout-card-title"><h2>Order summary</h2><Link className="button checkout-edit" to="/cart">Edit bag</Link></div><p className="checkout-summary-count">{cartQty} {cartQty === 1 ? "item" : "items"} in your bag</p>
    {!onPay && <div className="checkout-summary-previews">{cartWithProducts.slice(0, 3).map(item => <Link key={item.productId} to={`/products/${item.productId}`} aria-label={`View ${item.product?.productName || "product"}`}>{item.product?.primaryImageUrl ? <img src={item.product.primaryImageUrl} alt="" width="80" height="88" /> : <FiShoppingBag />}<span>×{item.quantity}</span></Link>)}{cartWithProducts.length > 3 && <span className="checkout-more-items">+{cartWithProducts.length - 3}</span>}</div>}
    <dl className="checkout-charges">{[["Subtotal", subtotal], ["Delivery", shipping], ["Platform fee", platformFee], ["Processing & handling", processingAndHandling]].filter(([label, value]) => label === "Subtotal" || label === "Delivery" || Number(value) > 0).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{label === "Delivery" && Number(value) === 0 ? "Free" : formatCurrency(value)}</dd></div>)}<div className="checkout-total"><dt>Total</dt><dd>{formatCurrency(total)}</dd></div></dl>
    {onPay && <div className="checkout-summary-payment"><PaymentAction onSubmit={onPay} loading={paymentLoading} disabled={paymentDisabled} describedBy={paymentError ? "checkout-desktop-payment-error" : undefined} /><p className="checkout-summary-note">Choose your payment method on the next screen.</p>{paymentError && <p className="checkout-action-error" id="checkout-desktop-payment-error" role="alert">{paymentError}</p>}</div>}
    <Link className="text-link checkout-help" to="/help">Need a hand? Visit Help</Link>
  </aside>;
}
