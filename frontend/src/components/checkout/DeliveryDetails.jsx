export default function DeliveryDetails({ address }) {
  return address && <address className="checkout-delivery-details"><strong>{address.recipientName}</strong><span>{address.addressLine1}{address.addressLine2 && `, ${address.addressLine2}`}</span><span>{address.city}, {address.state} {address.pincode}, {address.country}</span><span>{address.recipientPhone}</span></address>;
}
