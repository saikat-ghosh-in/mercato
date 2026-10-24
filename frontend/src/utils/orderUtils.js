import {
  MdCheckCircle, MdHourglassEmpty,
  MdAccessTime, MdBlock,
} from "react-icons/md";

export const ORDER_STATUS_MAP = {
  CREATED: {
    label: "Pending Payment",
    sublabel: "Awaiting payment confirmation",
    color: "bg-yellow-100 text-yellow-700",
    bannerBg: "bg-yellow-50 border-yellow-100",
    iconColor: "text-yellow-500",
    icon: MdHourglassEmpty,
  },
  CONFIRMED: {
    label: "Order Confirmed",
    sublabel: "Payment received, preparing your order",
    color: "bg-blue-100 text-blue-700",
    bannerBg: "bg-blue-50 border-blue-100",
    iconColor: "text-blue-500",
    icon: MdCheckCircle,
  },
  FULFILLMENT_PROCESSING: {
    label: "Being Prepared",
    sublabel: "Seller is picking and packing your order",
    color: "bg-blue-100 text-blue-700",
    bannerBg: "bg-blue-50 border-blue-100",
    iconColor: "text-blue-500",
    icon: MdAccessTime,
  },
  FULFILLMENT_COMPLETE: {
    label: "Shipped",
    sublabel: "Your order has been shipped",
    color: "bg-blue-100 text-blue-700",
    bannerBg: "bg-blue-50 border-blue-100",
    iconColor: "text-blue-500",
    icon: MdCheckCircle,
  },
  CANCELLED: {
    label: "Cancelled",
    sublabel: "This order has been cancelled",
    color: "bg-gray-100 text-gray-500",
    bannerBg: "bg-gray-50 border-gray-200",
    iconColor: "text-gray-400",
    icon: MdBlock,
  },
};

export const getOrderStatus = (raw) => ORDER_STATUS_MAP[raw?.toUpperCase()] ?? {
  label: raw ?? "Unknown",
  sublabel: "",
  color: "bg-gray-100 text-gray-600",
  bannerBg: "bg-gray-50 border-gray-100",
  iconColor: "text-gray-400",
  icon: MdAccessTime,
};

export const QUANTITY_CONFIG = [
  { key: "orderedQty", label: "ordered", className: "bg-gray-100 text-gray-700" },
  { key: "shippedQty", label: "shipped", className: "bg-blue-50 text-blue-800" },
  { key: "cancelledQty", label: "cancelled", className: "bg-red-50 text-red-800" },
  { key: "pendingQty", label: "pending", className: "bg-amber-50 text-amber-800" },
];

export const LINE_STATUS_MAP = {
  CREATED: { label: "Pending", color: "bg-yellow-100 text-yellow-700" },
  CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700" },
  PROCESSING: { label: "Processing", color: "bg-blue-100 text-blue-700" },
  PARTIALLY_PROCESSED: { label: "Part. Processed", color: "bg-blue-50 text-blue-500" },
  FULFILLED: { label: "Fulfilled", color: "bg-blue-100 text-blue-700" },
  PARTIALLY_FULFILLED: { label: "Part. Fulfilled", color: "bg-blue-50 text-blue-600" },
  CANCELLED: { label: "Cancelled", color: "bg-gray-100 text-gray-500" },
};

export const getLineStatus = (raw) =>
  LINE_STATUS_MAP[raw?.toUpperCase()] ?? { label: raw ?? "—", color: "bg-gray-100 text-gray-500" };

const PAYMENT_STATUS_STYLE = {
  SUCCESS: { text: "text-blue-700", border: "border-blue-100/60", badge: "bg-blue-100 text-blue-700" },
  PENDING: { text: "text-yellow-700", border: "border-yellow-100/60", badge: "bg-yellow-100 text-yellow-700" },
  FAILED: { text: "text-red-600", border: "border-red-100/60", badge: "bg-red-100 text-red-600" },
  USER_DROPPED: { text: "text-orange-600", border: "border-orange-100/60", badge: "bg-orange-100 text-orange-600" },
  CANCELLED: { text: "text-gray-500", border: "border-gray-100/60", badge: "bg-gray-100 text-gray-500" },
};

const REFUND_STATUS_STYLE = {
  SUCCESS: { text: "text-blue-700", border: "border-blue-100/60" },
  PENDING: { text: "text-yellow-700", border: "border-yellow-100/60" },
  PROCESSING: { text: "text-blue-700", border: "border-blue-100/60" },
  FAILED: { text: "text-red-600", border: "border-red-100/60" },
};

export const getPaymentStatusStyle = (raw) =>
  PAYMENT_STATUS_STYLE[raw?.toUpperCase()] ?? {
    text: "text-gray-600", border: "border-gray-100/60", badge: "bg-gray-100 text-gray-600",
  };

export const getRefundStatusStyle = (raw) =>
  REFUND_STATUS_STYLE[raw?.toUpperCase()] ?? {
    text: "text-gray-600", border: "border-gray-100/60",
  };

export const getPaymentMessage = (status, amount) => {
  switch (status?.toUpperCase()) {
    case "SUCCESS": return `Payment of ${amount} was successful`;
    case "PENDING": return `Payment of ${amount} is pending confirmation`;
    case "FAILED": return `Payment of ${amount} failed`;
    case "USER_DROPPED": return `Payment was not completed`;
    case "CANCELLED": return `Payment was cancelled`;
    default: return `Payment status: ${status}`;
  }
};

export const getRefundMessage = (status, amount) => {
  switch (status?.toUpperCase()) {
    case "SUCCESS": return `Refund of ${amount} has been processed`;
    case "PENDING": return `Refund of ${amount} is being initiated`;
    case "PROCESSING": return `Refund of ${amount} is on its way`;
    case "FAILED": return `Refund of ${amount} could not be processed`;
    default: return `Refund status: ${status}`;
  }
};

export const paymentFooterBg = {
  SUCCESS: "bg-blue-50",
  PENDING: "bg-yellow-50",
  FAILED: "bg-red-50",
  USER_DROPPED: "bg-orange-50",
  CANCELLED: "bg-gray-50",
};

export const refundFooterBg = {
  SUCCESS: "bg-blue-50",
  PENDING: "bg-yellow-50",
  PROCESSING: "bg-blue-50",
  FAILED: "bg-red-50",
};

const CANCEL_WINDOW_MS = 60 * 60 * 1000;

export const getCancelWindowRemaining = (completedAt, now = Date.now()) => {
  if (!completedAt) return null;
  const elapsed = now - new Date(completedAt).getTime();
  const remaining = CANCEL_WINDOW_MS - elapsed;
  return remaining > 0 ? remaining : 0;
};

export const formatTimeRemaining = (ms) => {
  if (!ms || ms <= 0) return null;
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${seconds}s`;
  return `${seconds}s`;
};

export const CANCEL_REASONS = [
  "Changed my mind",
  "Found a better price elsewhere",
  "Ordered by mistake",
  "Delivery time too long",
  "Other",
];
