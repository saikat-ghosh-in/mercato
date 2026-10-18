import { FiCheck } from "react-icons/fi";
export default function ProgressBar({ step, setStep, disabled }) {
  return <nav className="checkout-steps" aria-label="Checkout progress"><ol>{["Delivery", "Review", "Payment"].map((label, index) => {
    const number = index + 1;
    return <li key={label} data-complete={number < step}><button type="button" aria-current={step === number ? "step" : undefined} disabled={disabled || number >= step} onClick={() => setStep(number)}><span className="checkout-step-number">{number < step ? <FiCheck aria-hidden="true" /> : number}</span><span>{label}</span></button></li>;
  })}</ol></nav>;
}
