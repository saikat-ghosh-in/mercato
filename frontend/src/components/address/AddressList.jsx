import { useDispatch } from "react-redux";
import { FaCheckCircle, FaEdit, FaTrash } from "react-icons/fa";
import { MdLocationCity, MdPinDrop, MdPublic, MdPhone } from "react-icons/md";
import { setSelectedAddressForCheckout } from "../../reduxStore/actions/addressActions";

const AddressList = ({ addresses, selectedAddress, onEditAddress, onDeleteAddress }) => {
    const dispatch = useDispatch();

    return (
        <div className="space-y-3">
            {addresses.map((address) => {
                const isSelected = selectedAddress?.addressId === address.addressId;
                return (
                    <div
                        key={address.addressId}
                        onClick={() => dispatch(setSelectedAddressForCheckout(address))}
                        className={`relative p-4 rounded-xl border-2 cursor-pointer transition-colors duration-150
                            ${isSelected
                                ? "border-blue-500 bg-blue-50"
                                : "border-gray-100 bg-gray-50 hover:border-gray-200"
                            }`}
                    >
                        <div className="flex items-start justify-between gap-3">
                            <div className="flex-1 min-w-0 space-y-1.5">

                                {/* Name + checkmark */}
                                <div className="flex items-center gap-2">
                                    <input type="radio" name="shipping-address" checked={isSelected}
                                        aria-label={`Deliver to ${address.recipientName}, ${address.addressLine1}`}
                                        onChange={() => dispatch(setSelectedAddressForCheckout(address))} />
                                    <p className="text-sm font-bold text-gray-900">{address.recipientName}</p>
                                    {isSelected && (
                                        <FaCheckCircle size={14} className="text-blue-500 shrink-0" />
                                    )}
                                </div>

                                {/* Phone */}
                                <div className="flex items-center gap-1.5 text-xs text-gray-500">
                                    <MdPhone size={13} className="shrink-0" />
                                    <span>{address.recipientPhone}</span>
                                </div>

                                {/* Address lines */}
                                <div className="flex items-start gap-1.5 text-xs text-gray-500">
                                    <span className="shrink-0 mt-px">📍</span>
                                    <span>
                                        {address.addressLine1}
                                        {address.addressLine2 && `, ${address.addressLine2}`}
                                    </span>
                                </div>

                                {/* City + State */}
                                <div className="flex items-center gap-1.5 text-xs text-gray-500">
                                    <MdLocationCity size={13} className="shrink-0" />
                                    <span>{address.city}, {address.state}</span>
                                </div>

                                {/* Pincode + Country */}
                                <div className="flex items-center gap-3 text-xs text-gray-500">
                                    <span className="flex items-center gap-1.5">
                                        <MdPinDrop size={13} className="shrink-0" />
                                        {address.pincode}
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <MdPublic size={13} className="shrink-0" />
                                        {address.country}
                                    </span>
                                </div>
                            </div>

                            {/* Actions */}
                            <div className="flex items-center gap-1 shrink-0">
                                <button
                                    aria-label={`Edit address for ${address.recipientName}`}
                                    onClick={(e) => { e.stopPropagation(); onEditAddress(address); }}
                                    className="p-2 rounded-lg text-gray-400 hover:text-blue-500 hover:bg-blue-50 transition-colors"
                                >
                                    <FaEdit size={13} />
                                </button>
                                <button
                                    aria-label={`Delete address for ${address.recipientName}`}
                                    onClick={(e) => { e.stopPropagation(); onDeleteAddress(address); }}
                                    className="p-2 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
                                >
                                    <FaTrash size={13} />
                                </button>
                            </div>
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

export default AddressList;
