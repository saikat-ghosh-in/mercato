import { LoadingLayout } from "../shared/LoadingStates";
import ActionArrow from "../shared/ActionArrow";
import SelectControl from "../shared/SelectControl";
import { Link } from "react-router-dom";
import { useState } from "react";
import {
    MdSearch, MdClose, MdArrowBack,
    MdCheckCircle, MdCancel, MdHourglassEmpty,
    MdAccessTime, MdShoppingBag,
    MdLocationOn, MdPayment, MdContentCopy,
} from "react-icons/md";
import toast from "../../utils/toastUtils.js";
import useWorkflowResource, { useWorkflowEpoch } from "../../hooks/useWorkflowResource";
import { actionMaximum, canCancelWhole, workflowLabel } from "../../utils/fulfillment";
import ShipmentPanel, { ShipmentLineAction } from "../orders/ShipmentPanel";
import { QuantityCommand, WorkflowProgress } from "../orders/WorkflowControls";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";

const STATUS_MAP = {
    CREATED: { label: "Awaiting Payment", color: "bg-yellow-100 text-yellow-700", icon: MdHourglassEmpty },
    CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    FULFILLMENT_PROCESSING: { label: "Processing", color: "bg-blue-100 text-blue-700", icon: MdAccessTime },
    FULFILLMENT_COMPLETE: { label: "Shipped (legacy)", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    CANCELLED: { label: "Cancelled", color: "bg-red-100 text-red-600", icon: MdCancel },
};
const getStatus = (raw) =>
    STATUS_MAP[raw?.toUpperCase()] ?? { label: raw ?? "Unknown", color: "bg-gray-100 text-gray-600", icon: MdAccessTime };

const ALL_STATUSES = Object.keys(STATUS_MAP);

const TableSkeleton = ({ cols = 5 }) => <LoadingLayout variant="table" label="Loading products" cols={cols} />;

const DetailSkeleton = () => <LoadingLayout variant="list" label="Loading content" />;

const SectionCard = ({ title, icon: Icon, children }) => (
    <div className="surface p-5">
        {title && (
            <h3 className="text-xs font-bold text-gray-400  mb-4 flex items-center gap-1.5">
                {Icon && <Icon size={13} />}{title}
            </h3>
        )}
        {children}
    </div>
);

const InfoRow = ({ label, value, mono, copyable, last }) => {
    const handleCopy = async () => { try { await navigator.clipboard.writeText(value); toast.success("Copied"); } catch { toast.error("Couldn’t copy. Select and copy the value instead."); } };
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

const OrderDetail = ({ orderId, onBack }) => {
    const [shipmentState, setShipmentState] = useState(null);
    const [shipLine,setShipLine]=useState(null);
    const {data:order, loading, error, refresh:fetchOrder, epoch} = useWorkflowResource(`/admin/orders/${encodeURIComponent(orderId)}`);

    if (loading && !order) return <DetailSkeleton />;
    if (!order) return <div className="state-panel" role="alert">
        <h2>Order details could not load.</h2>
        <p>{error || "Check your connection and try again."}</p>
        <button type="button" className="button button-dark" onClick={fetchOrder}>Try again</button>
        <button type="button" className="text-link" onClick={onBack}>Back to orders</button>
    </div>;

    const { icon: StatusIcon } = getStatus(order.orderStatus);

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
                    <p className="text-xs text-gray-400">{formatDate(order.createdAt ?? order.createDate)}</p>
                </div>
                <QuantityCommand key={`${epoch}:${orderId}:cancel`} label="Cancel entire order" whole cancellation maximum={canCancelWhole(order) ? 1 : 0} version={order.workflow?.version} endpoint="/user/orders/cancel" makeBody={(_qty,reason) => ({orderId,reason})} onComplete={fetchOrder} disabled={loading} />
            </div>

            {error && <p role="alert" className="text-sm text-red-700">{error} <button className="text-link" onClick={fetchOrder}>Retry refresh</button></p>}
            <WorkflowProgress record={order} />
            <ShipmentPanel scope="admin" recordId={orderId} version={order.workflow?.version} onChanged={fetchOrder} onData={setShipmentState} lineRequest={shipLine} />
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
                    <p className="text-base font-semibold text-gray-900">{workflowLabel(order)}</p>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
                <div className="lg:col-span-2 space-y-4">
                    {/* Order lines */}
                    <SectionCard title="Order Items" icon={MdShoppingBag}>
                        {order.orderLines?.map((line, i) => (
                            <div key={i} className="flex flex-wrap items-center gap-3 py-3 border-b border-gray-50 last:border-0">
                                <Link to={`/products/${line.product?.productId}`} aria-label={`View ${line.product?.productName || "product"}`} className="w-12 h-12 rounded-xl bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
                                    {line.product?.primaryImageUrl
                                        ? <img src={line.product.primaryImageUrl} alt="" className="w-full h-full object-cover" />
                                        : <div className="w-full h-full flex items-center justify-center text-gray-200 text-xl">◈</div>
                                    }
                                </Link>
                                <div className="flex-1 min-w-0">
                                    <Link to={`/products/${line.product?.productId}`} className="text-sm font-semibold text-gray-900 line-clamp-1">{line.product?.productName}</Link>
                                    <p className="text-xs text-gray-400">Qty {line.orderedQty ?? line.quantity} · {formatCurrency(line.product?.unitPrice)} each</p>
                                </div>
                                <span className="text-sm font-semibold text-gray-900 shrink-0">{formatCurrency(line.lineTotal)}</span>
                                <div className="w-full space-y-3"><WorkflowProgress record={line} showQuantity={false} /><div className="flex flex-wrap gap-2">
                                    {[["ACCEPT","Accept"],["START_PREPARING","Start preparing"],["MARK_PACKED","Mark as packed"],["DELIVER","Mark delivered"],["CANCEL","Cancel units"]].filter(([action]) => action!=="DELIVER" || (shipmentState?.legacyDeliveryItems||[]).some(item => item.fulfillmentId===line.fulfillmentId && item.lineNumber===line.orderLineNumber && item.maximum>0)).map(([action,label]) => <QuantityCommand key={`${epoch}:${line.fulfillmentId}:${line.orderLineNumber}:${action}`} label={label} cancellation={action==='CANCEL'} maximum={action==="DELIVER" ? (shipmentState?.legacyDeliveryItems||[]).find(item => item.fulfillmentId===line.fulfillmentId && item.lineNumber===line.orderLineNumber)?.maximum || 0 : actionMaximum(line,action)} version={order.workflow?.version} endpoint="/fulfillment/commands" makeBody={(qty,reason) => ({fulfillmentId:line.fulfillmentId,orderLineNumber:line.orderLineNumber,action,qty,...(action==='CANCEL'?{reason}:{})})} onComplete={fetchOrder} disabled={loading} />)}
                                    <ShipmentLineAction data={shipmentState} fulfillmentId={line.fulfillmentId} lineNumber={line.orderLineNumber} onOpen={setShipLine} disabled={loading} />
                                </div></div>
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
                                <p className="pt-1">Phone: {order.deliveryAddress.recipientPhone}</p>
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
                            <span className="text-2xl font-semibold text-gray-950">{formatCurrency(order.totalAmount)}</span>
                        </div>
                    </SectionCard>
                </div>
            </div>

        </div>
    );
};


const OrdersTab = () => {
    const epoch = useWorkflowEpoch();
    const {data, loading, error, refresh} = useWorkflowResource('/admin/orders/summary');
    const orders = data || [];
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [selection, setSelection] = useState(null);
    const selectedOrderId = selection?.epoch === epoch ? selection.id : null;
    const setSelectedOrderId = id => setSelection(id ? {id,epoch} : null);
    if (selectedOrderId) return <OrderDetail key={`${epoch}:${selectedOrderId}`} orderId={selectedOrderId} onBack={() => { setSelectedOrderId(null); refresh(); }} />;

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
            {error && <p role="alert" className="p-4 text-sm text-red-700">{error} <button className="text-link" onClick={refresh}>Retry refresh</button></p>}
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
                                    <th key={h} className="text-left text-xs font-bold text-gray-400  px-4 py-3">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.length === 0 ? (
                                <tr><td colSpan={6} className="text-center py-10 text-sm text-gray-400">No orders found</td></tr>
                            ) : filtered.map(o => {
                                const { color, icon: StatusIcon } = getStatus(o.orderStatus);
                                return (
                                    <tr key={o.orderId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors cursor-pointer" tabIndex={0} onKeyDown={e => { if (e.target === e.currentTarget && ["Enter", " "].includes(e.key)) { e.preventDefault(); setSelectedOrderId(o.orderId); } }} onClick={() => setSelectedOrderId(o.orderId)}>
                                        <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-700">{o.orderId}</td>
                                        <td className="px-4 py-3">
                                            <p className="text-sm font-semibold text-gray-800">{o.customer?.name}</p>
                                            <p className="text-xs text-gray-400">{o.customer?.email}</p>
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={`text-xs font-bold px-2 py-0.5 rounded-full flex items-center gap-1 w-fit ${color}`}>
                                                <StatusIcon size={9} />{workflowLabel(o)}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-sm font-semibold text-gray-900">{formatCurrency(o.totalAmount)}</td>
                                        <td className="px-4 py-3 text-xs text-gray-400">{formatDate(o.createdAt)}</td>
                                        <td className="px-4 py-3">
                                            <ActionArrow size={14} className="text-gray-300" />
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
