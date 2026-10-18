import { FiMinus, FiPlus, FiTrash2 } from "react-icons/fi";
export default function QuantityStepper({ quantity, onIncrease, onDecrease, canAddMore = true, pendingSync = false }) {
  return <div className="quantity-control w-fit" aria-busy={pendingSync}><button className="icon-action" onClick={onDecrease} aria-label={quantity === 1 ? "Remove item" : "Decrease quantity"} disabled={pendingSync}>{quantity === 1 ? <FiTrash2 /> : <FiMinus />}</button><span aria-live="polite">{quantity}</span><button className="icon-action" onClick={onIncrease} aria-label="Increase quantity" disabled={!canAddMore || pendingSync}><FiPlus /></button></div>;
}
