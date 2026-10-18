import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { useLocation, useNavigate } from "react-router-dom";
import {
  MdShoppingBag, MdArrowBack,
  MdArrowForward, MdWarning,
  MdAccessTime
} from "react-icons/md";
import api from "../../backend/api";
import OrderDetails from "./OrderDetails";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";
import { getOrderStatus } from "../../utils/orderUtils";
import { retryPayment } from "../../utils/paymentUtils";

const OrderSkeleton = () => (
  <div className="surface p-5 animate-pulse">
    <div className="flex items-center justify-between">
      <div className="space-y-2 flex-1">
        <div className="h-3.5 bg-gray-100 rounded w-1/4" />
        <div className="h-3 bg-gray-100 rounded w-1/3" />
        <div className="h-3 bg-gray-100 rounded w-1/5 mt-1" />
      </div>
      <div className="h-9 w-28 bg-gray-100 rounded-xl ml-4 shrink-0" />
    </div>
  </div>
);

const EmptyOrders = ({ onShop }) => (
  <div className="flex flex-col items-center justify-center py-20 text-center">
    <div className="w-20 h-20 rounded-2xl bg-gray-100 flex items-center justify-center mb-5">
      <MdShoppingBag size={36} className="text-gray-300" />
    </div>
    <h2 className="text-lg font-bold text-gray-800 mb-1">No orders yet</h2>
    <p className="text-sm text-gray-400 mb-6">You haven't placed any orders. Start shopping!</p>
    <button
      onClick={onShop}
      className="flex items-center gap-2 bg-gray-950 hover:bg-gray-800 active:scale-[0.98] text-white text-sm font-bold px-6 py-2.5 rounded-xl transition-[transform,opacity,background-color]"
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
      className="bg-blue-500 hover:bg-blue-600 active:scale-[0.98] text-white text-sm font-bold px-6 py-2.5 rounded-xl transition-[transform,opacity,background-color]"
    >
      Try Again
    </button>
  </div>
);

const OrderCard = ({ ord, onClick, onResumePayment, resumingOrderId }) => {
  const { label, color, sublabel, icon: StatusIcon } = getOrderStatus(ord.orderStatus);
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
            <p className="text-sm font-bold text-gray-900">Order #{ord.orderId}</p>
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold ${color}`}>
              <StatusIcon size={10} /> {label}
            </span>
          </div>
          <p className="text-[11px] text-gray-400 flex items-center gap-1">
            <MdAccessTime size={11} /> {formatDate(ord.createdAt)}
            {sublabel && (
              <>
                <span className="text-gray-300">·</span>
                {sublabel}
              </>
            )}
          </p>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <span className="text-base font-black text-gray-950">{formatCurrency(ord.totalAmount)}</span>
          <MdArrowForward size={16} className="text-gray-300 group-hover:text-blue-500 transition-colors" />
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

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [resumingOrderId, setResumingOrderId] = useState(null);

  const fetchOrders = async () => {
    try {
      setLoading(true);
      setError("");
      const { data } = await api.get("/user/orders/summary");
      setOrders(data || []);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || "Failed to fetch orders");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchOrders(); }, []);

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
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-blue-50 rounded-xl">
            <MdShoppingBag size={20} className="text-blue-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">My Orders</h1>
            {!loading && !error && (
              <p className="text-xs text-gray-500 mt-0.5">
                {orders.length} order{orders.length !== 1 ? "s" : ""} placed
              </p>
            )}
          </div>
          <button
            onClick={() => navigate("/products")}
            className="ml-auto flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors"
          >
            <MdArrowBack size={13} /> Continue Shopping
          </button>
        </div>

        {/* Content */}
        {loading && !orders.length ? (
          <div className="space-y-3">
            {[1, 2, 3].map(i => <OrderSkeleton key={i} />)}
          </div>
        ) : error ? (
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