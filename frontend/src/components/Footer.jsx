import Brand from "./Brand";
import { Link } from "react-router-dom";

export default function Footer() {
  return <footer className="site-footer"><div className="site-container">
    <div className="footer-grid"><div><Brand /><p>Good things for the spaces you love<br />and the life you live.</p></div>
      <div><h3>Make a discovery</h3><nav aria-label="Footer shopping"><Link to="/products">Shop all</Link><Link to="/products?sortBy=createdAt&sortingOrder=desc">New arrivals</Link><Link to="/products?sortBy=discountPercent&sortingOrder=desc">The good finds</Link><Link to="/wishlist">Saved items</Link></nav></div>
      <div><h3>Here to help</h3><nav aria-label="Footer account"><Link to="/profile">Your account</Link><Link to="/orders">Your orders</Link><Link to="/cart">Shopping bag</Link><Link to="/help">Shopping help</Link><Link to="/seller-policies">Selling on Mercato</Link></nav></div>
    </div><div className="footer-bottom"><span>© {new Date().getFullYear()} Mercato. Made for your everyday.</span></div>
  </div></footer>;
}
