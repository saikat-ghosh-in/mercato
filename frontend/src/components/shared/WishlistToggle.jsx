import { useSelector, useDispatch } from "react-redux";
import { useNavigate, useLocation } from "react-router-dom";
import { FiHeart } from "react-icons/fi";
import { toggleWishlist } from "../../reduxStore/actions/wishlistActions";
import { isTokenExpired } from "../../utils/tokenManager";
export default function WishlistToggle({ productId }) {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useSelector(s => s.auth?.user);
  const saved = useSelector(s => s.wishlist?.productIds?.includes(productId));
  const pending = useSelector(s => Boolean(s.wishlist?.pending?.[productId]));
  const label = saved ? "Remove from wishlist" : "Save to wishlist";
  return <button type="button" disabled={pending} onClick={() => { if (!user?.token || isTokenExpired(user.tokenExpirationTime)) navigate("/login?redirect=" + encodeURIComponent(location.pathname + location.search)); else dispatch(toggleWishlist(productId)); }} className="button button-outline w-full mt-4" aria-label={label} aria-pressed={Boolean(saved)}><FiHeart fill={saved ? "currentColor" : "none"} />{label}</button>;
}
