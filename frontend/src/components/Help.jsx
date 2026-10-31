import Accordion from "./shared/Accordion";
import { CancellationPolicy } from "./orders/WorkflowControls";
import { paymentPolicy } from "../utils/fulfillment";
import { Link } from "react-router-dom";

export default function Help() {
  return <div className="site-container help-page"><h1>Shopping help</h1>
    <Accordion multiple defaultValue="track"><Accordion.Item value="track"><Accordion.Trigger>How do I track my order?</Accordion.Trigger><Accordion.Content><p>Sign in and open <Link className="underline" to="/orders">My orders</Link> to view payment, shipment and fulfillment updates for each item.</p></Accordion.Content></Accordion.Item>
    <Accordion.Item value="payment"><Accordion.Trigger>What happens if payment is interrupted?</Accordion.Trigger><Accordion.Content><p>Your bag stays intact until payment is confirmed. Return to your orders to check the payment status before trying again. If your bank shows a debit, allow the confirmation page to refresh the status.</p><p>{paymentPolicy}</p></Accordion.Content></Accordion.Item>
    <Accordion.Item value="fees"><Accordion.Trigger>How are delivery and fees calculated?</Accordion.Trigger><Accordion.Content><p>Product prices include applicable taxes. Your bag and checkout show delivery, platform and processing charges before payment. Confirm the delivery charge shown for your bag before placing an order.</p></Accordion.Content></Accordion.Item>
    <Accordion.Item value="stock"><Accordion.Trigger>Why did an item become unavailable?</Accordion.Trigger><Accordion.Content><p>Stock is shared across shoppers. Items in your bag are reserved for a limited time; availability is checked again at checkout. Update your bag if a reservation expires.</p></Accordion.Content></Accordion.Item>
    <Accordion.Item value="saved"><Accordion.Trigger>Can I save something for later?</Accordion.Trigger><Accordion.Content><p>Sign in and use the heart on any product to keep it in <Link className="underline" to="/wishlist">Saved items</Link>.</p></Accordion.Content></Accordion.Item>
    <Accordion.Item value="addresses"><Accordion.Trigger>Where can I manage delivery addresses?</Accordion.Trigger><Accordion.Content><p>You can add and edit delivery addresses from <Link className="underline" to="/addresses">Your addresses</Link> or during checkout.</p></Accordion.Content></Accordion.Item>
  </Accordion><CancellationPolicy /></div>;
}
