import { Link } from "react-router-dom";
import { FiArrowLeft, FiArrowRight, FiPackage } from "react-icons/fi";
import { formatCurrency } from "../../../utils/formatCurrency";
import DeliveryDetails from "../DeliveryDetails";
export default function ReviewStep({ selectedAddress, cartWithProducts, onBack, onNext, disabled }) {
  return <>
    <div className="checkout-section-heading"><div><p className="eyebrow">STEP 02 · REVIEW</p><h2 id="checkout-step-heading" tabIndex={-1}>Good finds. All accounted for.</h2><p>Check your items and delivery details before paying.</p></div></div>
    <section className="checkout-delivery-card"><div className="checkout-card-title"><h3>Delivering to</h3><button className="button checkout-edit" onClick={onBack}>Change</button></div><DeliveryDetails address={selectedAddress} /></section>
    <div className="checkout-card-title checkout-items-title"><h3>Your items</h3><Link className="button checkout-edit" to="/cart">Edit bag</Link></div>
    <ul className="checkout-review-items">{cartWithProducts.map(item => <li key={item.productId}><Link className="checkout-item-image" to={`/products/${item.productId}`} aria-label={`View ${item.product?.productName || "product"}`}>{item.product?.primaryImageUrl ? <img src={item.product.primaryImageUrl} alt="" width="96" height="112" /> : <FiPackage />}</Link><div className="checkout-item-info"><Link to={`/products/${item.productId}`}>{item.product?.productName || "Product information unavailable"}</Link>{item.product?.sellerName && <p>Sold by {item.product.sellerName}</p>}<p>Quantity {item.quantity}</p><strong>{formatCurrency(item.lineTotal)}</strong></div></li>)}</ul>
    <div className="checkout-step-actions"><button className="button checkout-back" onClick={onBack}><FiArrowLeft /> Delivery</button><button className="button button-dark button-prominent" onClick={onNext} disabled={disabled}>Continue to payment <FiArrowRight /></button></div>
  </>;
}
