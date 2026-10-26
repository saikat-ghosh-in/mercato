import { Link } from "react-router-dom";
import { FiPackage } from "react-icons/fi";
import { formatCurrency } from "../../../utils/formatCurrency";
import DeliveryDetails from "../DeliveryDetails";
import DeliveryMethods from "../../shared/DeliveryMethods";
import { CancellationPolicy } from "../../orders/WorkflowControls";
import { paymentPolicy } from "../../../utils/fulfillment";

export default function ReviewPaymentStep({ selectedAddress, cartWithProducts, onBack, onEditAddress, loading }) {
  return <>
    <div className="checkout-section-heading"><div><h2 id="checkout-step-heading" tabIndex={-1}>Review your order.</h2></div></div>
    <section className="checkout-delivery-card"><div className="checkout-card-title"><h3>Delivering to {selectedAddress.defaultAddress && <span className="status-tag">Default</span>}</h3><div className="checkout-inline-actions"><button className="button checkout-edit" disabled={loading} onClick={() => onEditAddress(selectedAddress)}>Edit address</button><button className="button checkout-edit" disabled={loading} onClick={onBack}>Change</button></div></div><DeliveryDetails address={selectedAddress} /></section>
    <DeliveryMethods disabled={loading} />
    <div className="checkout-card-title checkout-items-title"><h3>Your items</h3><Link className="button checkout-edit" to="/cart">Edit bag</Link></div>
    <ul className="checkout-review-items">{cartWithProducts.map(item => <li key={item.productId}><Link className="checkout-item-image" to={`/products/${item.productId}`} aria-label={`View ${item.product?.productName || "product"}`}>{item.product?.primaryImageUrl ? <img src={item.product.primaryImageUrl} alt="" width="96" height="112" /> : <FiPackage />}</Link><div className="checkout-item-info"><Link to={`/products/${item.productId}`}>{item.product?.productName || "Product information unavailable"}</Link>{item.product?.sellerName && <p>Sold by {item.product.sellerName}</p>}<p>Quantity {item.quantity}</p><strong>{formatCurrency(item.lineTotal)}</strong></div></li>)}</ul>
    <section className="surface p-4 space-y-3" aria-label="Payment and cancellation policy"><h3 className="text-sm font-semibold">Payment and cancellations</h3><p className="text-sm text-gray-600">{paymentPolicy}</p><CancellationPolicy /></section>
    <Link className="button checkout-back" to="/cart">Back to bag</Link>
  </>;
}
