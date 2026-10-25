import { Spinner } from "../shared/Spinner";
import ActionArrow from "../shared/ActionArrow";

export default function PaymentAction({ onSubmit, loading = false, disabled = false, describedBy }) {
  return <button type="button" className="button button-dark button-prominent checkout-pay" onClick={onSubmit} disabled={loading || disabled} aria-busy={loading} aria-describedby={describedBy}>
    <span>Continue to payment</span>
    <span className="checkout-action-icon" aria-hidden="true">{loading ? <Spinner size={16} /> : <ActionArrow />}</span>
  </button>;
}
