import { Link, NavLink, useNavigate, useLocation } from "react-router-dom";
import { Dialog, DialogPanel, DialogTitle, Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import { FiSearch, FiShoppingBag, FiHeart, FiUser, FiMenu, FiX, FiArrowUpRight, FiTruck } from "react-icons/fi";
import { useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { logOutUser } from "../reduxStore/actions/authActions";
import { isTokenExpired } from "../utils/tokenManager";
import { selectCartQty } from "../reduxStore/selectors/cartSelectors";

export default function Header() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const location = useLocation();
  const user = useSelector(s => s.auth?.user);
  const details = useSelector(s => s.auth?.userDetails);
  const categories = useSelector(s => s.categories.categories) ?? [];
  const count = useSelector(selectCartQty);
  const signedIn = Boolean(user?.token && !isTokenExpired(user.tokenExpirationTime));
  const [mobileOpen, setMobileOpen] = useState(false);
  const links = [
    ["My account", "/profile"], ["My orders", "/orders"], ["Saved items", "/wishlist"],
    ...(details?.roles?.includes("ROLE_ADMIN") ? [["Admin dashboard", "/admin/dashboard"]] : []),
    ...(details?.roles?.some(r => ["ROLE_SELLER", "ROLE_ADMIN"].includes(r)) ? [["Seller dashboard", "/seller/dashboard"]] : []),
  ];
  const search = e => {
    e.preventDefault();
    const keyword = new FormData(e.currentTarget).get("keyword").trim();
    navigate("/products" + (keyword ? "?keyword=" + encodeURIComponent(keyword) : ""));
    setMobileOpen(false);
  };
  const searchForm = <form className="site-search" role="search" onSubmit={search} key={location.search}>
    <FiSearch aria-hidden="true" />
    <input name="keyword" aria-label="Search products" placeholder="Search for something good" defaultValue={new URLSearchParams(location.search).get("keyword") ?? ""} maxLength={150} type="search" />
    <button aria-label="Submit search" type="submit"><FiArrowUpRight /></button>
  </form>;
  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <div className="announcement"><span>A little more considered. A little more you.</span><span><FiTruck /> Free shipping on orders above ₹{import.meta.env.VITE_FREE_SHIPPING_ABOVE || 999}</span></div>
    <header className="site-header">
      <div className="site-container header-main">
        <Link to="/" className="wordmark" aria-label="Mercato home"><span className="brand-symbol">m</span>mercato<span className="brand-dot">.</span></Link>
        <div className="desktop-search">{searchForm}</div>
        <div className="header-actions">
          <Link to="/wishlist" className="icon-button saved-link" aria-label="Saved items"><FiHeart /></Link>
          {signedIn ? <Menu><MenuButton className="icon-button" aria-label="Account menu"><FiUser /></MenuButton>
            <MenuItems anchor="bottom end" className="account-menu" modal>
              <p className="account-name">{details?.firstName || user.username || "Your account"}</p>
              {links.map(([label, href]) => <MenuItem key={href}><Link to={href}>{label}</Link></MenuItem>)}
              <MenuItem><button onClick={() => dispatch(logOutUser(navigate))}>Sign out</button></MenuItem>
            </MenuItems></Menu> : <Link className="account-link" to="/login"><FiUser /><span>Sign in</span></Link>}
          <Link to="/cart" className="icon-button cart-link" aria-label={"Shopping bag, " + count + (count === 1 ? " item" : " items")}><FiShoppingBag /><span className="cart-count">{count > 99 ? "99+" : count}</span></Link>
          <button className="icon-button mobile-menu-button" onClick={() => setMobileOpen(true)} aria-label="Open navigation"><FiMenu /></button>
        </div>
      </div>
      <nav className="site-container category-nav" aria-label="Shop navigation">
        <NavLink to="/products" end>Shop all</NavLink>
        {categories.slice(0, 5).map(cat => <Link key={cat.categoryId} to={"/products?category=" + encodeURIComponent(cat.categoryName)}>{cat.categoryName}</Link>)}
        <Link to="/products?sortBy=createdAt&sortingOrder=desc">New arrivals <span className="new-dot" /></Link>
        <Link className="nav-deals" to="/products?sortBy=discountPercent&sortingOrder=desc">The good finds <FiArrowUpRight /></Link>
      </nav>
      <div className="mobile-search site-container">{searchForm}</div>
    </header>
    <Dialog open={mobileOpen} onClose={setMobileOpen} className="mobile-dialog">
      <div className="dialog-backdrop" aria-hidden="true" />
      <DialogPanel className="mobile-panel">
        <div className="section-heading"><DialogTitle>Explore Mercato</DialogTitle><button className="icon-button" onClick={() => setMobileOpen(false)} aria-label="Close navigation"><FiX /></button></div>
        <nav aria-label="Mobile navigation" onClick={() => setMobileOpen(false)}>
          <Link to="/">Home</Link><Link to="/products">Shop all</Link>
          {categories.map(c => <Link key={c.categoryId} to={"/products?category=" + encodeURIComponent(c.categoryName)}>{c.categoryName}</Link>)}
          {(signedIn ? links : [["Sign in", "/login"], ["Create an account", "/register"]]).map(([label, href]) => <Link key={href} to={href}>{label}</Link>)}
          <Link to="/help">Shopping help</Link>
        </nav>
      </DialogPanel>
    </Dialog>
  </>;
}
