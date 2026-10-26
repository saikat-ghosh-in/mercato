import { Spinner } from "../shared/Spinner";
import { LoadingLayout, Skeleton as ImageSkeleton } from "../shared/LoadingStates";
import PageHeading from "../shared/PageHeading";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import {
  MdArrowBack, MdLocationOn, MdPayment, MdShoppingBag,
  MdWarning, MdContentCopy, MdShoppingCart,
  MdSync, MdAutorenew
} from "react-icons/md";
import toast from "../../utils/toastUtils.js";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";
import useWorkflowResource from "../../hooks/useWorkflowResource";
import { actionMaximum, canCancelWhole, workflowLabel, paymentPolicy } from "../../utils/fulfillment";
import { QuantityCommand, WorkflowProgress, CancellationPolicy } from "./WorkflowControls";
import {
  getOrderStatus,
  getPaymentStatusStyle, getRefundStatusStyle,
  getPaymentMessage, getRefundMessage,
  paymentFooterBg,
  refundFooterBg,
} from "../../utils/orderUtils";
import { syncPayment, retryPayment } from "../../utils/paymentUtils";

const SyncPaymentButton = ({ orderId, onSync }) => {
  const [loading, setLoading] = useState(false);
  return (
    <button
      onClick={() => syncPayment({ orderId, onSuccess: onSync, setLoading })}
      disabled={loading}
      className="control flex items-center gap-1.5 text-xs font-bold text-yellow-700 bg-yellow-100
        hover:bg-yellow-200 px-3 py-2 rounded-xl transition-colors cursor-pointer
        disabled:opacity-50 "
    >
      {loading ? <Spinner size={14} /> : <MdSync size={14} />}
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
      className="control flex items-center gap-1.5 text-xs font-bold text-white bg-blue-500
        hover:bg-blue-600 px-3 py-2 rounded-xl transition-colors cursor-pointer
        disabled:opacity-50 "
    >
      <MdPayment size={14} />
      {loading ? "Redirecting..." : "Retry Payment"}
    </button>
  );
};

const StatusBanner = ({ order }) => {
  const { bannerBg, iconColor, icon: StatusIcon } = getOrderStatus(order.orderStatus);

  return (
    <div className={`rounded-2xl border mb-6 ${bannerBg}`}>
      <div className="flex items-center gap-3 px-5 py-4">
        <StatusIcon size={22} className={iconColor} />
        <div className="flex-1 min-w-0">
          <p className="text-base font-semibold text-gray-900">{workflowLabel(order)}</p>
          <p className="text-xs text-gray-500 mt-0.5">Progress is shown separately for each quantity.</p>
        </div>
      </div>
    </div>
  );
};

const Skeleton = () => <LoadingLayout variant="checkout" label="Loading content" />;

const SectionCard = ({ title, icon: Icon, children }) => (
  <div className="surface p-5">
    {title && (
      <h3 className="text-xs font-bold text-gray-400  mb-4 flex items-center gap-1.5">
        {Icon && <Icon size={13} />} {title}
      </h3>
    )}
    {children}
  </div>
);

