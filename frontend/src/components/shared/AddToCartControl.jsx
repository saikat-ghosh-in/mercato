import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { MdShoppingCart } from "react-icons/md";
import { FiMinus, FiPlus } from "react-icons/fi";
import { HiOutlineTrash } from "react-icons/hi";
import { addToCart, updateCartItemQuantity, removeFromCart } from "../../reduxStore/actions/cartActions";
import { selectCartItemByProductId, selectCartPendingSync } from "../../reduxStore/selectors/cartSelectors";

const AddToCartControl = ({ product, stopPropagation = false, className = "", showOnlyQty = false }) => {
    const dispatch = useDispatch();
    const navigate = useNavigate();

    const cartItem = useSelector(selectCartItemByProductId(product.productId));
    const pendingSync = useSelector(selectCartPendingSync);
    const [optimisticLoading, setOptimisticLoading] = useState(false);

    const cartQty = cartItem?.quantity ?? 0;
    const inCart = cartQty > 0;
    const inStock = product.availableQty > 0;

    const canAddMore = inCart ? (cartItem?.canAddMore ?? true) : inStock;

    const wrap = (fn) => (e) => {
        if (stopPropagation) e.stopPropagation();
        fn(e);
    };

    const handleAdd = wrap(async () => {
        setOptimisticLoading(true);
        try {
            await dispatch(addToCart(product.productId, 1));
        } finally {
            setOptimisticLoading(false);
        }
    });

    const handleIncrease = wrap(() => {
        if (!canAddMore || pendingSync) return;
        dispatch(updateCartItemQuantity(product.productId, cartQty + 1));
    });

    const handleDecrease = wrap(() => {
        if (cartQty <= 1) {
            dispatch(removeFromCart(product.productId));
        } else {
            dispatch(updateCartItemQuantity(product.productId, cartQty - 1));
        }
    });

    const handleViewCart = wrap((e) => {
        e.stopPropagation();
        navigate("/cart");
    });

    if (inCart) {
        return (
            <div className={`h-9 min-w-45 flex items-center border border-gray-200 rounded-xl overflow-hidden bg-white ${className}`}>
                <button
                    onClick={handleDecrease}
                    aria-label={cartQty === 1 ? "Remove item" : "Decrease quantity"}
                    disabled={pendingSync}
                    className={`w-1/4 h-9 flex items-center justify-center transition-colors cursor-pointer shrink-0 text-gray-400
                        disabled:opacity-50 disabled:cursor-not-allowed
                        ${cartQty === 1 ? "hover:bg-gray-100 hover:text-red-500" : "hover:bg-gray-100 hover:text-gray-900"}`}
                >
                    {cartQty === 1 ? <HiOutlineTrash size={14} /> : <FiMinus size={14} />}
                </button>

                <div className="w-1/2 flex items-center justify-center gap-1 px-1 min-w-0 overflow-hidden">
                    <span className="text-sm font-bold text-gray-700 shrink-0">{cartQty}</span>
                    {!showOnlyQty && (
                        <span className="text-xs font-semibold text-gray-500 whitespace-nowrap overflow-hidden text-ellipsis">
                            {" in "}
                            <button
                                onClick={handleViewCart}
                                className="font-bold text-gray-700 hover:text-blue-600 underline underline-offset-2 cursor-pointer transition-colors"
                            >
                                Cart
                            </button>
                        </span>
                    )}
                </div>

                <button
                    onClick={handleIncrease}
                    aria-label="Increase quantity"
                    disabled={!canAddMore || pendingSync}
                    className="w-1/4 h-9 flex items-center justify-center text-gray-400 hover:bg-gray-100 hover:text-gray-900
                    transition-colors cursor-pointer shrink-0 disabled:opacity-30 disabled:cursor-not-allowed
                    disabled:hover:bg-transparent disabled:hover:text-gray-400"
                >
                    <FiPlus size={14} />
                </button>
            </div>
        );
    }

    return (
        <button
            onClick={handleAdd}
            disabled={!inStock || optimisticLoading || pendingSync}
            className={`flex items-center justify-center gap-1.5 bg-gray-950 hover:bg-gray-800 active:scale-[0.98]
                disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed disabled:active:scale-100
                text-white font-bold rounded-xl transition-all cursor-pointer text-xs ${className}`}
        >
            <MdShoppingCart size={14} />
            {optimisticLoading ? "Adding..." : !inStock ? "Out of Stock" : "Add to Cart"}
        </button>
    );
};

export default AddToCartControl;
