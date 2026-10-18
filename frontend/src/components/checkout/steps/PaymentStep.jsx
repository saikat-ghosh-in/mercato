import { FaChevronLeft, FaChevronRight } from "react-icons/fa";
import { MdPayment } from "react-icons/md";

const Spinner = () => (
  <svg className="animate-spin h-3.5 w-3.5" viewBox="0 0 24 24" fill="none">
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
  </svg>
);

const PaymentStep = ({ onBack, onSubmit, loading, disabled }) => (
  <div className="surface overflow-hidden">
    <div className="px-5 py-4 border-b border-gray-100">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest">
        Payment
      </p>
    </div>

    <div className="p-5">
      <div className="mb-6 p-4 bg-blue-50 rounded-xl border border-blue-100">
        <div className="flex items-start gap-3">
          <MdPayment size={24} className="text-blue-500 mt-0.5 flex-shrink-0" />
          <div>
            <h3 className="text-sm font-bold text-gray-900 mb-1">Secure Payment via Cashfree</h3>
            <p className="text-xs text-gray-600">Complete your payment securely with multiple payment options including cards, UPI, net banking, and more.</p>
          </div>
        </div>
      </div>

      <div className="flex justify-between items-center pt-4 border-t border-gray-100">
        <button
          onClick={onBack}
          className="control flex items-center gap-2 px-5 py-2.5 bg-white border border-gray-200 hover:border-gray-300 text-gray-700 text-xs font-bold rounded-xl transition-colors"
        >
          <FaChevronLeft size={11} /> Back
        </button>
        <button
          onClick={onSubmit}
          disabled={loading || disabled}
          className="control control-prominent flex items-center gap-2 px-6 py-2.5 bg-blue-500 hover:bg-blue-600 text-white text-xs font-bold rounded-xl transition-[transform,opacity,background-color] active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
        >
          {loading ? (
            <><Spinner /> Preparing...</>
          ) : (
            <>Proceed to Payment <FaChevronRight size={11} /></>
          )}
        </button>
      </div>
    </div>
  </div>
);

export default PaymentStep;