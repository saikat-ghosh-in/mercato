import SelectControl from "../shared/SelectControl";
import ModalFrame from "../shared/ModalFrame";
import { useCallback, useEffect, useState } from "react";
import {
    MdSearch, MdClose, MdArrowBack, MdArrowForward,
    MdCheckCircle, MdCancel, MdHourglassEmpty,
    MdAccessTime, MdWarning, MdShoppingBag,
    MdLocationOn, MdPayment, MdContentCopy,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";

const STATUS_MAP = {
    CREATED: { label: "Awaiting Payment", color: "bg-yellow-100 text-yellow-700", icon: MdHourglassEmpty },
    CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    FULFILLMENT_PROCESSING: { label: "Processing", color: "bg-indigo-100 text-indigo-700", icon: MdAccessTime },
    FULFILLMENT_COMPLETE: { label: "Delivered", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    CANCELLED: { label: "Cancelled", color: "bg-red-100 text-red-600", icon: MdCancel },
};
const getStatus = (raw) =>
    STATUS_MAP[raw?.toUpperCase()] ?? { label: raw ?? "Unknown", color: "bg-gray-100 text-gray-600", icon: MdAccessTime };

const ALL_STATUSES = Object.keys(STATUS_MAP);

const TableSkeleton = () => (
    <div className="animate-pulse space-y-2 p-4">
        {[1, 2, 3, 4].map(i => (
            <div key={i} className="flex gap-4 py-3">
                {[1, 2, 3, 4, 5].map(j => <div key={j} className="h-3 bg-gray-100 rounded flex-1" />)}
            </div>
        ))}
    </div>
);

const DetailSkeleton = () => (
    <div className="animate-pulse space-y-4">
        {[1, 2, 3].map(i => (
            <div key={i} className="surface p-5 space-y-3">
                <div className="h-3 bg-gray-100 rounded w-1/3" />
                <div className="h-4 bg-gray-100 rounded w-full" />
                <div className="h-4 bg-gray-100 rounded w-2/3" />
            </div>
        ))}
    </div>
);

const SectionCard = ({ title, icon: Icon, children }) => (
    <div className="surface p-5">
        {title && (
            <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center gap-1.5">
                {Icon && <Icon size={13} />}{title}
            </h3>
        )}
        {children}
    </div>
);

const InfoRow = ({ label, value, mono, copyable, last }) => {
    const handleCopy = () => { navigator.clipboard.writeText(value); toast.success("Copied"); };
    return (
        <div className={`flex items-start justify-between gap-4 py-2.5 ${!last ? "border-b border-gray-50" : ""}`}>
            <span className="text-xs text-gray-400 shrink-0">{label}</span>
            <div className="flex items-center gap-1.5">
                <span className={`text-xs font-semibold text-gray-800 text-right break-all ${mono ? "font-mono" : ""}`}>{value ?? "—"}</span>
                {copyable && value && (
                    <button onClick={handleCopy} className="text-gray-300 hover:text-blue-500 cursor-pointer">
                        <MdContentCopy size={12} />
                    </button>
                )}
            </div>
        </div>
    );
};

const CancelModal = ({ order, onClose, onConfirm, loading }) => (
    <ModalFrame title="Cancel Order" onClose={onClose}>
        <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
            <div className="flex items-center gap-2 text-red-600 mb-3">
                <MdWarning size={20} />
                <h2 className="text-sm font-bold">Cancel Order</h2>
            </div>
            <p className="text-sm text-gray-600 mb-1">Cancel order <span className="font-semibold">#{order.orderId}</span>?</p>
            <p className="text-xs text-gray-400 mb-6">Refund will be initiated if payment was completed.</p>
            <div className="flex gap-3">
                <button aria-label="Close dialog" onClick={onClose} className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">Keep</button>
                <button onClick={onConfirm} disabled={loading} className="control flex-1 px-3 py-2.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98]">
                    {loading ? "Cancelling..." : "Yes, Cancel"}
                </button>
            </div>
        </div>
    </ModalFrame>
);

const OrderDetail = ({ orderId, onBack }) => {
    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);
    const [showCancel, setShowCancel] = useState(false);
    const [cancelling, setCancelling] = useState(false);

    const fetchOrder = useCallback(async () => {
        try {
            setLoading(true);
            const { data } = await api.get(`/admin/orders/${orderId}`);
            setOrder(data);
        } catch {
            toast.error("Failed to load order");
        } finally {
            setLoading(false);
        }
    }, [orderId]);

    useEffect(() => { fetchOrder(); }, [fetchOrder]);

    const handleCancel = async () => {
        setCancelling(true);
        try {
            const { data } = await api.post("/user/orders/cancel", { orderId, reason: "Admin cancellation" });
            setOrder(data);
            toast.success("Order cancelled");
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to cancel order");
        } finally {
            setCancelling(false);
            setShowCancel(false);
        }
    };

    if (loading && !order) return <DetailSkeleton />;
    if (!order) return null;

    const { label, color, icon: StatusIcon } = getStatus(order.orderStatus);
    const canCancel = order.orderStatus === "CREATED" || order.orderStatus === "CONFIRMED";

    const PAYMENT_COLOR = { SUCCESS: "text-blue-700", PENDING: "text-yellow-600", FAILED: "text-red-500", CANCELLED: "text-gray-400" };

    return (
        <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center gap-3">
                <button onClick={onBack} className="control flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer">
                    <MdArrowBack size={13} /> Back
                </button>
                <div>
                    <p className="text-sm font-bold text-gray-900">Order #{order.orderId}</p>
                    <p className="text-[11px] text-gray-400">{formatDate(order.createdAt ?? order.createDate)}</p>
                </div>
                {canCancel && (
                    <button
                        onClick={() => setShowCancel(true)}
                        className="control ml-auto flex items-center gap-1.5 text-xs font-bold text-red-500 bg-red-50 hover:bg-red-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer"
                    >
                        <MdCancel size={13} /> Cancel Order
                    </button>
                )}
            </div>

            {/* Status banner */}
            <div className={`flex items-center gap-3 px-5 py-4 rounded-2xl border ${order.orderStatus === "CANCELLED" ? "bg-red-50 border-red-100" :
                    order.orderStatus === "FULFILLMENT_COMPLETE" ? "bg-blue-50 border-blue-100" :
                        order.orderStatus === "CONFIRMED" ? "bg-blue-50 border-blue-100" :
                            "bg-yellow-50 border-yellow-100"
                }`}>
                <StatusIcon size={22} className={
                    order.orderStatus === "CANCELLED" ? "text-red-400" :
                        order.orderStatus === "FULFILLMENT_COMPLETE" ? "text-blue-500" :
                            order.orderStatus === "CONFIRMED" ? "text-blue-500" : "text-yellow-500"
                } />
                <div>
                    <p className="text-xs text-gray-500 font-medium">Order Status</p>
                    <p className="text-base font-black text-gray-900">{label}</p>
                </div>
                <span className={`ml-auto text-[11px] font-bold px-2.5 py-1 rounded-full ${color}`}>{order.orderStatus}</span>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
                <div className="lg:col-span-2 space-y-4">
                    {/* Order lines */}
                    <SectionCard title="Order Items" icon={MdShoppingBag}>
                        {order.orderLines?.map((line, i) => (
                            <div key={i} className="flex items-center gap-3 py-3 border-b border-gray-50 last:border-0">
                                <div className="w-12 h-12 rounded-xl bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
                                    {line.product?.primaryImageUrl
                                        ? <img src={line.product.primaryImageUrl} alt="" className="w-full h-full object-cover" />
                                        : <div className="w-full h-full flex items-center justify-center text-gray-200 text-xl">◈</div>
                                    }
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="text-sm font-semibold text-gray-900 line-clamp-1">{line.product?.productName}</p>
                                    <p className="text-[11px] text-gray-400">Qty: {line.quantity ?? line.orderedQty} · {formatCurrency(line.product?.unitPrice)} each</p>
                                </div>
                                <span className="text-sm font-black text-gray-900 shrink-0">{formatCurrency(line.lineTotal)}</span>
                            </div>
                        ))}
                    </SectionCard>

                    {/* Delivery */}
                    <SectionCard title="Delivery Address" icon={MdLocationOn}>
                        {order.deliveryAddress ? (
                            <div className="space-y-1 text-xs text-gray-600">
                                <p className="font-bold text-gray-900">{order.deliveryAddress.recipientName}</p>
                                <p>{order.deliveryAddress.addressLine1}</p>
                                {order.deliveryAddress.addressLine2 && <p>{order.deliveryAddress.addressLine2}</p>}
                                <p>{order.deliveryAddress.city}, {order.deliveryAddress.state} — {order.deliveryAddress.pincode}</p>
                                <p className="pt-1">📞 {order.deliveryAddress.recipientPhone}</p>
                            </div>
                        ) : <p className="text-sm text-gray-400">No address available</p>}
                    </SectionCard>

                    {/* Payment */}
                    <SectionCard title="Payment" icon={MdPayment}>
                        <InfoRow label="Method" value={order.paymentSummary?.paymentMethod} />
                        <InfoRow label="Status" value={order.paymentSummary?.status}
                            color={PAYMENT_COLOR[order.paymentSummary?.status?.toUpperCase()]} />
                        {order.paymentSummary?.gatewayReference && (
                            <InfoRow label="Transaction ID" value={order.paymentSummary.gatewayReference} mono copyable />
                        )}
                        {order.paymentSummary?.completedAt && (
                            <InfoRow label="Paid On" value={formatDate(order.paymentSummary.completedAt)} last />
                        )}
                    </SectionCard>
                </div>

                {/* Summary */}
                <div className="space-y-4 lg:sticky lg:top-4">
                    <SectionCard title="Customer">
                        <p className="text-sm font-bold text-gray-900">{order.customer?.name}</p>
                        <p className="text-xs text-gray-400 mt-0.5">{order.customer?.email}</p>
                    </SectionCard>
                    <SectionCard title="Order Summary">
                        <div className="space-y-1 pb-3 border-b border-gray-100">
                            <div className="flex justify-between text-xs"><span className="text-gray-500">Subtotal</span><span className="font-semibold">{formatCurrency(order.subTotal)}</span></div>
                            {order.charges > 0 && (
                                <div className="flex justify-between text-xs"><span className="text-gray-500">Fees</span><span className="font-semibold">{formatCurrency(order.charges)}</span></div>
                            )}
                        </div>
                        <div className="flex justify-between items-center pt-3">
                            <span className="text-sm font-bold text-gray-900">Total</span>
                            <span className="text-2xl font-black text-gray-950">{formatCurrency(order.totalAmount)}</span>
                        </div>
                    </SectionCard>
                </div>
            </div>

            {showCancel && (
                <CancelModal order={order} onClose={() => setShowCancel(false)} onConfirm={handleCancel} loading={cancelling} />
            )}
        </div>
    );
};


const OrdersTab = () => {
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [selectedOrderId, setSelectedOrderId] = useState(null);

    useEffect(() => {
        const fetch = async () => {
            try {
                setLoading(true);
                const { data } = await api.get("/admin/orders/summary");
                setOrders(data ?? []);
            } catch {
                toast.error("Failed to load orders");
            } finally {
                setLoading(false);
            }
        };
        fetch();
    }, []);

    if (selectedOrderId) return (
        <OrderDetail orderId={selectedOrderId} onBack={() => setSelectedOrderId(null)} />
    );

    const filtered = orders.filter(o => {
        const matchSearch = !search ||
            o.orderId?.toLowerCase().includes(search.toLowerCase()) ||
            o.customer?.name?.toLowerCase().includes(search.toLowerCase()) ||
            o.customer?.email?.toLowerCase().includes(search.toLowerCase());
        const matchStatus = !statusFilter || o.orderStatus === statusFilter;
        return matchSearch && matchStatus;
    });

    return (
        <div className="surface overflow-hidden">
            {/* Toolbar */}
            <div className="flex items-center gap-3 p-4 border-b border-gray-50 flex-wrap">
                <div className="dashboard-search flex-1 min-w-48">
                    <MdSearch size={14} className="text-gray-400 shrink-0" />
                    <input
                        type="text"
                        aria-label="Search by order ID or customer..." value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Order ID or customer"
                        className="text-xs text-gray-700 focus:outline-none w-full"
                    />
                    {search && <button aria-label="Clear search" onClick={() => setSearch("")} className="icon-action text-gray-300 hover:text-gray-500 cursor-pointer"><MdClose size={13} /></button>}
                </div>
                <SelectControl aria-label="Filter orders by status"
                    value={statusFilter}
                    onChange={e => setStatusFilter(e.target.value)}
                    className="text-xs border border-gray-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
                >
                    <option value="">All Statuses</option>
                    {ALL_STATUSES.map(s => (
                        <option key={s} value={s}>{STATUS_MAP[s].label}</option>
                    ))}
                </SelectControl>
                <span className="text-xs text-gray-400 font-semibold shrink-0">{filtered.length} order{filtered.length !== 1 ? "s" : ""}</span>
            </div>

            {loading && !orders.length ? <TableSkeleton /> : (
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-gray-50">
                                {["Order ID", "Customer", "Status", "Total", "Date", ""].map(h => (
                                    <th key={h} className="text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest px-4 py-3">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.length === 0 ? (
                                <tr><td colSpan={6} className="text-center py-10 text-sm text-gray-400">No orders found</td></tr>
                            ) : filtered.map(o => {
                                const { label, color, icon: StatusIcon } = getStatus(o.orderStatus);
                                return (
                                    <tr key={o.orderId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors cursor-pointer" tabIndex={0} onKeyDown={e => { if (e.target === e.currentTarget && ["Enter", " "].includes(e.key)) { e.preventDefault(); setSelectedOrderId(o.orderId); } }} onClick={() => setSelectedOrderId(o.orderId)}>
                                        <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-700">{o.orderId}</td>
                                        <td className="px-4 py-3">
                                            <p className="text-sm font-semibold text-gray-800">{o.customer?.name}</p>
                                            <p className="text-[10px] text-gray-400">{o.customer?.email}</p>
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1 w-fit ${color}`}>
                                                <StatusIcon size={9} />{label}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-sm font-black text-gray-900">{formatCurrency(o.totalAmount)}</td>
                                        <td className="px-4 py-3 text-xs text-gray-400">{formatDate(o.createdAt)}</td>
                                        <td className="px-4 py-3">
                                            <MdArrowForward size={14} className="text-gray-300" />
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default OrdersTab;
