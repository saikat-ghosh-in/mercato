import ModalFrame from "../shared/ModalFrame";
import { useCallback, useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import {
  MdArrowBack, MdLocationOn, MdPayment, MdShoppingBag,
  MdCancel, MdWarning, MdContentCopy, MdShoppingCart,
  MdClose, MdSync, MdTimer, MdAutorenew
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";
import QuantityStepper from "../shared/QuantityStepper";
import {
  getOrderStatus,
  CANCEL_REASONS, getCancelWindowRemaining, formatTimeRemaining,
  getPaymentStatusStyle, getRefundStatusStyle,
  getPaymentMessage, getRefundMessage,
  paymentFooterBg,
  refundFooterBg,
  QUANTITY_CONFIG,
} from "../../utils/orderUtils";
import { syncPayment, retryPayment } from "../../utils/paymentUtils";

const CancelReasonModal = ({ title, subtitle, onClose, onConfirm, loading, children }) => {
  const [selected, setSelected] = useState("");
  const [custom, setCustom] = useState("");
  const reason = selected === "Other" ? custom.trim() : selected;
  const canSubmit = selected !== "" && (selected !== "Other" || custom.trim() !== "");

  return (
    <ModalFrame title={title} onClose={onClose}>
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
        <div className="flex items-center justify-between mb-1">
          <div className="flex items-center gap-2 text-red-600">
            <MdWarning size={20} />
            <h2 className="text-sm font-bold">{title}</h2>
          </div>
          <button aria-label="Close dialog" onClick={onClose} className="text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
            <MdClose size={18} />
          </button>
        </div>
        {subtitle && <p className="text-xs text-gray-400 mb-2 leading-relaxed">{subtitle}</p>}
        {children}
        <p className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-2 mt-3">
          Reason for cancellation
        </p>
        <div className="space-y-1.5 mb-3">
          {CANCEL_REASONS.map(r => (
            <button
              key={r}
              onClick={() => { setSelected(r); setCustom(""); }}
              className={`w-full text-left text-xs px-3 py-2 rounded-xl border transition-colors cursor-pointer ${selected === r
                ? "bg-red-50 border-red-200 text-red-700 font-semibold"
                : "border-gray-100 text-gray-600 hover:bg-gray-50"
                }`}
            >
              {r}
            </button>
          ))}
        </div>
        {selected === "Other" && (
          <textarea
            value={custom}
            onChange={e => setCustom(e.target.value)}
            placeholder="Please describe your reason..."
            rows={2}
            className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2 mb-3 focus:outline-none focus:ring-1 focus:ring-red-300 resize-none text-gray-700 placeholder-gray-300"
          />
        )}
        <div className="flex gap-3 mt-1">
          <button onClick={onClose} className="flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">
            Keep
          </button>
          <button
            onClick={() => onConfirm(reason)}
            disabled={loading || !canSubmit}
            className="flex-1 px-3 py-2.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded-xl transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer font-bold active:scale-[0.98]"
          >
            {loading ? "Cancelling..." : "Yes, Cancel"}
          </button>
        </div>
      </div>
    </ModalFrame>
  );
};

const useCancelCountdown = (completedAt) => {
  const [now, setNow] = useState(Date.now);
  const intervalRef = useRef(null);

  useEffect(() => {
    if (!completedAt) return;

    intervalRef.current = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => clearInterval(intervalRef.current);
  }, [completedAt]);

  return getCancelWindowRemaining(completedAt, now);
};

const SyncPaymentButton = ({ orderId, onSync }) => {
  const [loading, setLoading] = useState(false);
  return (
    <button
      onClick={() => syncPayment({ orderId, onSuccess: onSync, setLoading })}
      disabled={loading}
      className="flex items-center gap-1.5 text-xs font-bold text-yellow-700 bg-yellow-100
        hover:bg-yellow-200 px-3 py-2 rounded-xl transition-colors cursor-pointer
        disabled:opacity-50 active:scale-[0.98]"
    >
      <MdSync size={14} className={loading ? "animate-spin" : ""} />
      {loading ? "Checking..." : "Check Status"}
    </button>
  );
};

const RetryPaymentButton = ({ order }) => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  return (
    <button
      onClick={() => retryPayment({ order, dispatch, navigate, setLoading })}
      disabled={loading}
      className="flex items-center gap-1.5 text-xs font-bold text-white bg-blue-500
        hover:bg-blue-600 px-3 py-2 rounded-xl transition-colors cursor-pointer
        disabled:opacity-50 active:scale-[0.98]"
    >
      <MdPayment size={14} />
      {loading ? "Redirecting..." : "Retry Payment"}
    </button>
  );
};

const StatusBanner = ({ order }) => {
  const { label, sublabel, bannerBg, iconColor, icon: StatusIcon } = getOrderStatus(order.orderStatus);

  return (
    <div className={`rounded-2xl border mb-6 ${bannerBg}`}>
      <div className="flex items-center gap-3 px-5 py-4">
        <StatusIcon size={22} className={iconColor} />
        <div className="flex-1 min-w-0">
          <p className="text-base font-black text-gray-900">{label}</p>
          <p className="text-xs text-gray-500 mt-0.5">{sublabel}</p>
        </div>
      </div>
    </div>
  );
};

const Skeleton = () => (
  <div className="animate-pulse space-y-4">
    <div className="surface p-5 space-y-3">
      <div className="h-4 bg-gray-100 rounded w-1/4" />
      <div className="h-8 bg-gray-100 rounded w-1/2" />
      <div className="h-3 bg-gray-100 rounded w-1/3" />
    </div>
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <div className="lg:col-span-2 space-y-4">
        {[1, 2, 3].map(i => (
          <div key={i} className="surface p-5 space-y-3">
            <div className="h-3 bg-gray-100 rounded w-1/3" />
            <div className="h-4 bg-gray-100 rounded w-full" />
            <div className="h-4 bg-gray-100 rounded w-3/4" />
          </div>
        ))}
      </div>
      <div className="surface p-5 space-y-3">
        <div className="h-3 bg-gray-100 rounded w-1/2" />
        <div className="h-6 bg-gray-100 rounded w-full" />
        <div className="h-6 bg-gray-100 rounded w-full" />
        <div className="h-10 bg-gray-100 rounded w-full mt-4" />
      </div>
    </div>
  </div>
);

const SectionCard = ({ title, icon: Icon, children }) => (
  <div className="surface p-5">
    {title && (
      <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4 flex items-center gap-1.5">
        {Icon && <Icon size={13} />} {title}
      </h3>
    )}
    {children}
  </div>
);

const InfoRow = ({ label, value, mono, copyable, color, last }) => {
  const handleCopy = () => { navigator.clipboard.writeText(value); toast.success("Copied to clipboard"); };
  return (
    <div className={`flex items-start justify-between gap-4 py-2.5 ${!last ? "border-b border-gray-100" : ""}`}>
      <span className="text-xs text-gray-400 shrink-0 pt-0.5">{label}</span>
      <div className="flex items-center gap-1.5 min-w-0">
        <span className={`text-xs font-semibold text-right break-all ${color ?? "text-gray-800"} ${mono ? "font-mono" : ""}`}>
          {value ?? "—"}
        </span>
        {copyable && value && (
          <button onClick={handleCopy} className="text-gray-300 hover:text-blue-500 transition-colors shrink-0 cursor-pointer">
            <MdContentCopy size={12} />
          </button>
        )}
      </div>
    </div>
  );
};

const QuantityBreakdown = ({ line }) => {
  const visible = QUANTITY_CONFIG.filter(({ key }) => (line[key] ?? 0) > 0);
  if (visible.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-1 mt-1.5">
      {visible.map(({ key, label, className }) => (
        <span
          key={label}
          className={`text-[10px] font-semibold px-1.5 py-0.5 rounded-md ${className}`}
        >
          {line[key]} {label}
        </span>
      ))}
    </div>
  );
};

const OrderLineItem = ({ line, orderStatus, onLineUpdate, cancelWindowRemaining }) => {
  const navigate = useNavigate();
  const [cancelling, setCancelling] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [cancelQty, setCancelQty] = useState(line.pendingQty);
  const [fullProduct, setFullProduct] = useState(null);
  const [imageLoading, setImageLoading] = useState(false);

  // Fetch full product details if primaryImageUrl is missing
  useEffect(() => {
    if (!line.product?.primaryImageUrl && line.product?.productId) {
      setImageLoading(true);
      api.get(`/public/products/${line.product.productId}`)
        .then(({ data }) => setFullProduct(data))
        .catch(err => console.error(`Failed to fetch product ${line.product.productId}:`, err))
        .finally(() => setImageLoading(false));
    }
  }, [line.product?.productId, line.product?.primaryImageUrl]);

  const lineStatus = line.orderLineStatus?.toUpperCase();

  const canCancelLine =
    (lineStatus === "CREATED" || lineStatus === "CONFIRMED") &&
    (orderStatus === "CREATED" ||
      (orderStatus === "CONFIRMED" && cancelWindowRemaining > 0));

  const handleCancelLine = async (reason) => {
    setCancelling(true);
    try {
      await api.post("/order-line/update", {
        fulfillmentId: line.fulfillmentId,
        orderLineNumber: line.orderLineNumber,
        action: "CANCEL",
        qty: cancelQty,
        reason,
      });
      toast.success("Item cancelled successfully");
      onLineUpdate(true);
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to cancel item");
    } finally {
      setCancelling(false);
      setShowModal(false);
    }
  };

  const openModal = () => { setCancelQty(line.pendingQty); setShowModal(true); };

  const sellerName = line.seller?.name ?? "Unknown Seller";

  return (
    <>
      <div className="flex items-center gap-3 py-3 last:pb-0 border-b border-gray-100 last:border-0 hover:bg-gray-50/50 transition-colors rounded-lg px-1 cursor-pointer" onClick={() => navigate(`/products/${line.product.productId}`)}>
        <div className="w-14 h-14 rounded-xl bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
          {imageLoading ? (
            <div className="w-full h-full bg-linear-to-r from-gray-100 to-gray-50 animate-pulse" />
          ) : (fullProduct?.primaryImageUrl || line.product?.primaryImageUrl) ? (
            <img src={fullProduct?.primaryImageUrl || line.product?.primaryImageUrl} alt={line.product.productName} className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-gray-200 text-2xl select-none">◈</div>
          )}
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-gray-900 line-clamp-1 hover:text-blue-600 transition-colors">{line.product?.productName ?? "Product"}</p>
          {/* ✦ seller + unit rate on the same line */}
          <p className="text-[10px] text-gray-400 mt-0.5">
            by {sellerName}&nbsp;·&nbsp;{formatCurrency(line.product?.unitPrice)} / unit
          </p>
          <QuantityBreakdown line={line} />
        </div>

        <div className="flex flex-col items-end gap-1 shrink-0">
          <span className="text-sm font-black text-gray-900">{formatCurrency(line.lineTotal)}</span>
          {canCancelLine && (
            <button
              onClick={(e) => { e.stopPropagation(); openModal(); }}
              disabled={cancelling}
              className="text-[11px] font-semibold text-red-400 hover:text-red-600 transition-colors cursor-pointer disabled:opacity-40"
            >
              Cancel
            </button>
          )}
        </div>
      </div>

      {showModal && (
        <CancelReasonModal
          title="Cancel Item"
          subtitle={`Cancel "${line.product?.productName}"?`}
          onClose={() => setShowModal(false)}
          onConfirm={handleCancelLine}
          loading={cancelling}
        >
          {line.pendingQty > 1 && (
            <div className="mb-3">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-2">Quantity to cancel</p>
              <div className="flex items-center gap-3">
                <QuantityStepper
                  quantity={cancelQty}
                  onDecrease={() => setCancelQty(q => Math.max(1, q - 1))}
                  onIncrease={() => setCancelQty(q => Math.min(line.pendingQty, q + 1))}
                  minQty={1}
                  maxQty={line.pendingQty}
                />
                <span className="text-xs text-gray-400">of {line.pendingQty} pending</span>
              </div>
              {cancelQty < line.pendingQty && (
                <p className="text-[11px] text-blue-500 mt-1.5">
                  Partial cancellation — {line.pendingQty - cancelQty} unit{line.pendingQty - cancelQty !== 1 ? "s" : ""} will remain active
                </p>
              )}
            </div>
          )}
        </CancelReasonModal>
      )}
    </>
  );
};

const OrderDetails = ({ orderId }) => {
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  const fetchOrder = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      setError("");
      const { data } = await api.get(`/user/orders/${orderId}`);
      setOrder(data);
    } catch (err) {
      setError(err?.response?.data?.message || "Failed to fetch order details");
    } finally {
      if (!silent) setLoading(false);
    }
  }, [orderId]);

  useEffect(() => { if (orderId) fetchOrder(); }, [fetchOrder, orderId]);

  const cancelWindowRemaining = useCancelCountdown(
    order?.orderStatus === "CONFIRMED" ? order?.paymentSummary?.completedAt : null
  );

  const handleCancelOrder = async (reason) => {
    setCancelling(true);
    try {
      const { data } = await api.post("/user/orders/cancel", { orderId: order.orderId, reason });
      setOrder(data);
      toast.success("Order cancelled successfully");
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to cancel order");
    } finally {
      setCancelling(false);
      setShowCancelModal(false);
    }
  };

  const canCancelOrder = order?.orderStatus === "CREATED" ||
    (order?.orderStatus === "CONFIRMED" && cancelWindowRemaining > 0);

  if (loading && !order) return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8"><Skeleton /></div>
    </div>
  );

  if (error || !order) return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="surface p-10 text-center">
          <div className="w-16 h-16 rounded-2xl bg-red-50 flex items-center justify-center mx-auto mb-4">
            <MdWarning size={28} className="text-red-300" />
          </div>
          <h2 className="text-base font-bold text-gray-800 mb-1">Order not found</h2>
          <p className="text-sm text-gray-400 mb-6">{error || "Unable to load order details"}</p>
          <button
            onClick={() => navigate("/orders")}
            className="inline-flex items-center gap-2 bg-gray-950 hover:bg-gray-800 text-white text-sm font-bold px-5 py-2.5 rounded-xl transition-[transform,opacity,background-color] active:scale-[0.98]"
          >
            <MdArrowBack size={14} /> Back to Orders
          </button>
        </div>
      </div>
    </div>
  );

  const isRefundPending =
    order.orderStatus !== "CANCELLED" &&
    order.orderStatus !== "FULFILLMENT_COMPLETE" &&
    order.orderLines?.some(l => l.cancelledQty > 0);

  const refundSummary = order.refundSummary;

  const paymentStyle = getPaymentStatusStyle(order.paymentSummary?.status);
  const refundStyle = getRefundStatusStyle(order.refundSummary?.status);
  const refundBg = refundSummary
    ? refundFooterBg[refundSummary.status?.toUpperCase()] ?? "bg-gray-50"
    : null;

  return (
    <div className="bg-gray-50 min-h-screen pb-28 sm:pb-20 lg:pb-6 py-6">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-blue-50 rounded-xl">
            <MdShoppingBag size={20} className="text-blue-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">Order #{order.orderId}</h1>
            <p className="text-xs text-gray-500 mt-0.5">Placed on {formatDate(order.createdAt)}</p>
          </div>
          <button
            onClick={() => navigate("/orders")}
            className="ml-auto flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors"
          >
            <MdArrowBack size={13} /> All Orders
          </button>
        </div>

        {/* Status banner */}
        <StatusBanner order={order} onRetrySuccess={() => fetchOrder(true)} />

        {/* Floating info bar */}
        {(order.orderStatus === "CONFIRMED" || order.orderStatus === "CREATED" || order.orderStatus === "CANCELLED") && (
          <div className="bg-white border border-gray-100 rounded-xl mb-4 shadow-sm overflow-hidden">

            {/* Payment status row — CREATED only */}
            {order.orderStatus === "CREATED" && order.paymentSummary && (() => {
              const paymentStatus = order.paymentSummary.status?.toUpperCase();
              const paymentStyle = getPaymentStatusStyle(paymentStatus);
              const paymentBg = paymentFooterBg[paymentStatus] ?? "bg-gray-50";

              return (
                <div className={`flex items-center justify-between gap-3 px-4 py-2.5 flex-wrap border-b border-gray-100 ${paymentBg}`}>
                  <div className="flex items-center gap-2">
                    <MdPayment size={14} className={paymentStyle.text} />
                    <div>
                      <p className={`text-xs font-semibold ${paymentStyle.text}`}>
                        {getPaymentMessage(order.paymentSummary.status, formatCurrency(order.paymentSummary.amount))}
                      </p>
                      {paymentStatus === "PENDING" && (
                        <p className="text-[11px] text-gray-400 mt-0.5">
                          Already paid? Use "Check Status" to sync your payment.
                        </p>
                      )}
                      {(paymentStatus === "FAILED" || paymentStatus === "USER_DROPPED") && (
                        <p className="text-[11px] text-gray-400 mt-0.5">
                          You can retry the payment to complete your order.
                        </p>
                      )}
                      {!["PENDING", "FAILED", "USER_DROPPED"].includes(paymentStatus) && (
                        <p className="text-[11px] text-gray-400 mt-0.5">
                          Waiting for payment confirmation.
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <SyncPaymentButton orderId={order.orderId} onSync={() => fetchOrder(true)} />
                    <RetryPaymentButton order={order} />
                  </div>
                </div>
              );
            })()}

            {/* Cancel timer row — CONFIRMED only */}
            {order.orderStatus === "CONFIRMED" && (
              <div className="flex items-center gap-2 px-4 py-2.5 border-t border-gray-100">
                <MdTimer size={14} className={cancelWindowRemaining > 0 ? "text-amber-500 shrink-0" : "text-gray-400 shrink-0"} />
                {cancelWindowRemaining > 0 ? (
                  <p className="text-xs text-amber-700 font-semibold">
                    Cancel window closes in <span className="font-black">{formatTimeRemaining(cancelWindowRemaining)}</span>
                  </p>
                ) : (
                  <p className="text-xs text-gray-500 font-semibold">Cancellation window has expired</p>
                )}
              </div>
            )}

            {/* Refund pending row */}
            {isRefundPending && (
              <div className="flex items-start gap-2 px-4 py-2.5 bg-amber-50 border-t border-amber-100">
                <MdAutorenew size={14} className="text-amber-500 shrink-0 mt-0.5" />
                <p className="text-xs text-amber-700 leading-relaxed">
                  Refund for cancelled units will be processed once the order is fully closed.
                </p>
              </div>
            )}

            {/* Refund summary row */}
            {refundSummary && (
              <div className={`flex items-center gap-2 px-4 py-2.5 border-t border-gray-100 ${refundBg}`}>
                <MdAutorenew size={14} className={`shrink-0 ${refundStyle.text}`} />
                <p className={`text-xs font-semibold ${refundStyle.text}`}>
                  {getRefundMessage(
                    refundSummary.status,
                    formatCurrency(refundSummary.amount)
                  )}
                </p>
              </div>
            )}

          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">

          {/* Left column */}
          <div className="lg:col-span-2 space-y-4">
            <SectionCard title="Order Items" icon={MdShoppingBag}>
              {order.orderLines?.length > 0 ? (
                order.orderLines.map((line, i) => (
                  <OrderLineItem
                    key={i}
                    line={line}
                    orderStatus={order.orderStatus}
                    onLineUpdate={(silent) => fetchOrder(silent)}
                    cancelWindowRemaining={cancelWindowRemaining}
                  />
                ))
              ) : (
                <p className="text-sm text-gray-400 text-center py-4">No items found</p>
              )}
            </SectionCard>

            <SectionCard title="Delivery Address" icon={MdLocationOn}>
              {order.deliveryAddress ? (
                <div className="space-y-1">
                  <p className="text-sm font-bold text-gray-900">{order.deliveryAddress.recipientName}</p>
                  <p className="text-xs text-gray-600">{order.deliveryAddress.addressLine1}</p>
                  {order.deliveryAddress.addressLine2 && (
                    <p className="text-xs text-gray-600">{order.deliveryAddress.addressLine2}</p>
                  )}
                  <p className="text-xs text-gray-600">
                    {order.deliveryAddress.city}, {order.deliveryAddress.state} — {order.deliveryAddress.pincode}
                  </p>
                  <p className="text-xs text-gray-500 pt-1">Ph. {order.deliveryAddress.recipientPhone}</p>
                </div>
              ) : (
                <p className="text-sm text-gray-400">No address information available</p>
              )}
            </SectionCard>

            <SectionCard title="Payment Information" icon={MdPayment}>
              <InfoRow label="Method" value={order.paymentSummary?.paymentMethod} />
              <InfoRow label="Status" value={order.paymentSummary?.status} color={paymentStyle.text} />
              {order.paymentSummary?.gatewayName && (
                <InfoRow label="Gateway" value={order.paymentSummary.gatewayName} />
              )}
              {order.paymentSummary?.gatewayReference && (
                <InfoRow label="Transaction ID" value={order.paymentSummary.gatewayReference} mono copyable />
              )}
              {order.paymentSummary?.completedAt && (
                <InfoRow label="Paid On" value={formatDate(order.paymentSummary.completedAt)} />
              )}
              {order.paymentSummary?.gatewayResponseMessage && (
                <InfoRow label="Message" value={order.paymentSummary.gatewayResponseMessage} last />
              )}
            </SectionCard>

            {order.refundSummary && (
              <SectionCard title="Refund Information" icon={MdAutorenew}>
                <InfoRow label="Status" value={order.refundSummary.status} color={refundStyle.text} />
                <InfoRow label="Amount" value={formatCurrency(order.refundSummary.amount)} color="text-blue-700" />
                <InfoRow label="Reason" value={order.refundSummary.reason} />
                {order.refundSummary.gatewayReference && (
                  <InfoRow label="Refund ID" value={order.refundSummary.gatewayReference} mono copyable />
                )}
                {order.refundSummary.createdAt && (
                  <InfoRow label="Initiated" value={formatDate(order.refundSummary.createdAt)} />
                )}
                {order.refundSummary.updatedAt && (
                  <InfoRow label="Last Updated" value={formatDate(order.refundSummary.updatedAt)} />
                )}
                {order.refundSummary.gatewayResponseMessage && (
                  <InfoRow label="Message" value={order.refundSummary.gatewayResponseMessage} />
                )}
                {order.refundSummary.failureReason && (
                  <InfoRow label="Failure Reason" value={order.refundSummary.failureReason} color="text-red-500" last />
                )}
              </SectionCard>
            )}
          </div>

          {/* Right column - Hidden on mobile, sticky on desktop */}
          <div className="hidden lg:block space-y-4 lg:sticky lg:top-4">
            <SectionCard title="Order Summary">
              <div className="space-y-1 pb-3 border-b border-gray-100">
                <div className="flex justify-between items-center text-xs">
                  <span className="text-gray-500">Subtotal</span>
                  <span className="font-semibold text-gray-800">{formatCurrency(order.subTotal)}</span>
                </div>
                <div className="flex justify-between items-center text-xs">
                  <span className="text-gray-500">Fees</span>
                  <span className="font-semibold text-gray-800">{formatCurrency(order.charges)}</span>
                </div>
              </div>
              <div className="flex justify-between items-center pt-3">
                <span className="text-sm font-bold text-gray-900">Total</span>
                <span className="text-2xl font-black text-gray-950">{formatCurrency(order.totalAmount)}</span>
              </div>
            </SectionCard>

            <SectionCard>
              <div className="space-y-2.5">
                {canCancelOrder && (
                  <button
                    onClick={() => setShowCancelModal(true)}
                    className="w-full flex items-center justify-center gap-2 py-2.5 text-sm font-bold text-red-500 bg-red-50 hover:bg-red-100 rounded-xl transition-colors cursor-pointer active:scale-[0.98]"
                  >
                    <MdCancel size={15} /> Cancel Order
                  </button>
                )}
                <button
                  onClick={() => navigate("/products")}
                  className="w-full flex items-center justify-center gap-2 py-2.5 text-sm font-bold text-gray-700 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors cursor-pointer"
                >
                  <MdShoppingCart size={15} /> Continue Shopping
                </button>
              </div>
            </SectionCard>
          </div>
        </div>
      </div>

      {/* Mobile floating action bar */}
      <div className="fixed bottom-0 left-0 right-0 lg:hidden bg-white border-t border-gray-100 shadow-lg">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 py-3 flex gap-2 safe-area-pb">
          {canCancelOrder && (
            <button
              onClick={() => setShowCancelModal(true)}
              className="flex-1 flex items-center justify-center gap-2 py-2.5 text-sm font-bold text-red-500 bg-red-50 hover:bg-red-100 rounded-xl transition-colors cursor-pointer active:scale-[0.98]"
            >
              <MdCancel size={15} /> Cancel
            </button>
          )}
          <button
            onClick={() => navigate("/products")}
            className={`flex-1 flex items-center justify-center gap-2 py-2.5 text-sm font-bold text-gray-700 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors cursor-pointer ${!canCancelOrder ? "col-span-2" : ""}`}
          >
            <MdShoppingCart size={15} /> Shop
          </button>
        </div>
      </div>

      {showCancelModal && (
        <CancelReasonModal
          title="Cancel Order"
          subtitle="If payment was already made, a refund will be initiated automatically."
          onClose={() => setShowCancelModal(false)}
          onConfirm={handleCancelOrder}
          loading={cancelling}
        />
      )}
    </div>
  );
};

export default OrderDetails;
