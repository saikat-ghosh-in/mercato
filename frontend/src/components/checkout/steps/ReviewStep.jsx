import { FaChevronLeft, FaChevronRight } from "react-icons/fa";
import { formatCurrency } from "../../../utils/formatCurrency";

const ReviewStep = ({ selectedAddress, cartWithProducts, onBack, onNext }) => (
  <div className="surface overflow-hidden">
    <div className="px-5 py-4 border-b border-gray-100">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest">
        Review Your Order
      </p>
    </div>

    <div className="p-5 space-y-4">
      {/* Shipping To */}
      <div className="bg-gray-50 rounded-xl p-4">
        <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-3">
          Shipping To
        </p>
        {selectedAddress && (
          <div className="text-sm space-y-0.5">
            <p className="font-bold text-gray-900">{selectedAddress.recipientName}</p>
            <p className="text-gray-500">{selectedAddress.addressLine1}</p>
            {selectedAddress.addressLine2 && (
              <p className="text-gray-500">{selectedAddress.addressLine2}</p>
            )}
            <p className="text-gray-500">
              {selectedAddress.city}, {selectedAddress.state} {selectedAddress.pincode}
            </p>
            <p className="text-gray-500">{selectedAddress.country}</p>
            <p className="text-xs text-gray-400 pt-1"> Ph. {selectedAddress.recipientPhone}</p>
          </div>
        )}
      </div>

      {/* Items */}
      <div className="bg-gray-50 rounded-xl p-4">
        <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-3">
          Items ({cartWithProducts.length})
        </p>
        <div className="divide-y divide-gray-100">
          {cartWithProducts.map((item) => (
            <div key={item.productId} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
              <div className="w-10 h-10 rounded-xl bg-white border border-gray-100 overflow-hidden shrink-0">
                {item?.product?.primaryImageUrl ? (
                  <img
                    src={item.product.primaryImageUrl}
                    alt=""
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-gray-200 text-lg select-none">◈</div>
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-900 truncate">
                  {item?.product?.productName || "Item"}
                </p>
                <p className="text-xs text-gray-400">Qty: {item.quantity}</p>
              </div>
              <p className="text-sm font-bold text-gray-900 shrink-0">
                {formatCurrency(item.lineTotal)}
              </p>
            </div>
          ))}
        </div>
      </div>

      <div className="flex justify-between gap-3 pt-2">
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-5 py-2.5 bg-white border border-gray-200 hover:border-gray-300 text-gray-700 text-xs font-bold rounded-xl transition-colors"
        >
          <FaChevronLeft size={11} /> Back
        </button>
        <button
          onClick={onNext}
          className="flex items-center gap-2 px-5 py-2.5 bg-blue-500 hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-bold rounded-xl transition-[transform,opacity,background-color]"
        >
          Continue to Payment <FaChevronRight size={11} />
        </button>
      </div>
    </div>
  </div>
);

export default ReviewStep;