import { useSelector, useDispatch } from "react-redux";
import { useNavigate, useLocation } from "react-router-dom";
import { FiHeart } from "react-icons/fi";
import { toggleWishlist } from "../../reduxStore/actions/wishlistActions";
import { fetchWishlistIds } from "../../reduxStore/actions/wishlistActions";
export default function WishlistToggle({ productId }) {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useSelector(s => s.auth?.user);
  const saved = useSelector(s => s.wishlist?.productIds?.includes(productId));
  const pending = useSelector(s => Boolean(s.wishlist?.pending?.[productId]));
  const { loaded, error, readId } = useSelector(s => s.wishlist);
  const label = saved ? "Remove from wishlist" : "Save to wishlist";
  return <button type="button" disabled={pending || Boolean(user && !loaded && !error) || Boolean(readId)} onClick={() => { if (!user) navigate("/login?redirect=" + encodeURIComponent(location.pathname + location.search)); else if (!loaded) dispatch(fetchWishlistIds()); else dispatch(toggleWishlist(productId)); }} className="button button-outline w-full mt-4" aria-label={label} aria-pressed={Boolean(saved)}><FiHeart fill={saved ? "currentColor" : "none"} />{user && !loaded ? error ? "Retry saved items" : "Loading saved items…" : label}</button>;
}
