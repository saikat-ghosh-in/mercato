import { Link, NavLink, useNavigate, useLocation } from "react-router-dom";
import { Dialog, DialogPanel, DialogTitle, Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import { FiSearch, FiShoppingBag, FiHeart, FiUser, FiMenu, FiX, FiArrowRight, FiLock } from "react-icons/fi";
import { useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { logOutUser } from "../reduxStore/actions/authActions";
import { isTokenExpired } from "../utils/tokenManager";
import { selectCartQty } from "../reduxStore/selectors/cartSelectors";
import Brand from "./Brand";

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
  const [searchOpen, setSearchOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const checkout = location.pathname.startsWith("/checkout");
  const links = [["My account", "/profile"], ["My orders", "/orders"], ["Saved items", "/wishlist"],
    ...(details?.roles?.includes("ROLE_ADMIN") ? [["Admin dashboard", "/admin/dashboard"]] : []),
    ...(details?.roles?.some(r => ["ROLE_SELLER", "ROLE_ADMIN"].includes(r)) ? [["Seller dashboard", "/seller/dashboard"]] : [])];
  const search = e => { e.preventDefault(); navigate("/products" + (keyword.trim() ? "?keyword=" + encodeURIComponent(keyword.trim()) : "")); setSearchOpen(false); setMobileOpen(false); };
  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <div className="utility-bar"><div className="site-container"><Link to="/help">Help</Link><span aria-hidden="true">|</span>{signedIn ? <Link to="/orders">Your orders</Link> : <Link to="/register">Join us</Link>}<span aria-hidden="true">|</span>{signedIn ? <button onClick={() => dispatch(logOutUser(navigate))}>Sign out</button> : <Link to="/login">Sign in</Link>}</div></div>
    <header className="site-header">
      <div className="site-container header-main"><Brand />
        {checkout ? <p className="checkout-lock"><FiLock /> Secure checkout</p> : <nav className="desktop-navigation" aria-label="Shop navigation">
          <NavLink to="/products">Shop</NavLink><Link to="/products?sortBy=createdAt&sortingOrder=desc">New &amp; Featured</Link>
          <Menu><MenuButton>Collections</MenuButton><MenuItems anchor="bottom" className="account-menu" transition>{categories.map(cat => <MenuItem key={cat.categoryId}><Link to={"/products?category=" + encodeURIComponent(cat.categoryName)}>{cat.categoryName}</Link></MenuItem>)}</MenuItems></Menu>
        </nav>}
        <div className="header-actions">
          {!checkout && <button className="icon-button" aria-label="Open search" aria-expanded={searchOpen} onClick={() => { setKeyword(new URLSearchParams(location.search).get("keyword") ?? ""); setSearchOpen(v => !v); }}><FiSearch /></button>}
          <Link to="/wishlist" className="icon-button saved-link" aria-label="Saved items"><FiHeart /></Link>
          {signedIn && <Menu><MenuButton className="icon-button" aria-label="Account menu"><FiUser /></MenuButton><MenuItems anchor="bottom end" className="account-menu" transition><p className="account-name">{details?.firstName || user.username || "Your account"}</p>{links.map(([label, href]) => <MenuItem key={href}><Link to={href}>{label}</Link></MenuItem>)}<MenuItem><button onClick={() => dispatch(logOutUser(navigate))}>Sign out</button></MenuItem></MenuItems></Menu>}
          <Link to="/cart" className="icon-button cart-link" aria-label={"Shopping bag, " + count + (count === 1 ? " item" : " items")}><FiShoppingBag />{count > 0 && <span className="cart-count" aria-hidden="true">{count > 99 ? "99+" : count}</span>}</Link>
          {!checkout && <button className="icon-button mobile-menu-button" onClick={() => setMobileOpen(true)} aria-label="Open navigation"><FiMenu /></button>}
        </div>
      </div>
      {searchOpen && <div className="search-panel site-container"><form className="site-search" role="search" onSubmit={search}><FiSearch aria-hidden="true" /><input autoFocus name="keyword" aria-label="Search products" placeholder="What are you looking for?" value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={150} type="search" onKeyDown={e => { if (e.key === "Escape") setSearchOpen(false); }} /><button aria-label="Submit search"><FiArrowRight /></button></form><button className="icon-button" aria-label="Close search" onClick={() => setSearchOpen(false)}><FiX /></button></div>}
    </header>
    <Dialog open={mobileOpen} onClose={setMobileOpen} className="mobile-dialog"><div className="dialog-backdrop" aria-hidden="true" /><DialogPanel transition className="mobile-panel"><div className="section-heading"><DialogTitle>Explore Mercato</DialogTitle><button className="icon-button" onClick={() => setMobileOpen(false)} aria-label="Close navigation"><FiX /></button></div><nav aria-label="Mobile navigation" onClick={() => setMobileOpen(false)}><Link to="/">Home</Link><Link to="/products">Shop all</Link>{categories.map(c => <Link key={c.categoryId} to={"/products?category=" + encodeURIComponent(c.categoryName)}>{c.categoryName}</Link>)}{(signedIn ? links : [["Sign in", "/login"], ["Create an account", "/register"]]).map(([label, href]) => <Link key={href} to={href}>{label}</Link>)}<Link to="/help">Shopping help</Link></nav></DialogPanel></Dialog>
  </>;
}
