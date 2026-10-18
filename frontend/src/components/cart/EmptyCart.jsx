import { useNavigate } from "react-router-dom";
import { MdShoppingCart } from "react-icons/md";
import { FiArrowRight } from "react-icons/fi";

const SUGGESTIONS = [
    { label: "Electronics",   emoji: "💻" },
    { label: "Fashion",       emoji: "👗" },
    { label: "Home & Kitchen",emoji: "🏠" },
    { label: "Baby Products", emoji: "🍼" },
];

const EmptyCart = () => {
    const navigate = useNavigate();

    return (
        <div className="bg-gray-50 min-h-screen flex flex-col items-center justify-center px-4 py-16">

            {/* Icon */}
            <div className="relative mb-6">
                <div className="w-24 h-24 rounded-full bg-blue-50 flex items-center justify-center">
                    <MdShoppingCart size={44} className="text-blue-300" />
                </div>
                {/* Empty badge */}
                <span className="absolute -top-1 -right-1 w-7 h-7 rounded-full bg-white border-2 border-gray-100 flex items-center justify-center text-sm shadow-sm">
                    0
                </span>
            </div>

            {/* Text */}
            <h1 className="text-2xl font-black text-gray-900 mb-2">Your cart is empty</h1>
            <p className="text-sm text-gray-400 text-center max-w-xs mb-8">
                Looks like you haven't added anything yet. Browse our products and find something you love!
            </p>

            {/* CTA */}
            <button
                onClick={() => navigate("/products")}
                className="control inline-flex items-center gap-2 bg-blue-500 hover:bg-blue-600 active:scale-[0.98] text-white font-bold px-6 py-3 rounded-xl text-sm transition-[transform,opacity,background-color] shadow-sm cursor-pointer mb-10"
            >
                Start Shopping <FiArrowRight size={16} />
            </button>

            {/* Category suggestions */}
            <div className="w-full max-w-sm">
                <p className="text-xs font-bold text-gray-400 uppercase tracking-widest text-center mb-3">
                    Browse by category
                </p>
                <div className="grid grid-cols-2 gap-3">
                    {SUGGESTIONS.map(({ label, emoji }) => (
                        <button
                            key={label}
                            onClick={() => navigate(`/products?category=${encodeURIComponent(label)}`)}
                            className="control flex items-center gap-3 bg-white border border-gray-100 hover:border-blue-200 hover:bg-blue-50 rounded-2xl px-4 py-3 text-sm font-semibold text-gray-700 hover:text-blue-600 transition-[transform,opacity,background-color] cursor-pointer shadow-sm"
                        >
                            <span className="text-xl">{emoji}</span>
                            {label}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default EmptyCart;