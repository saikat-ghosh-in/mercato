import ActionArrow from "../shared/ActionArrow";
import { Link } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import { FiArrowLeft, FiArrowRight, FiShield, FiShoppingBag } from "react-icons/fi";
import { useCartProducts } from "../../hooks/useCartProducts";
import { syncCartFromBackend } from "../../reduxStore/actions/cartActions";
import { formatCurrency } from "../../utils/formatCurrency";
import CartItem from "./CartItem";
import DeliveryMethods from "../shared/DeliveryMethods";
import { selectCartItems, selectCartQty, selectCharges, selectSubtotal, selectTotal } from "../../reduxStore/selectors/cartSelectors";

export default function Cart() {
  const dispatch = useDispatch();
  const items = useSelector(selectCartItems);
  const qty = useSelector(selectCartQty);
  const subtotal = useSelector(selectSubtotal);
  const total = useSelector(selectTotal);
  const { shipping, platformFee, processingAndHandling } = useSelector(selectCharges);
  const { cartLoading, cartError, cartLoaded } = useSelector(s => s.cart);
  const { items: enriched, loading, failed } = useCartProducts(items);
  const checkoutBlocked = cartLoading || loading || failed || Boolean(cartError);

  return <div className="site-container pb-16">
    <div className="page-heading"><p className="eyebrow"><Link to="/">HOME</Link> / YOUR BAG</p><h1>Bag</h1><p>{qty} item{qty === 1 ? "" : "s"} in your shopping bag.</p></div>
    {cartError && <div className="state-panel mb-6" role="alert"><h3>Your bag couldn’t be refreshed.</h3><p>{cartError}</p><button className="button button-dark" onClick={() => dispatch(syncCartFromBackend())}>Refresh bag</button></div>}
    {!cartLoaded && items.length === 0 ? <div className="state-panel" role="status">Loading your bag…</div> : items.length === 0 ? <div className="state-panel"><FiShoppingBag /><h3>Make room for something good.</h3><p>Your bag is waiting for its first favourite.</p><Link className="button button-dark" to="/products">Explore the collection <ActionArrow /></Link></div>
      : <div className="bag-layout">
        <section className="bag-items" aria-label="Shopping bag items" aria-busy={cartLoading}>
          {enriched.map(item => <CartItem key={item.productId} productId={item.productId} product={item.product} />)}
          {failed && <p className="px-6 py-4 text-xs text-red-700" role="alert">Some product details could not load. Refresh your bag before checkout.</p>}
          <div className="px-6 py-5"><Link className="text-link" to="/products"><FiArrowLeft />Keep discovering</Link></div>
          <DeliveryMethods />
        </section>
        <aside className="order-summary" aria-label="Order summary">
          <h2 className="text-lg font-medium mb-6">Summary</h2>
          <dl className="space-y-4 text-xs">
            <div className="flex justify-between gap-4"><dt>Subtotal</dt><dd>{formatCurrency(subtotal)}</dd></div>
            <div className="flex justify-between gap-4"><dt>Delivery</dt><dd>{shipping === 0 ? "On us" : formatCurrency(shipping)}</dd></div>
            <div className="flex justify-between gap-4"><dt>Platform fee</dt><dd>{formatCurrency(platformFee)}</dd></div>
            {processingAndHandling > 0 && <div className="flex justify-between gap-4"><dt>Processing & handling</dt><dd>{formatCurrency(processingAndHandling)}</dd></div>}
            <div className="flex justify-between gap-4 border-t border-gray-200 pt-5 text-base font-semibold"><dt>Total</dt><dd>{formatCurrency(total)}</dd></div>
          </dl>
          <p className="text-[10px] text-gray-500 mt-2 mb-6">Product prices include applicable taxes.</p>
          <Link className="button button-prominent button-dark cart-checkout w-full" to="/checkout" aria-disabled={checkoutBlocked} aria-busy={cartLoading || loading} onClick={event => { if (checkoutBlocked) event.preventDefault(); }}>Continue to checkout <FiArrowRight /></Link>
          <p className="sr-only" role="status">{cartLoading || loading ? "Updating your bag. Checkout will be available when the update finishes." : failed || cartError ? "Refresh your bag before checkout." : "Your bag is ready for checkout."}</p>
          <p className="flex items-center justify-center gap-2 text-[10px] text-gray-500 mt-4"><FiShield />Secure checkout with Cashfree</p>
        </aside>
      </div>}
  </div>;
}
