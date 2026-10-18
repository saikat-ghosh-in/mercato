import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import {
    MdStorefront, MdCategory,
    MdArrowBack, MdArrowForward
} from "react-icons/md";
import toast from "react-hot-toast";
import { clearProductToDisplay, setProductToDisplay } from "../../reduxStore/actions/productActions";
import { useSubHeader } from "../../hooks/useSubHeader";
import { formatDate } from "../../utils/formatDate";
import { formatCurrency } from "../../utils/formatCurrency";
import ProductCard from "./ProductCard";
import AddToCartControl from "../shared/AddToCartControl";
import WishlistToggle from "../shared/WishlistToggle";
import { selectCartItemByProductId } from "../../reduxStore/selectors/cartSelectors";
import api from "../../backend/api";
import ImageGallery from "./ImageGallery";

const LOW_STOCK_THRESHOLD = 5;

const Skeleton = () => (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-10 animate-pulse">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">
            <div className="bg-gray-100 rounded-2xl aspect-square" />
            <div className="space-y-4 pt-4">
                <div className="h-3 bg-gray-100 rounded w-1/4" />
                <div className="h-8 bg-gray-100 rounded w-3/4" />
                <div className="h-8 bg-gray-100 rounded w-1/2" />
                <div className="h-24 bg-gray-100 rounded w-full mt-6" />
                <div className="h-12 bg-gray-100 rounded w-full mt-4" />
                <div className="grid grid-cols-3 gap-3 mt-4">
                    {[1, 2, 3].map((i) => <div key={i} className="h-16 bg-gray-100 rounded-xl" />)}
                </div>
            </div>
        </div>
    </div>
);

