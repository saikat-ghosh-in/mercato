import { FaChevronRight, FaPlus } from "react-icons/fa";
import AddressList from "../../address/AddressList";

const AddressStep = ({
  addresses,
  selectedAddress,
  loadingAddresses,
  onAddAddress,
  onEditAddress,
  onDeleteAddress,
  onNext,
}) => (
  <div className="surface overflow-hidden">
    <div className="flex justify-between items-center px-5 py-4 border-b border-gray-100">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest">
        Shipping Address
      </p>
      <button
        onClick={onAddAddress}
        className="control flex items-center gap-1.5 text-xs font-bold text-blue-500 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors"
      >
        <FaPlus size={10} /> Add New
      </button>
    </div>

    <div className="p-5">
      {loadingAddresses && !addresses?.length ? (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-20 bg-gray-50 rounded-xl animate-pulse" />
          ))}
        </div>
      ) : addresses?.length > 0 ? (
        <AddressList
          addresses={addresses}
          selectedAddress={selectedAddress}
          onEditAddress={onEditAddress}
          onDeleteAddress={onDeleteAddress}
        />
      ) : (
        <div className="text-center py-12">
          <div className="text-4xl mb-3 select-none">📍</div>
          <p className="text-sm font-semibold text-gray-700 mb-1">No saved addresses</p>
          <p className="text-xs text-gray-400 mb-4">Add an address to continue</p>
          <button
            onClick={onAddAddress}
            className="control px-5 py-2.5 bg-blue-500 hover:bg-blue-600 text-white text-xs font-bold rounded-xl transition-colors"
          >
            Add Your First Address
          </button>
        </div>
      )}

      <div className="flex justify-end mt-6 pt-4 border-t border-gray-100">
        <button
          onClick={onNext}
          disabled={!selectedAddress}
          className="control control-prominent flex items-center gap-2 px-5 py-2.5 bg-blue-500 hover:bg-blue-600 active:scale-[0.98] disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-bold rounded-xl transition-[transform,opacity,background-color]"
        >
          Review Order <FaChevronRight size={11} />
        </button>
      </div>
    </div>
  </div>
);

export default AddressStep;