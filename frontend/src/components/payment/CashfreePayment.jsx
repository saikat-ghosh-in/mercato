import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { load } from "@cashfreepayments/cashfree-js";
import { MdPayment } from "react-icons/md";
import { formatCurrency } from "../../utils/formatCurrency";

const CashfreePayment = () => {
    const navigate = useNavigate();
    const { paymentData, selectedAddressForCheckout } = useSelector((s) => s.auth);
    const [initializing, setInitializing] = useState(true);

    useEffect(() => {
        if (!paymentData || !paymentData.paymentSessionId) {
            toast.error("Payment session missing. Please start from checkout.");
            navigate("/checkout");
            return;
        }

        const initAndRedirect = async () => {
            try {
                const cashfree = await load({
                    mode: import.meta.env.VITE_CASHFREE_MODE || "sandbox"
                });

                const checkoutOptions = {
                    paymentSessionId: paymentData.paymentSessionId,
                    redirectTarget: "_self"
                };

                await cashfree.checkout(checkoutOptions);
            } catch (err) {
                toast.error("Failed to initialize payment. Please try again.");
                console.error("Cashfree init error:", err);
                navigate("/checkout");
            } finally {
                setInitializing(false);
            }
        };

        initAndRedirect();
    }, [navigate, paymentData]);

    return (
        <div className="bg-gray-50 min-h-screen py-6">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-blue-50 rounded-xl">
                        <MdPayment size={20} className="text-blue-500" />
                    </div>
                    <div>
                        <h1 className="text-xl font-bold text-gray-900">Complete Payment</h1>
                        <p className="text-xs text-gray-500 mt-0.5">Secured by Cashfree</p>
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">

                    {/* Loading / redirecting state */}
                    <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-100 shadow-sm p-10 flex flex-col items-center justify-center gap-4">
                        <svg className="animate-spin h-8 w-8 text-blue-500" viewBox="0 0 24 24" fill="none">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                        </svg>
                        <p className="text-gray-500 text-sm">
                            {initializing ? "Redirecting to payment..." : "Loading..."}
                        </p>
                    </div>

                    {/* Summary Sidebar */}
                    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 sticky top-4">
                        <h2 className="text-base font-bold text-gray-900 mb-5">Order Summary</h2>
                        <div className="pb-4 border-b border-gray-100 space-y-2">
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-500">Order</span>
                                <span className="font-semibold text-gray-800 font-mono text-xs">
                                    #{paymentData?.orderId}
                                </span>
                            </div>
                            {selectedAddressForCheckout && (
                                <div className="flex justify-between text-sm">
                                    <span className="text-gray-500">Shipping to</span>
                                    <span className="font-semibold text-gray-800 text-right max-w-35 truncate">
                                        {selectedAddressForCheckout.city}, {selectedAddressForCheckout.state}
                                    </span>
                                </div>
                            )}
                        </div>
                        <div className="flex justify-between items-center py-4">
                            <span className="text-sm font-bold text-gray-900">Total</span>
                            <span className="text-2xl font-black text-gray-950">
                                {formatCurrency(paymentData?.totalAmount || 0)}
                            </span>
                        </div>
                        <div className="flex items-center gap-2 p-3 bg-gray-50 rounded-xl">
                            <span className="text-lg">🔒</span>
                            <p className="text-xs text-gray-400">Your payment is encrypted and secure</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default CashfreePayment;
