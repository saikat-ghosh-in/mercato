import { formatCurrency } from "../../utils/formatCurrency";

const OrderSummary = ({ cartWithProducts, cartQty, subtotal, shipping, platformFee, processingAndHandling, total }) => (
  <div className="order-summary">
    <h2 className="text-base font-bold text-gray-900 mb-4">Order Summary</h2>

    {/* Mini item previews */}
    {cartWithProducts.length > 0 && (
      <div className="mb-4 space-y-2.5">
        {cartWithProducts.slice(0, 3).map((item) => (
          <div key={item.productId} className="flex items-center gap-2.5">
            <div className="w-16 h-16 rounded-lg bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
              {item?.product?.primaryImageUrl ? (
                <img src={item.product.primaryImageUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-gray-200 text-sm select-none">◈</div>
              )}
            </div>
            <p className="text-xs text-gray-600 truncate flex-1 leading-snug">
              {item?.product?.productName}
            </p>
            <span className="text-xs font-semibold text-gray-400 shrink-0">×{item.quantity}</span>
          </div>
        ))}
        {cartWithProducts.length > 3 && (
          <p className="text-[11px] text-gray-400 pl-10">
            +{cartWithProducts.length - 3} more item{cartWithProducts.length - 3 !== 1 ? "s" : ""}
          </p>
        )}
      </div>
    )}

    <div className="space-y-3 pb-4 border-b border-gray-100">
      <SummaryRow label={`Subtotal (${cartQty} ${cartQty === 1 ? "item" : "items"})`} value={formatCurrency(subtotal)} />
      <SummaryRow
        label="Shipping"
        value={
          shipping === 0
            ? <span className="font-semibold text-blue-700">FREE</span>
            : formatCurrency(shipping)
        }
      />
      <SummaryRow label="Platform Fee" value={formatCurrency(platformFee)} />
      <SummaryRow label="Processing & Handling" value={formatCurrency(processingAndHandling)} />
    </div>

    <div className="flex justify-between items-center pt-4">
      <span className="text-sm font-bold text-gray-900">Total</span>
      <span className="text-2xl font-black text-gray-950">{formatCurrency(total)}</span>
    </div>
  </div>
);

const SummaryRow = ({ label, value }) => (
  <div className="flex justify-between items-center text-sm">
    <span className="text-gray-500">{label}</span>
    <span className="font-semibold text-gray-800">{value}</span>
  </div>
);

export default OrderSummary;