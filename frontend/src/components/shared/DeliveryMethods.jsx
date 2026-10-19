import { useDispatch, useSelector } from "react-redux";
import { selectDeliveryMethod } from "../../reduxStore/actions/cartActions";
import { formatCurrency } from "../../utils/formatCurrency";

export default function DeliveryMethods({ disabled = false }) {
  const dispatch = useDispatch();
  const { deliveryOptions, deliveryMethod, cartLoading } = useSelector(s => s.cart);
  return <fieldset className="delivery-methods" aria-busy={cartLoading}>
    <legend>Delivery method</legend>
    {deliveryOptions.length ? deliveryOptions.map(option => <label className="delivery-option" key={option.code} data-selected={deliveryMethod === option.code}>
      <input type="radio" name="delivery-method" value={option.code} checked={deliveryMethod === option.code} disabled={disabled || cartLoading} onChange={() => dispatch(selectDeliveryMethod(option.code))} />
      <span><strong>{option.name}</strong><span>{option.estimate}</span><small>{option.description}</small></span>
      <b>{Number(option.price) === 0 ? "Free" : formatCurrency(option.price)}</b>
    </label>) : <p role="status">Delivery options are being checked. Refresh your bag if they do not appear.</p>}
    <p className="delivery-caption">Prices are for the whole bag. Estimates start at dispatch; availability is confirmed before payment.</p>
  </fieldset>;
}
