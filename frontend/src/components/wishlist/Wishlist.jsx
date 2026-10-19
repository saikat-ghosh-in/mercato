import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { MdBookmarkBorder, MdShoppingBag } from "react-icons/md";
import api from "../../backend/api";
import ProductCard, { CardSkeleton } from "../product/ProductCard";

const Wishlist = () => {
    const navigate = useNavigate();
    const wishlist = useSelector(s => s.wishlist);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadWishlist = async () => {
        setLoading(true);
        setError(null);
        try {
            const { data } = await api.get("/wishlist");
            setProducts(data);
        } catch (err) {
            setError(err?.response?.data?.message || "Failed to load wishlist");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadWishlist();
    }, []);

    const visibleProducts = wishlist.loaded ? products.filter(p => wishlist.productIds.includes(p.productId)) : products;
    return (
        <div className="bg-gray-50 min-h-screen py-6">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-gray-50 rounded-xl">
                        <MdBookmarkBorder size={20} className="text-gray-600" />
                    </div>
                    <div>
                        <h1 className="text-xl font-bold text-gray-900">Wishlist</h1>
                        <p className="text-xs text-gray-500 mt-0.5">Items you&apos;ve saved for later</p>
                    </div>
                </div>

                {loading && !products.length ? (
                    <div className="product-grid">
                        {Array.from({ length: 4 }).map((_, i) => <CardSkeleton key={i} />)}
                    </div>
                ) : error ? (
                    <div className="text-center py-20">
                        <p className="text-sm text-gray-500 mb-4">{error}</p>
                        <button
                            onClick={loadWishlist}
                            className="control px-5 py-2.5 bg-blue-500 hover:bg-blue-600 text-white text-sm font-bold rounded-xl"
                        >
                            Try Again
                        </button>
                    </div>
                ) : visibleProducts.length === 0 ? (
                    <div className="text-center py-20">
                        <h2 className="text-lg font-bold text-gray-800 mb-1">Your wishlist is empty</h2>
                        <p className="text-sm text-gray-400 mb-6">Open a product and choose Save to wishlist to keep it here.</p>
                        <button
                            onClick={() => navigate("/products")}
                            className="control inline-flex items-center gap-2 bg-gray-950 hover:bg-gray-800 text-white text-sm font-bold px-6 py-2.5 rounded-xl"
                        >
                            <MdShoppingBag size={15} /> Browse Products
                        </button>
                    </div>
                ) : (
                    <div className="product-grid">
                        {visibleProducts.map((product) => (
                            <ProductCard
                                key={product.productId}
                                product={product}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default Wishlist;
