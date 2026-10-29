import { LoadingLayout } from "../shared/LoadingStates";
import PageHeading from "../shared/PageHeading";
import ActionArrow from "../shared/ActionArrow";
import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { useLocation, useNavigate } from "react-router-dom";
import {
  MdShoppingBag,
  MdWarning,
  MdAccessTime
} from "react-icons/md";
import { useOrdersQuery } from "../../reduxStore/services/privateApi.js";
import OrderDetails from "./OrderDetails";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";
import { getOrderStatus } from "../../utils/orderUtils";
import { workflowLabel } from "../../utils/fulfillment";
import { retryPayment } from "../../utils/paymentUtils";

const OrderSkeleton = () => <LoadingLayout variant="list" label="Loading content" rows={1} />;

const EmptyOrders = ({ onShop }) => (
  <div className="flex flex-col items-center justify-center py-20 text-center">
    <div className="w-20 h-20 rounded-2xl bg-gray-100 flex items-center justify-center mb-5">
      <MdShoppingBag size={36} className="text-gray-300" />
    </div>
    <h2 className="text-lg font-bold text-gray-800 mb-1">No orders yet</h2>
    <p className="text-sm text-gray-400 mb-6">You haven't placed any orders. Start shopping!</p>
    <button
      onClick={onShop}
      className="control flex items-center gap-2 bg-gray-950 hover:bg-gray-800  text-white text-sm font-bold px-6 py-2.5 rounded-xl transition-[transform,opacity,background-color]"
    >
      <MdShoppingBag size={15} /> Start Shopping
    </button>
  </div>
);

const ErrorState = ({ message, onRetry }) => (
  <div className="flex flex-col items-center justify-center py-20 text-center">
    <div className="w-20 h-20 rounded-2xl bg-red-50 flex items-center justify-center mb-5">
      <MdWarning size={36} className="text-red-300" />
    </div>
    <h2 className="text-lg font-bold text-gray-800 mb-1">Failed to load orders</h2>
    <p className="text-sm text-gray-400 mb-6">{message}</p>
    <button
      onClick={onRetry}
      className="control bg-blue-500 hover:bg-blue-600  text-white text-sm font-bold px-6 py-2.5 rounded-xl transition-[transform,opacity,background-color]"
    >
      Try Again
    </button>
  </div>
);

const OrderCard = ({ ord, onClick, onResumePayment, resumingOrderId }) => {
  const { color, icon: StatusIcon } = getOrderStatus(ord.orderStatus);
  const needsPayment = ord.orderStatus === "CREATED"
    && ord.paymentStatus
    && ord.paymentStatus !== "SUCCESS";

  return (
    <div
      className="group surface  transition-[transform,opacity,background-color] duration-200 overflow-hidden"
    >
      <button type="button" aria-label={"View order " + ord.orderId}
        onClick={onClick}
        className="order-card-button flex items-center gap-4 px-5 py-4 cursor-pointer w-full text-left"
      >
        <div className="w-10 h-10 rounded-xl bg-gray-50 border border-gray-100 flex items-center justify-center shrink-0">
          <MdShoppingBag size={18} className="text-gray-400" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-0.5">
            <p className="text-sm font-semibold text-gray-900 break-words">Order #{ord.orderId}</p>
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${color}`}>
              <StatusIcon size={10} /> {workflowLabel(ord, 'customer')}
            </span>
          </div>
          <p className="text-xs text-gray-500 flex flex-wrap items-center gap-1">
            <MdAccessTime size={11} /> {formatDate(ord.createdAt)}
            <span className="text-gray-300">·</span> View quantity progress
          </p>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <span className="text-base font-semibold text-gray-950">{formatCurrency(ord.totalAmount)}</span>
          <ActionArrow size={16} className="text-gray-300 group-hover:text-blue-500 transition-colors" />
        </div>
      </button>
      {needsPayment && (
        <div className="px-5 pb-4">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onResumePayment?.(ord);
            }}
            className="w-full py-2.5 bg-blue-500 hover:bg-blue-600 text-white text-sm font-bold rounded-xl transition-colors disabled:opacity-60"
            disabled={resumingOrderId === ord.orderId}
          >
            Complete Payment
          </button>
        </div>
      )}
    </div>
  );
};


const Orders = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const orderId = new URLSearchParams(location.search).get("orderId");

  const { data: orders = [], isLoading: loading, error: queryError, refetch: fetchOrders } = useOrdersQuery();
  const error = queryError ? queryError.data?.message || "Failed to fetch orders" : "";
  const [resumingOrderId, setResumingOrderId] = useState(null);
  useEffect(() => { if (!orderId) fetchOrders(); }, [orderId, fetchOrders]);

  const handleResumePayment = async (ord) => {
    setResumingOrderId(ord.orderId);
    await retryPayment({
      order: { orderId: ord.orderId, totalAmount: ord.totalAmount },
      dispatch,
      navigate,
      setLoading: () => {},
    });
    setResumingOrderId(null);
  };

  if (orderId) return <OrderDetails orderId={orderId} />;

  return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <PageHeading title="Your orders" description={!loading && !error ? `${orders.length} ${orders.length === 1 ? "order" : "orders"} placed` : "Track deliveries and view previous orders."}><button type="button" className="button button-outline" onClick={() => navigate("/products")}>Continue shopping <ActionArrow /></button></PageHeading>

        {/* Content */}
        {error && orders.length > 0 && <p role="alert" className="text-sm text-red-700 mb-4">{error}. Your last loaded orders are retained. <button className="text-link" onClick={fetchOrders}>Retry refresh</button></p>}
        {loading && !orders.length ? (
          <div className="space-y-3">
            {[1, 2, 3].map(i => <OrderSkeleton key={i} />)}
          </div>
        ) : error && !orders.length ? (
          <div className="surface">
            <ErrorState message={error} onRetry={fetchOrders} />
          </div>
        ) : orders.length === 0 ? (
          <div className="surface">
            <EmptyOrders onShop={() => navigate("/products")} />
          </div>
        ) : (
          <div className="space-y-3">
            {orders.map(ord => (
              <OrderCard
                key={ord.orderId}
                ord={ord}
                onClick={() => navigate(`/orders?orderId=${ord.orderId}`)}
                onResumePayment={handleResumePayment}
                resumingOrderId={resumingOrderId}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default Orders;