const ProductDetailsPage = () => {
    const { productId } = useParams();
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const { setSubHeader } = useSubHeader();
    const [related, setRelated] = useState([]);
    const [loading, setLoading] = useState(true);
    const [stickyVisible, setStickyVisible] = useState(false);

    const product = useSelector(s => s.products.productToDisplay);
    const cartItem = useSelector(selectCartItemByProductId(productId));

    useEffect(() => {
        const controller = new AbortController();
        const fetchProduct = async () => {
            try {
                setLoading(true);
                await dispatch(setProductToDisplay(productId, controller.signal));
            } catch {
                if (controller.signal.aborted) return;
                toast.error("Product not found");
                navigate("/products");
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        };
        fetchProduct();
        return () => { controller.abort(); dispatch(clearProductToDisplay()); };
    }, [productId, dispatch, navigate]);

    useEffect(() => {
        if (loading || !product?.categoryName) return;

        const fetchRelated = async () => {
            try {
                const { data } = await api.get("/public/products", {
                    params: {
                        category: product.categoryName,
                        pageSize: 5,
                        pageNumber: 0,
                        sortBy: "createdAt",
                        sortingOrder: "desc"
                    },
                });
                setRelated(
                    (data.content ?? [])
                        .filter((p) => p.productId !== product.productId)
                        .slice(0, 4)
                );
            } catch {
                console.warn("Failed to fetch related products");
            }
        };

        fetchRelated();
    }, [loading, product?.categoryName, product?.productId]);

    useEffect(() => {
        if (!product) return;
        setSubHeader(
            <div className="max-w-6xl mx-auto px-4 sm:px-6 py-2 flex items-center gap-2 overflow-hidden">
                <button onClick={() => navigate("/products")} className="text-gray-400 hover:text-gray-700 transition-colors cursor-pointer shrink-0">
                    <MdArrowBack size={16} />
                </button>
                <span className="text-xs text-gray-300">/</span>
                <button
                    onClick={() => navigate(`/products?category=${encodeURIComponent(product?.categoryName || "")}`)}
                    className="text-xs text-gray-400 hover:text-blue-500 transition-colors cursor-pointer shrink-0"
                >
                    {product?.categoryName}
                </button>
                <span className="text-xs text-gray-300">/</span>
                <span className="text-xs font-semibold text-gray-700 truncate">{product?.productName}</span>
            </div>
        );
        return () => setSubHeader(null);
    }, [product, navigate, setSubHeader]);

    useEffect(() => {
        const onScroll = () => setStickyVisible(window.scrollY > 500);
        window.addEventListener("scroll", onScroll);
        return () => window.removeEventListener("scroll", onScroll);
    }, []);

    if (loading) return <div className="bg-white min-h-screen"><Skeleton /></div>;
    if (!product) return null;

    const hasDiscount = product?.discountPercent > 0;
    const savings = product?.retailPrice - product?.sellingPrice;
    const cartQty = cartItem?.quantity ?? 0;

    // availableQty from backend ALREADY INCLUDES user's cart reservation
    // So: canAddMore = availableQty (DON'T subtract cartQuantity!)
    const canAddMore = product?.availableQty ?? 0;

    const inCart = cartQty > 0;
    const inStock = product?.availableQty > 0 || (inCart && !cartItem?.outOfStock);
    const lowStock = canAddMore > 0 && canAddMore <= LOW_STOCK_THRESHOLD;

    // Only show stock message for low stock or out of stock
    const stockColor = canAddMore <= 0 ? "text-red-500" : lowStock ? "text-amber-500" : "";
    const stockLabel = canAddMore <= 0
        ? "Out of Stock"
        : lowStock
            ? `Only ${canAddMore} left in stock`
            : "";
    const allImages = product?.allImages || [];

    return (
        <div className="bg-white min-h-screen pb-24">
            <div className="max-w-6xl mx-auto px-4 sm:px-6 py-10">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-start">

                    <div className="lg:sticky lg:top-28">
                        <ImageGallery
                            images={allImages}
                            productName={product?.productName}
                            inStock={inStock}
                        />
                    </div>

                    <div className="space-y-6">
                        <div className="flex items-center gap-2 flex-wrap">
                            <button
                                onClick={() => navigate(`/products?category=${encodeURIComponent(product?.categoryName || "")}`)}
                                className="inline-flex items-center gap-1 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 px-2.5 py-1 rounded-full transition-colors cursor-pointer"
                            >
                                <MdCategory size={11} /> {product?.categoryName}
                            </button>
                            {product?.sellerName && (
                                <span className="inline-flex items-center gap-1 text-xs text-gray-500 bg-gray-50 border border-gray-100 px-2.5 py-1 rounded-full">
                                    <MdStorefront size={11} /> {product?.sellerName}
                                </span>
                            )}
                        </div>

                        <div className="flex items-start justify-between gap-3">
                            <h1 className="text-3xl sm:text-4xl font-black text-gray-950 leading-tight tracking-tight">
                                {product?.productName}
                            </h1>
                            <WishlistToggle productId={productId} className="shrink-0 mt-1" />
                        </div>

                        <div className="bg-gray-50 rounded-2xl p-5 space-y-1.5">
                            <div className="flex items-baseline gap-3 flex-wrap">
                                <span className="text-4xl font-black text-gray-950">{formatCurrency(product?.sellingPrice)}</span>
                                {hasDiscount && (
                                    <span className="text-lg text-gray-400 line-through font-medium">{formatCurrency(product?.retailPrice)}</span>
                                )}
                            </div>
                            {hasDiscount && savings > 0 && (
                                <p className="text-sm font-semibold text-green-600">
                                    You save {formatCurrency(savings)} &nbsp;·&nbsp; {product?.discountPercent}% off
                                </p>
                            )}
                        </div>

                        {stockLabel && cartQty === 0 && (
                            <div className={`flex items-center gap-1.5 text-sm font-semibold ${stockColor}`}>
                                {stockLabel}
                            </div>
                        )}

                        <AddToCartControl
                            product={product}
                            className="w-full py-4 text-sm tracking-wide"
                        />

                        <div className="grid grid-cols-3 gap-3">
                            {[["🚚", "Free Delivery", "Above ₹" + (import.meta.env.VITE_FREE_SHIPPING_ABOVE || 999)], ["▤", "Order updates", "In your account"], ["🔒", "Secure Pay", "Encrypted"]].map(([icon, title, sub]) => (
                                <div key={title} className="bg-gray-50 rounded-xl p-3 text-center border border-gray-100">
                                    <div className="text-lg mb-1">{icon}</div>
                                    <div className="text-xs font-semibold text-gray-800">{title}</div>
                                    <div className="text-[10px] text-gray-400 mt-0.5">{sub}</div>
                                </div>
                            ))}
                        </div>

                        <div className="pt-4 border-t border-gray-100">
                            <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-3">About this product</h3>
                            <p className="text-sm text-gray-600 leading-relaxed whitespace-pre-line">{product?.description}</p>
                        </div>

                        <div className="pt-4 border-t border-gray-100 grid grid-cols-2 gap-y-3 gap-x-4 text-xs">
                            {[
                                ["Product ID", product?.productId],
                                ["Category", product?.categoryName],
                                ["Sold by", product?.sellerName ?? "—"],
                                ["Listed on", formatDate(product?.createdAt)],
                            ].map(([label, value]) => (
                                <div key={label}>
                                    <span className="text-gray-400 font-semibold block">{label}</span>
                                    <span className="text-gray-700 font-medium truncate block mt-0.5">{value}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {related.length > 0 && (
                    <div className="mt-16 pt-10 border-t border-gray-100">
                        <div className="flex items-center justify-between mb-6">
                            <div>
                                <h2 className="text-xl font-black text-gray-950">More from {product?.categoryName}</h2>
                                <p className="text-xs text-gray-400 mt-0.5">You might also like these</p>
                            </div>
                            <button
                                onClick={() => navigate(`/products?category=${encodeURIComponent(product?.categoryName || "")}`)}
                                className="inline-flex items-center gap-1 text-xs font-semibold text-blue-500 hover:text-blue-700 transition-colors cursor-pointer"
                            >
                                See all <MdArrowForward size={14} />
                            </button>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
                            {related.map((p) => (
                                <ProductCard
                                    key={p.productId}
                                    product={p}
                                    onClick={() => navigate(`/products/${p.productId}`)}
                                />
                            ))}
                        </div>
                    </div>
                )}
            </div>

            <div aria-hidden={!stickyVisible} inert={!stickyVisible} className={`fixed bottom-0 left-0 right-0 z-40 bg-white border-t border-gray-200 shadow-2xl transition-[transform,opacity] duration-200 ${stickyVisible ? "translate-y-0 opacity-100" : "translate-y-full opacity-0 pointer-events-none"}`}>
                <div className="max-w-6xl mx-auto px-4 sm:px-6 py-3 flex items-center justify-between gap-4">
                    <div className="min-w-0 flex items-center gap-3">
                        {product?.primaryImageUrl && (
                            <div className="w-12 h-12 rounded-lg border border-gray-100 shrink-0 hidden sm:flex items-center justify-center bg-gray-50">
                                <img src={product?.primaryImageUrl} alt="" className="max-w-full max-h-full object-contain" />
                            </div>
                        )}
                        <div className="min-w-0">
                            <p className="text-xs font-semibold text-gray-700 truncate">{product?.productName}</p>
                            <p className="text-lg font-black text-gray-950">{formatCurrency(product?.sellingPrice)}</p>
                            {inCart && (
                                <p className="text-[11px] text-blue-500 font-semibold">{cartQty} in cart</p>
                            )}
                        </div>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                        <AddToCartControl
                            product={product}
                            className="text-sm py-3 px-4"
                            showOnlyQty={true}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ProductDetailsPage;
