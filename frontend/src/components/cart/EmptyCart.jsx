import { Link } from "react-router-dom";
import ActionArrow from "../shared/ActionArrow";
export default function EmptyCart() {
 return <div className="site-container state-panel"><h1>Your bag is empty</h1><p>Browse the marketplace to find your next addition.</p><Link to="/products" className="button button-dark">Browse products <ActionArrow /></Link></div>;
}
