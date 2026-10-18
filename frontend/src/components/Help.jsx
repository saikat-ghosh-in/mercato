import { Link } from "react-router-dom";

export default function Help() {
  return <div className="site-container help-page"><p className="eyebrow">HERE TO HELP</p><h1>A few helpful things.</h1>
    <details open><summary>How do I track my order?</summary><p>Sign in and open <Link className="underline" to="/orders">Your orders</Link> to view payment, shipment and fulfillment updates for each item.</p></details>
    <details><summary>What happens if payment is interrupted?</summary><p>Your bag stays intact until payment is confirmed. Return to your orders to check the payment status before trying again. If your bank shows a debit, allow the confirmation page to refresh the status.</p></details>
    <details><summary>How are delivery and fees calculated?</summary><p>Product prices include applicable taxes. Your bag and checkout show delivery, platform and processing charges before payment. Confirm the delivery charge shown for your bag before placing an order.</p></details>
    <details><summary>Why did an item become unavailable?</summary><p>Stock is shared across shoppers. Items in your bag are reserved for a limited time; availability is checked again at checkout. Update your bag if a reservation expires.</p></details>
    <details><summary>Can I save something for later?</summary><p>Sign in and use the heart on any product to keep it in <Link className="underline" to="/wishlist">Saved items</Link>.</p></details>
    <details><summary>Where can I manage delivery addresses?</summary><p>You can add and edit delivery addresses from <Link className="underline" to="/addresses">Your addresses</Link> or during checkout.</p></details>
  </div>;
}
