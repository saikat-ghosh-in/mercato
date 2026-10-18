import { useDispatch, useSelector } from "react-redux";
import { Link } from "react-router-dom";
import { FiMinus, FiPlus, FiTrash2 } from "react-icons/fi";
import { addToCart, updateCartItemQuantity, removeFromCart } from "../../reduxStore/actions/cartActions";
import { selectCartItemByProductId } from "../../reduxStore/selectors/cartSelectors";

export default function AddToCartControl({ product, stopPropagation = false, className = "", showOnlyQty = false }) {
  const dispatch = useDispatch();
  const item = useSelector(selectCartItemByProductId(product.productId));
  const pending = useSelector(s => Boolean(s.cart.pendingProducts?.[product.productId]));
  const qty = item?.quantity ?? 0;
  const run = action => e => { if (stopPropagation) e.stopPropagation(); dispatch(action); };
  return qty > 0 ? <div className={"quantity-control " + className} aria-label="Bag quantity" aria-busy={pending}>
    <button className="icon-action" aria-label={qty === 1 ? "Remove item" : "Decrease quantity"} disabled={pending} onClick={run(qty === 1 ? removeFromCart(product.productId) : updateCartItemQuantity(product.productId, qty - 1))}>{qty === 1 ? <FiTrash2 /> : <FiMinus />}</button>
    <span aria-live="polite">{qty}{!showOnlyQty && <> in <Link to="/cart">bag</Link></>}</span>
    <button className="icon-action" aria-label="Increase quantity" disabled={pending || item.canAddMore === false || item.outOfStock} onClick={run(updateCartItemQuantity(product.productId, qty + 1))}><FiPlus /></button>
  </div> : <button className={"button button-prominent button-dark " + className} disabled={pending || product.availableQty <= 0} onClick={run(addToCart(product.productId))}>{pending ? "Adding…" : product.availableQty <= 0 ? "Sold out" : "Add to bag"}</button>;
}
