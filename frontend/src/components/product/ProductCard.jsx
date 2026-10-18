import { Link } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import { useState } from "react";
import { FiPlus, FiMinus, FiShoppingBag, FiImage, FiTrash2 } from "react-icons/fi";
import { addToCart, updateCartItemQuantity, removeFromCart } from "../../reduxStore/actions/cartActions";
import { formatCurrency } from "../../utils/formatCurrency";
import WishlistToggle from "../shared/WishlistToggle";

export default function ProductCard({ product }) {
  const dispatch = useDispatch();
  const item = useSelector(s => s.cart.cartItems.find(i => i.productId === product.productId));
  const pending = useSelector(s => s.cart.cartLoading);
  const [busy, setBusy] = useState(false);
  const [brokenImage, setBrokenImage] = useState(false);
  const qty = item?.quantity ?? 0;
  const available = product.availableQty ?? 0;
  const inStock = available > 0 || (qty > 0 && !item?.outOfStock);
  const run = async action => { setBusy(true); try { await dispatch(action); } finally { setBusy(false); } };
  return <article className="product-card">
    <div className="product-image-wrap">
      <Link to={"/products/" + product.productId} tabIndex={-1} aria-hidden="true" className="product-image-link">
        {product.primaryImageUrl && !brokenImage ? <img src={product.primaryImageUrl} alt="" loading="lazy" onError={() => setBrokenImage(true)} /> : <div className="image-placeholder"><FiImage /><span>Image unavailable</span></div>}
      </Link>
      {Number(product.discountPercent) > 0 && <span className="product-badge">{Math.round(product.discountPercent)}% off</span>}
      <div className="product-save"><WishlistToggle productId={product.productId} /></div>
      {!inStock && <span className="stock-badge">Currently sold out</span>}
    </div>
    <div className="product-copy"><p className="product-category">{product.categoryName || "The collection"}</p>
      <h3><Link to={"/products/" + product.productId} title={product.productName}>{product.productName}</Link></h3>
      <p className="product-price"><strong>{formatCurrency(product.sellingPrice)}</strong>{Number(product.discountPercent) > 0 && <del>{formatCurrency(product.retailPrice)}</del>}</p>
      <div className="product-card-action">{qty > 0 ? <div className="card-stepper" aria-label={"Quantity for " + product.productName}>
        <button aria-label={"Remove one " + product.productName} disabled={busy || pending} onClick={() => run(qty === 1 ? removeFromCart(product.productId) : updateCartItemQuantity(product.productId, qty - 1))}>{qty === 1 ? <FiTrash2 /> : <FiMinus />}</button>
        <span aria-live="polite">{qty} in bag</span><button aria-label={"Add one " + product.productName} disabled={busy || pending || item?.canAddMore === false} onClick={() => run(updateCartItemQuantity(product.productId, qty + 1))}><FiPlus /></button>
      </div> : <button className="add-to-bag" disabled={!inStock || busy || pending} onClick={() => run(addToCart(product.productId, 1))}><FiShoppingBag />{busy ? "Adding…" : inStock ? "Add to bag" : "Sold out"}<FiPlus /></button>}</div>
    </div>
  </article>;
}

export const CardSkeleton = () => <div className="card-skeleton" aria-hidden="true"><div /><span /><span /><span /></div>;
