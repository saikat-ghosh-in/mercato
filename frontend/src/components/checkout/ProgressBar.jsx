export default function ProgressBar({ step, setStep }) {
  return <nav className="checkout-progress" aria-label="Checkout progress">{["Delivery", "Review", "Payment"].map((label, index) => <div className="flex items-center gap-4" key={label}><button aria-current={step === index + 1 ? "step" : undefined} disabled={index + 1 > step} onClick={() => { if (index + 1 < step) setStep(index + 1); }}><span>{index + 1}</span>{label}</button>{index < 2 && <i aria-hidden="true" />}</div>)}</nav>;
}
