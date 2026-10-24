import { LoadingLayout } from "../shared/LoadingStates";
import ActionArrow from "../shared/ActionArrow";
import { useEffect, useState } from "react";
import {
    MdSearch, MdClose, MdShoppingCart,
    MdArrowBack, MdPerson,
} from "react-icons/md";
import toast from "../../utils/toastUtils.js";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import { useCartProducts } from "../../hooks/useCartProducts";

const TableSkeleton = ({ cols = 5 }) => <LoadingLayout variant="table" label="Loading products" cols={cols} />;

const DetailSkeleton = () => <LoadingLayout variant="list" label="Loading content" />;

const CartDetail = ({ cartId, onBack }) => {
    const [cart, setCart] = useState(null);
    const [loading, setLoading] = useState(true);
    const { items: cartItems, loading: loadingProducts } = useCartProducts(cart?.cartItems ?? []);

    useEffect(() => {
        const fetch = async () => {
            try {
                setLoading(true);
                const { data } = await api.get(`/admin/carts/${cartId}`);
                setCart(data);
            } catch {
                toast.error("Failed to load cart");
            } finally {
                setLoading(false);
            }
        };
        fetch();
    }, [cartId]);

    if (loading && !cart) return <DetailSkeleton />;
    if (!cart) return null;

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center gap-3">
                <button onClick={onBack} className="control flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer">
                    <MdArrowBack size={13} /> Back
                </button>
                <div>
                    <p className="text-sm font-bold text-gray-900">Cart #{cart.cartId}</p>
                    {cart.customer && (
                        <p className="text-xs text-gray-400">{cart.customer.name} · {cart.customer.email}</p>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
                {/* Items */}
                <div className="lg:col-span-2 surface overflow-hidden">
                    <div className="px-5 py-4 border-b border-gray-50">
                        <h3 className="text-xs font-bold text-gray-400 ">
                            Cart Items ({cart.cartItems?.length ?? 0})
                        </h3>
                    </div>
                    <div className="divide-y divide-gray-50">
                        {cart.cartItems?.length === 0 ? (
                            <p className="text-sm text-gray-400 text-center py-8">Empty cart</p>
                        ) : cartItems.map((item, i) => (
                            <div key={i} className="admin-cart-line px-5 py-4">
                                <div className="w-12 h-12 rounded-xl bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
                                    {item.product?.primaryImageUrl
                                        ? <img src={item.product.primaryImageUrl} alt="" className="w-full h-full object-cover" />
                                        : <div className="w-full h-full flex items-center justify-center text-gray-200 text-xl">◈</div>
                                    }
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="text-sm font-semibold text-gray-900 break-words">
                                        {item.product?.productName ?? (loadingProducts ? "Loading product…" : item.productId)}
                                    </p>
                                    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 mt-0.5 text-xs text-gray-500">
                                        <span>Qty: {item.quantity}</span>
                                        <span>·</span>
                                        <span>{formatCurrency(item.itemPrice ?? item.product?.sellingPrice)} each</span>
                                        {item.outOfStock && (
                                            <span className="text-red-500 font-semibold">Out of stock</span>
                                        )}
                                    </div>
                                </div>
                                <span className="text-sm font-semibold text-gray-900 shrink-0">
                                    {formatCurrency(item.lineTotal)}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Summary */}
                <div className="space-y-4 lg:sticky lg:top-4">
                    {/* Customer */}
                    {cart.customer && (
                        <div className="surface p-5">
                            <h3 className="text-xs font-bold text-gray-400  mb-3 flex items-center gap-1.5">
                                <MdPerson size={13} /> Customer
                            </h3>
                            <p className="text-sm font-bold text-gray-900">{cart.customer.name}</p>
                            <p className="text-xs text-gray-400 mt-0.5">{cart.customer.email}</p>
                        </div>
                    )}

                    {/* Totals */}
                    <div className="surface p-5">
                        <h3 className="text-xs font-bold text-gray-400  mb-3">Summary</h3>
                        <div className="space-y-1 pb-3 border-b border-gray-100">
                            <div className="flex justify-between text-xs">
                                <span className="text-gray-500">Subtotal</span>
                                <span className="font-semibold">{formatCurrency(cart.subtotal)}</span>
                            </div>
                            {cart.charges?.map((c, i) => (
                                <div key={i} className="flex justify-between text-xs">
                                    <span className="text-gray-500">{{ SHIPPING: "Delivery", PLATFORM_FEE: "Platform fee", PROCESSING_AND_HANDLING: "Processing & handling" }[c.type] ?? c.type?.replaceAll("_", " ").toLowerCase() ?? "Charge"}</span>
                                    <span className="font-semibold">{formatCurrency(c.amount)}</span>
                                </div>
                            ))}
                        </div>
                        <div className="flex justify-between items-center pt-3">
                            <span className="text-sm font-bold text-gray-900">Total</span>
                            <span className="text-2xl font-semibold text-gray-950">{formatCurrency(cart.total)}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

const CartsTab = () => {
    const [carts, setCarts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [selectedCartId, setSelectedCartId] = useState(null);

    useEffect(() => {
        const fetch = async () => {
            try {
                setLoading(true);
                const { data } = await api.get("/admin/carts");
                setCarts(data ?? []);
            } catch {
                toast.error("Failed to load carts");
            } finally {
                setLoading(false);
            }
        };
        fetch();
    }, []);

    if (selectedCartId) return (
        <CartDetail cartId={selectedCartId} onBack={() => setSelectedCartId(null)} />
    );

    const filtered = carts.filter(c =>
        !search ||
        c.cartId?.toLowerCase().includes(search.toLowerCase()) ||
        c.customer?.name?.toLowerCase().includes(search.toLowerCase()) ||
        c.customer?.email?.toLowerCase().includes(search.toLowerCase())
    );

    return (
        <div className="surface overflow-hidden">
            {/* Toolbar */}
            <div className="flex items-center gap-3 p-4 border-b border-gray-50 flex-wrap">
                <div className="dashboard-search flex-1 min-w-48">
                    <MdSearch size={14} className="text-gray-400 shrink-0" />
                    <input
                        type="text"
                        aria-label="Search by cart ID or customer..." value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Cart ID or customer"
                        className="text-xs text-gray-700 focus:outline-none w-full"
                    />
                    {search && <button aria-label="Clear search" onClick={() => setSearch("")} className="icon-action text-gray-300 hover:text-gray-500 cursor-pointer"><MdClose size={13} /></button>}
                </div>
                <span className="text-xs text-gray-400 font-semibold shrink-0">{filtered.length} cart{filtered.length !== 1 ? "s" : ""}</span>
            </div>

            {loading && !carts.length ? <TableSkeleton /> : filtered.length === 0 ? (
                <div className="py-16 text-center">
                    <MdShoppingCart size={32} className="text-gray-200 mx-auto mb-3" />
                    <p className="text-sm text-gray-400">No carts found</p>
                </div>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-gray-50">
                                {["Cart ID", "Customer", "Items", "Total", ""].map(h => (
                                    <th key={h} className="text-left text-xs font-bold text-gray-400  px-4 py-3">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.map(c => (
                                <tr key={c.cartId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors cursor-pointer" tabIndex={0} onKeyDown={e => { if (e.target === e.currentTarget && ["Enter", " "].includes(e.key)) { e.preventDefault(); setSelectedCartId(c.cartId); } }} onClick={() => setSelectedCartId(c.cartId)}>
                                    <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-700">{c.cartId}</td>
                                    <td className="px-4 py-3">
                                        {c.customer ? (
                                            <>
                                                <p className="text-sm font-semibold text-gray-800">{c.customer.name}</p>
                                                <p className="text-xs text-gray-400">{c.customer.email}</p>
                                            </>
                                        ) : (
                                            <span className="text-xs text-gray-400 italic">Guest</span>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-sm font-semibold text-gray-700">
                                        {c.cartItems?.length ?? 0} item{(c.cartItems?.length ?? 0) !== 1 ? "s" : ""}
                                    </td>
                                    <td className="px-4 py-3 text-sm font-semibold text-gray-900">{formatCurrency(c.total)}</td>
                                    <td className="px-4 py-3"><ActionArrow size={14} className="text-gray-300" /></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default CartsTab;
