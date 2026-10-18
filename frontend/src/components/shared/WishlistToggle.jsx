import { useSelector, useDispatch } from "react-redux";
import { useNavigate, useLocation } from "react-router-dom";
import { FiHeart } from "react-icons/fi";
import { toggleWishlist } from "../../reduxStore/actions/wishlistActions";
import { isTokenExpired } from "../../utils/tokenManager";
export default function WishlistToggle({ productId, className = "", showLabel = false }) {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useSelector(s => s.auth?.user);
  const saved = useSelector(s => s.wishlist?.productIds?.includes(productId));
  const pending = useSelector(s => Boolean(s.wishlist?.pending?.[productId]));
  const label = saved ? "Remove from wishlist" : "Save to wishlist";
  return <button type="button" disabled={pending} onClick={e => { e.stopPropagation(); if (!user?.token || isTokenExpired(user.tokenExpirationTime)) navigate("/login?redirect=" + encodeURIComponent(location.pathname + location.search)); else dispatch(toggleWishlist(productId)); }} className={(showLabel ? "button button-outline w-full mt-4 " : "icon-button ") + className} aria-label={showLabel ? label : saved ? "Remove from wishlist" : "Add to wishlist"} aria-pressed={Boolean(saved)}><FiHeart fill={saved ? "currentColor" : "none"} />{showLabel && label}</button>;
}
