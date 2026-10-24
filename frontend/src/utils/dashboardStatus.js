import { MdCheckCircle, MdCancel, MdHourglassEmpty, MdAccessTime, MdLocalShipping } from "react-icons/md";
export const ORDER_STATUS_MAP = {
    CREATED: { label: "Awaiting Payment", color: "bg-yellow-100 text-yellow-700", icon: MdHourglassEmpty },
    CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    FULFILLMENT_PROCESSING: { label: "Processing", color: "bg-blue-100 text-blue-700", icon: MdAccessTime },
    FULFILLMENT_COMPLETE: { label: "Delivered", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    CANCELLED: { label: "Cancelled", color: "bg-red-100 text-red-600", icon: MdCancel },
};

export const ORDER_LINE_STATUS_MAP = {
    CREATED: { label: "Awaiting Confirmation", color: "bg-yellow-100 text-yellow-700", icon: MdHourglassEmpty },
    CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    PROCESSING: { label: "Processing", color: "bg-blue-100 text-blue-700", icon: MdAccessTime },
    PARTIALLY_PROCESSED: { label: "Partially Processed", color: "bg-blue-50 text-blue-500", icon: MdAccessTime },
    FULFILLED: { label: "Fulfilled", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
    PARTIALLY_FULFILLED: { label: "Partially Fulfilled", color: "bg-blue-50 text-blue-600", icon: MdLocalShipping },
    CANCELLED: { label: "Cancelled", color: "bg-red-100 text-red-500", icon: MdCancel },
};
