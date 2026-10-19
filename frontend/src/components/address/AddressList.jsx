import { useDispatch } from "react-redux";
import { useState } from "react";
import { FiEdit2, FiTrash2 } from "react-icons/fi";
import { setSelectedAddressForCheckout, makeAddressDefault } from "../../reduxStore/actions/addressActions";
export default function AddressList({ addresses, selectedAddress, onEditAddress, onDeleteAddress }) {
  const dispatch = useDispatch();
  const [defaulting, setDefaulting] = useState(false);
  return <fieldset className="checkout-addresses"><legend className="sr-only">Choose a delivery address</legend>{addresses.map(address => <div className="checkout-address" key={address.addressId} data-selected={selectedAddress?.addressId === address.addressId}>
    <label><input type="radio" name="shipping-address" checked={selectedAddress?.addressId === address.addressId} onChange={() => dispatch(setSelectedAddressForCheckout(address))} /><span><strong>{address.recipientName}</strong><span>{address.addressLine1}{address.addressLine2 && `, ${address.addressLine2}`}</span><span>{address.city}, {address.state} {address.pincode}</span><span>{address.country}</span><span className="checkout-address-phone">{address.recipientPhone}</span></span></label>
    <div className="checkout-address-actions">{address.defaultAddress ? <span className="status-tag">Default</span> : <button type="button" className="button checkout-edit" disabled={defaulting} onClick={async () => { setDefaulting(true); try { await dispatch(makeAddressDefault(address.addressId)); } finally { setDefaulting(false); } }}>Make default</button>}<button type="button" className="icon-action" aria-label={`Edit address for ${address.recipientName}`} onClick={() => onEditAddress(address)}><FiEdit2 /></button><button type="button" className="icon-action" aria-label={`Delete address for ${address.recipientName}`} onClick={() => onDeleteAddress(address)}><FiTrash2 /></button></div>
  </div>)}</fieldset>;
}