const InfoRow = ({ label, value, mono, copyable, color, last }) => {
  const handleCopy = async () => { try { await navigator.clipboard.writeText(value); toast.success("Copied to clipboard"); } catch { toast.error("Couldn’t copy. Select and copy the value instead."); } };
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

const OrderLineItem = ({ line, version, onLineUpdate, refreshing }) => {
  const navigate = useNavigate();
  const [fullProduct, setFullProduct] = useState(null);
  const [imageLoading, setImageLoading] = useState(() => !line.product?.primaryImageUrl && Boolean(line.product?.productId));

  // Fetch full product details if primaryImageUrl is missing
  useEffect(() => {
    const controller = new AbortController();
    if (!line.product?.primaryImageUrl && line.product?.productId) {
      api.get(`/public/products/${line.product.productId}`, {signal:controller.signal})
        .then(({ data }) => { if (!controller.signal.aborted) setFullProduct(data); })
        .catch(err => console.error(`Failed to fetch product ${line.product.productId}:`, err))
        .finally(() => { if (!controller.signal.aborted) setImageLoading(false); });
    }
    return () => controller.abort();
  }, [line.product?.productId, line.product?.primaryImageUrl]);

  const sellerName = line.seller?.name ?? "Unknown Seller";

  return (
    <>
      <div className="flex items-start gap-3 py-3 last:pb-0 border-b border-gray-100 last:border-0 rounded-lg px-1">
        <div className="w-14 h-14 rounded-xl bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
          {imageLoading ? (
            <ImageSkeleton shape="block" className="w-full h-full" />
          ) : (fullProduct?.primaryImageUrl || line.product?.primaryImageUrl) ? (
            <img src={fullProduct?.primaryImageUrl || line.product?.primaryImageUrl} alt={line.product.productName} className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-gray-200 text-2xl select-none">◈</div>
          )}
        </div>

        <div className="flex-1 min-w-0">
          <button className="text-sm font-semibold text-gray-900 text-left hover:text-blue-600" onClick={() => navigate(`/products/${line.product.productId}`)}>{line.product?.productName ?? "Product"}</button>
          {/* Seller and unit price */}
          <p className="text-xs text-gray-400 mt-0.5">
            by {sellerName}&nbsp;·&nbsp;{formatCurrency(line.product?.unitPrice)} / unit
          </p>
          <div className="mt-2"><WorkflowProgress record={line} /></div>
          <div className="mt-2"><QuantityCommand label="Cancel units" cancellation maximum={actionMaximum(line,'CANCEL')} version={version} endpoint="/user/fulfillment/commands" makeBody={(qty,reason) => ({fulfillmentId:line.fulfillmentId,orderLineNumber:line.orderLineNumber,action:'CANCEL',qty,reason})} onComplete={onLineUpdate} disabled={refreshing} /></div>
        </div>

        <div className="flex flex-col items-end gap-1 shrink-0">
          <span className="text-sm font-semibold text-gray-900">{formatCurrency(line.lineTotal)}</span>

        </div>
      </div>

    </>
  );
};

const OrderDetails = ({ orderId }) => {
  const navigate = useNavigate();
  const {data:order, loading, error, refresh:fetchOrder, epoch} = useWorkflowResource(`/user/orders/${encodeURIComponent(orderId)}`);
  const canCancelOrder = canCancelWhole(order);

  if (loading && !order) return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8"><Skeleton /></div>
    </div>
  );

  if (!order) return (
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
            className="control inline-flex items-center gap-2 bg-gray-950 hover:bg-gray-800 text-white text-sm font-bold px-5 py-2.5 rounded-xl transition-[transform,opacity,background-color] "
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
        <PageHeading title={"Order #" + order.orderId} description={"Placed on " + formatDate(order.createdAt)}><button type="button" className="button button-outline" onClick={() => navigate("/orders")}><MdArrowBack aria-hidden="true" /> All orders</button></PageHeading>

        {error && <p role="alert" className="text-sm text-red-700">{error} <button className="text-link" onClick={fetchOrder}>Retry refresh</button></p>}
        <div className="surface p-4 mb-4 space-y-3"><WorkflowProgress record={order} /><p className="text-sm text-gray-600">You can repeatedly cancel unprepared units, even when other units have advanced. Preparation ends cancellation eligibility for those units.</p><QuantityCommand key={`${epoch}:${orderId}:whole`} label="Cancel entire order" cancellation whole maximum={canCancelOrder ? 1 : 0} version={order.workflow?.version} endpoint="/user/fulfillment/cancellations" makeBody={(_qty,reason) => ({orderId:order.orderId,reason})} onComplete={fetchOrder} disabled={loading} /><CancellationPolicy /></div>
        {/* Status banner */}
        <StatusBanner order={order} onRetrySuccess={() => fetchOrder(true)} />

        {/* Floating info bar */}
        {(order.orderStatus === "CONFIRMED" || order.orderStatus === "CREATED" || order.orderStatus === "CANCELLED") && (
          <div className="bg-white border border-gray-100 rounded-xl mb-4  overflow-hidden">

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
                        <p className="text-xs text-gray-400 mt-0.5">
                          Already paid? Use "Check Status" to sync your payment.
                        </p>
                      )}
                      {(paymentStatus === "FAILED" || paymentStatus === "USER_DROPPED") && (
                        <p className="text-xs text-gray-400 mt-0.5">
                          You can retry the payment to complete your order.
                        </p>
                      )}
                      {!["PENDING", "FAILED", "USER_DROPPED"].includes(paymentStatus) && (
                        <p className="text-xs text-gray-400 mt-0.5">
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

            {/* Refund pending row */}
            {isRefundPending && (
              <div className="flex items-start gap-2 px-4 py-2.5 bg-amber-50 border-t border-amber-100">
                <MdAutorenew size={14} className="text-amber-500 shrink-0 mt-0.5" />
                <p className="text-xs text-amber-700 leading-relaxed">
                  Refund progress is shown below when available. Partial customer cancellation retains fixed fees; final closure returns residual buyer charges.
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
                order.orderLines.map(line => (
                  <OrderLineItem
                    key={`${epoch}:${orderId}:${line.fulfillmentId}:${line.orderLineNumber}`}
                    line={line}
                    version={order.workflow?.version}
                    onLineUpdate={fetchOrder}
                    refreshing={loading}
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
              <p className="text-sm text-gray-600 mb-3">{paymentPolicy}</p>
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
                <span className="text-2xl font-semibold text-gray-950">{formatCurrency(order.totalAmount)}</span>
              </div>
            </SectionCard>

            <SectionCard>
              <div className="space-y-2.5">

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

          <button
            onClick={() => navigate("/products")}
            className={`flex-1 flex items-center justify-center gap-2 py-2.5 text-sm font-bold text-gray-700 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors cursor-pointer ${!canCancelOrder ? "col-span-2" : ""}`}
          >
            <MdShoppingCart size={15} /> Shop
          </button>
        </div>
      </div>

    </div>
  );
};

export default OrderDetails;
