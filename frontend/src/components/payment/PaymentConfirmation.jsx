import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { FaCheckCircle, FaTimesCircle, FaClock } from "react-icons/fa";
import { MdShoppingBag } from "react-icons/md";
import api from "../../backend/api";
import { syncCartFromBackend } from "../../reduxStore/actions/cartActions";
import { clearPaymentData } from "../../reduxStore/actions/paymentActions";

const TERMINAL_FAILURE = new Set(["FAILED", "USER_DROPPED", "CANCELLED"]);
const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 45000;

const PaymentConfirmation = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();

  const searchParams = new URLSearchParams(window.location.search);
  const orderId = searchParams.get("order_id");

  const [status, setStatus] = useState(orderId ? "loading" : "failed");
  const [paymentStatus, setPaymentStatus] = useState(null);

  useEffect(() => {
    if (!orderId) {
      return;
    }

    let cancelled = false;
    let timeoutId;
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    const controller = new AbortController();

    const resolveStatus = async () => {
      try {
        await api.post(`/orders/${orderId}/sync-payment-refund`, undefined, { signal: controller.signal, timeout: 10000 });
        const { data: order } = await api.get(`/user/orders/${orderId}`, { signal: controller.signal, timeout: 10000 });
        if (cancelled) return true;

        const currentStatus = order?.paymentSummary?.status;
        setPaymentStatus(currentStatus);

        if (currentStatus === "SUCCESS" && order?.inventoryFinalizationFailed) {
          dispatch(clearPaymentData());
          setStatus("inventory_failed");
          return true;
        }

        if (currentStatus === "SUCCESS") {
          dispatch(syncCartFromBackend());
          dispatch(clearPaymentData());
          setStatus("success");
          return true;
        }

        if (TERMINAL_FAILURE.has(currentStatus)) {
          dispatch(clearPaymentData());
          setStatus("failed");
          return true;
        }

        return false;
      } catch (error) {
        if (!cancelled) {
          console.error("Payment status sync failed:", error);
        }
        return false;
      }
    };

    const poll = async () => {
      const done = await resolveStatus();
      if (done || cancelled) return;

      if (Date.now() >= deadline) {
        dispatch(clearPaymentData());
        setStatus("pending");
      } else {
        timeoutId = setTimeout(poll, POLL_INTERVAL_MS);
      }
    };

    poll();


    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timeoutId);
    };
  }, [orderId, dispatch]);

  if (status === "loading") {
    return (
      <div className="bg-gray-50 min-h-screen flex items-center justify-center">
        <div className="text-center">
          <svg className="animate-spin h-8 w-8 text-blue-500 mx-auto mb-4" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
          </svg>
          <p className="text-gray-500 text-sm">Confirming your payment...</p>
        </div>
      </div>
    );
  }

  if (status === "inventory_failed") {
    return (
      <div className="bg-gray-50 min-h-screen py-6 flex items-center justify-center">
        <div className="max-w-md w-full mx-auto px-4">
          <div className="surface p-8 text-center">
            <div className="flex justify-center mb-4">
              <div className="p-3 bg-amber-50 rounded-2xl">
                <FaTimesCircle size={40} className="text-amber-500" />
              </div>
            </div>
            <h2 className="text-xl font-bold text-gray-900 mb-2">Payment received, stock unavailable</h2>
            <p className="text-sm text-gray-500 mb-6">
              Your payment went through, but stock could not be confirmed. Check your order for updates and contact support. Avoid paying again for this order.
            </p>
            <div className="flex flex-col gap-3">
              <button
                onClick={() => navigate(orderId ? `/orders?orderId=${orderId}` : "/orders")}
                className="w-full py-3 bg-blue-500 hover:bg-blue-600 text-white text-sm font-bold rounded-xl transition-[transform,opacity,background-color]"
              >
                View Order
              </button>
              <button
                onClick={() => navigate("/products")}
                className="w-full py-3 bg-blue-50 hover:bg-blue-100 text-blue-600 text-sm font-bold rounded-xl transition-colors"
              >
                Continue Shopping
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (status === "failed") {
    return (
      <div className="bg-gray-50 min-h-screen py-6 flex items-center justify-center">
        <div className="max-w-md w-full mx-auto px-4">
          <div className="surface p-8 text-center">
            <div className="flex justify-center mb-4">
              <div className="p-3 bg-red-50 rounded-2xl">
                <FaTimesCircle size={40} className="text-red-400" />
              </div>
            </div>
            <h2 className="text-xl font-bold text-gray-900 mb-2">Payment not completed</h2>
            <p className="text-sm text-gray-500 mb-6">
              {paymentStatus
                ? `Payment status: ${paymentStatus.replaceAll("_", " ").toLowerCase()}`
                : "We couldn't confirm your payment. You can retry from your orders page."}
            </p>
            <div className="flex flex-col gap-3">
              <button
                onClick={() => navigate(orderId ? `/orders?orderId=${orderId}` : "/orders")}
                className="w-full py-3 bg-blue-500 hover:bg-blue-600 text-white text-sm font-bold rounded-xl transition-[transform,opacity,background-color]"
              >
                View Order & Retry Payment
              </button>
              <button
                onClick={() => navigate("/cart")}
                className="w-full py-3 bg-blue-50 hover:bg-blue-100 text-blue-600 text-sm font-bold rounded-xl transition-colors"
              >
                Return to Cart
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (status === "pending") {
    return (
      <div className="bg-gray-50 min-h-screen py-6 flex items-center justify-center">
        <div className="max-w-md w-full mx-auto px-4">
          <div className="surface p-8 text-center">
            <div className="flex justify-center mb-4">
              <div className="p-3 bg-yellow-50 rounded-2xl">
                <FaClock size={40} className="text-yellow-500" />
              </div>
            </div>
            <h2 className="text-xl font-bold text-gray-900 mb-2">Payment processing</h2>
            <p className="text-sm text-gray-500 mb-6">
              Your payment is still being confirmed. Check your order shortly for the latest status.
            </p>
            <button
              onClick={() => navigate(orderId ? `/orders?orderId=${orderId}` : "/orders")}
              className="w-full py-3 bg-blue-500 hover:bg-blue-600 text-white text-sm font-bold rounded-xl transition-[transform,opacity,background-color]"
            >
              View Order
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-gray-50 min-h-screen py-6 flex items-center justify-center">
      <div className="max-w-md w-full mx-auto px-4">
        <div className="surface p-8 text-center">
          <div className="flex justify-center mb-4">
            <div className="p-3 bg-blue-50 rounded-2xl">
              <FaCheckCircle size={40} className="text-blue-500" />
            </div>
          </div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Payment confirmed!</h2>
          <p className="text-sm text-gray-500 mb-2">
            Your order is confirmed. We'll email you a receipt shortly.
          </p>

          {orderId && (
            <div className="mb-6 p-4 bg-gray-50 rounded-xl text-left">
              <div className="flex justify-between text-sm">
                <span className="text-gray-400">Order</span>
                <span className="font-semibold text-gray-800">#{orderId}</span>
              </div>
            </div>
          )}

          <div className="flex flex-col gap-3">
            <button
              onClick={() => navigate(orderId ? `/orders?orderId=${orderId}` : "/orders")}
              className="w-full flex items-center justify-center gap-2 py-3 bg-blue-500 hover:bg-blue-600 active:scale-[0.98] text-white text-sm font-bold rounded-xl transition-[transform,opacity,background-color]"
            >
              <MdShoppingBag size={16} /> View Order
            </button>
            <button
              onClick={() => navigate("/products")}
              className="w-full py-3 bg-blue-50 hover:bg-blue-100 text-blue-600 text-sm font-bold rounded-xl transition-colors"
            >
              Continue Shopping
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PaymentConfirmation;
