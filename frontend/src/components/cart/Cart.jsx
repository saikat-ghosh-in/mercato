import { Link } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import { FiArrowLeft, FiArrowRight, FiShield, FiShoppingBag, FiTruck } from "react-icons/fi";
import { useCartProducts } from "../../hooks/useCartProducts";
import { syncCartFromBackend } from "../../reduxStore/actions/cartActions";
import { formatCurrency } from "../../utils/formatCurrency";
import CartItem from "./CartItem";
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
  const threshold = Number(import.meta.env.VITE_FREE_SHIPPING_ABOVE || 999);
  return <div className="site-container pb-16">
    <div className="page-heading"><p className="eyebrow"><Link to="/">HOME</Link> / YOUR BAG</p><h1>A few good choices.</h1><p>{qty} item{qty === 1 ? "" : "s"} in your shopping bag.</p></div>
    {cartError && <div className="state-panel mb-6" role="alert"><h3>Your bag couldn’t be refreshed.</h3><p>{cartError}</p><button className="button button-dark" onClick={() => dispatch(syncCartFromBackend())}>Refresh bag</button></div>}
    {!cartLoaded && items.length === 0 ? <div className="state-panel" role="status">Loading your bag…</div> : items.length === 0 ? <div className="state-panel"><FiShoppingBag /><h3>Make room for something good.</h3><p>Your bag is waiting for its first favourite.</p><Link className="button button-dark" to="/products">Explore the collection <FiArrowRight /></Link></div>
      : <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
        <section className="lg:col-span-2 bg-white border border-gray-200 rounded-lg overflow-hidden" aria-label="Shopping bag items" aria-busy={cartLoading}>
          <div className="px-6 py-4 border-b border-gray-100 flex items-center gap-2 text-xs text-blue-700"><FiTruck />{subtotal > threshold ? "Your order qualifies for free delivery." : "Add " + formatCurrency(Math.max(0, threshold - subtotal + .01)) + " more for free delivery."}</div>
          {enriched.map(item => <CartItem key={item.productId} productId={item.productId} product={item.product} />)}
          {failed && <p className="px-6 py-4 text-xs text-red-700" role="alert">Some product details could not load. Refresh your bag before checkout.</p>}
          <div className="px-6 py-5"><Link className="text-link" to="/products"><FiArrowLeft />Keep discovering</Link></div>
        </section>
        <aside className="bg-white border border-gray-200 rounded-lg p-6 lg:sticky lg:top-40" aria-label="Order summary">
          <h2 className="text-lg font-medium mb-6">The details</h2>
          <dl className="space-y-4 text-xs">
            <div className="flex justify-between gap-4"><dt>Subtotal</dt><dd>{formatCurrency(subtotal)}</dd></div>
            <div className="flex justify-between gap-4"><dt>Delivery</dt><dd>{shipping === 0 ? "On us" : formatCurrency(shipping)}</dd></div>
            <div className="flex justify-between gap-4"><dt>Platform fee</dt><dd>{formatCurrency(platformFee)}</dd></div>
            {processingAndHandling > 0 && <div className="flex justify-between gap-4"><dt>Processing & handling</dt><dd>{formatCurrency(processingAndHandling)}</dd></div>}
            <div className="flex justify-between gap-4 border-t border-gray-200 pt-5 text-base font-semibold"><dt>Total</dt><dd>{formatCurrency(total)}</dd></div>
          </dl>
          <p className="text-[10px] text-gray-500 mt-2 mb-6">Product prices include applicable taxes.</p>
          {cartLoading || loading || failed || cartError ? <button className="button button-dark w-full" disabled>{cartLoading || loading ? "Updating your bag…" : "Refresh bag to continue"}</button> : <Link className="button button-dark w-full" to="/checkout">Continue to checkout <FiArrowRight /></Link>}
          <p className="flex items-center justify-center gap-2 text-[10px] text-gray-500 mt-4"><FiShield />Secure checkout with Cashfree</p>
        </aside>
      </div>}
  </div>;
}
