import { useSelector, useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { MdFavorite, MdFavoriteBorder } from "react-icons/md";
import { toggleWishlist } from "../../reduxStore/actions/wishlistActions";
import toast from "react-hot-toast";

const WishlistToggle = ({ productId, className = "" }) => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const user = useSelector((state) => state.auth?.user);
    const isWishlisted = useSelector((state) =>
        state.wishlist?.productIds?.includes(productId)
    );

    const handleClick = (e) => {
        e.stopPropagation();
        dispatch(toggleWishlist(productId));
    };

    const handleLoginPrompt = (e) => {
        e.stopPropagation();
        toast.error("Sign in to save items");
        navigate("/login");
    };

    return (
        <button
            type="button"
            onClick={user ? handleClick : handleLoginPrompt}
            className={`p-2 rounded-full bg-white/90 border border-gray-100 shadow-sm hover:scale-105 transition-transform cursor-pointer ${className}`}
            aria-label={isWishlisted ? "Remove from wishlist" : "Add to wishlist"}
            aria-pressed={Boolean(isWishlisted)}
        >
            {isWishlisted ? (
                <MdFavorite size={18} className="text-red-500" />
            ) : (
                <MdFavoriteBorder size={18} className="text-gray-500" />
            )}
        </button>
    );
};

export default WishlistToggle;
