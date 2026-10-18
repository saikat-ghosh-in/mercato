import { Link } from "react-router-dom";
import { FiShoppingBag } from "react-icons/fi";
import { formatCurrency } from "../../utils/formatCurrency";
export default function OrderSummary({ cartWithProducts, cartQty, subtotal, shipping, platformFee, processingAndHandling, total }) {
  return <aside className="checkout-summary" aria-label="Order summary"><div className="checkout-card-title"><h2>Order summary</h2><Link className="button checkout-edit" to="/cart">Edit bag</Link></div><p className="checkout-summary-count">{cartQty} {cartQty === 1 ? "item" : "items"} in your bag</p>
    <div className="checkout-summary-previews" aria-hidden="true">{cartWithProducts.slice(0, 3).map(item => <div key={item.productId}>{item.product?.primaryImageUrl ? <img src={item.product.primaryImageUrl} alt="" width="80" height="88" /> : <FiShoppingBag />}<span>×{item.quantity}</span></div>)}{cartWithProducts.length > 3 && <span className="checkout-more-items">+{cartWithProducts.length - 3}</span>}</div>
    <dl className="checkout-charges">{[["Subtotal", subtotal], ["Shipping", shipping], ["Platform fee", platformFee], ["Processing & handling", processingAndHandling]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{label === "Shipping" && value === 0 ? "Free" : formatCurrency(value)}</dd></div>)}<div className="checkout-total"><dt>Total</dt><dd>{formatCurrency(total)}</dd></div></dl><p className="checkout-summary-note">Charges shown are calculated for your current bag.</p><Link className="text-link checkout-help" to="/help">Need a hand? Visit Help</Link>
  </aside>;
}
