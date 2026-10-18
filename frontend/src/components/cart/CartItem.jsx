import { Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { FiTrash2, FiImage } from "react-icons/fi";
import { updateCartItemQuantity, removeFromCart } from "../../reduxStore/actions/cartActions";
import { formatCurrency } from "../../utils/formatCurrency";
import { selectCartItemByProductId } from "../../reduxStore/selectors/cartSelectors";
import QuantityStepper from "../shared/QuantityStepper";

export default function CartItem({ productId, product }) {
  const dispatch = useDispatch();
  const item = useSelector(selectCartItemByProductId(productId));
  const pending = useSelector(s => Boolean(s.cart.pendingProducts?.[productId] || s.cart.pendingProducts?.["*"]));
  if (!item) return null;
  return <div className="bag-item">
    <Link className="bag-image" to={"/products/" + productId}>{product?.primaryImageUrl ? <img src={product.primaryImageUrl} alt={product.productName} /> : <FiImage />}</Link>
    <div className="bag-item-copy"><p className="product-category">{product?.categoryName}</p><Link className="bag-product-name" to={"/products/" + productId}>{product?.productName || "Item unavailable"}</Link>
      <p className="product-price">{formatCurrency(item.itemPrice)} each</p>
      <button className="bag-remove" disabled={pending} onClick={() => dispatch(removeFromCart(productId))}><FiTrash2 /> Remove</button>
    </div>
    <div className="bag-quantity"><QuantityStepper quantity={item.quantity} pendingSync={pending} canAddMore={item.canAddMore}
      onIncrease={() => dispatch(updateCartItemQuantity(productId, item.quantity + 1))}
      onDecrease={() => dispatch(item.quantity === 1 ? removeFromCart(productId) : updateCartItemQuantity(productId, item.quantity - 1))} /></div>
    <strong className="bag-line-total">{formatCurrency(item.lineTotal)}</strong>
  </div>;
}
