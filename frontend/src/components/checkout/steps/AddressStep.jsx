import { Link } from "react-router-dom";
import { FiArrowLeft, FiArrowRight, FiMapPin, FiPlus } from "react-icons/fi";
import AddressList from "../../address/AddressList";
export default function AddressStep({ addresses, selectedAddress, loadingAddresses, addressError, onRetry, onAddAddress, onEditAddress, onDeleteAddress, onNext }) {
  return <>
    <div className="checkout-section-heading"><div><p className="eyebrow">STEP 01 · DELIVERY</p><h2 id="checkout-step-heading" tabIndex={-1}>Where should we send it?</h2><p>Choose a saved address or add somewhere new.</p></div><button type="button" className="button button-outline" onClick={onAddAddress}><FiPlus /> Add address</button></div>
    {addressError && <div className="inline-error" role="alert">We couldn’t refresh your addresses. <button onClick={onRetry}>Try again</button></div>}
    {loadingAddresses && !addresses?.length ? <div className="checkout-address-skeleton" role="status" aria-label="Loading addresses" /> : addresses?.length ? <AddressList addresses={addresses} selectedAddress={selectedAddress} onEditAddress={onEditAddress} onDeleteAddress={onDeleteAddress} /> : !addressError && <div className="checkout-empty"><FiMapPin aria-hidden="true" /><h3>A place for your next good find.</h3><p>Add your delivery address to get started.</p><button className="button button-dark" onClick={onAddAddress}>Add your first address</button></div>}
    <div className="checkout-step-actions"><Link className="button checkout-back" to="/cart"><FiArrowLeft /> Back to bag</Link><button className="button button-dark button-prominent" onClick={onNext} disabled={!selectedAddress || loadingAddresses || addressError}>Review order <FiArrowRight /></button></div>
  </>;
}
