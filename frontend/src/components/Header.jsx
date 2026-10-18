import { Link, useNavigate, useLocation } from "react-router-dom";
import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import { FiSearch, FiShoppingBag, FiHeart, FiUser, FiGrid, FiX, FiArrowRight, FiLock, FiHelpCircle, FiChevronDown } from "react-icons/fi";
import { useEffect, useRef, useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { logOutUser } from "../reduxStore/actions/authActions";
import { isTokenExpired } from "../utils/tokenManager";
import { selectCartQty } from "../reduxStore/selectors/cartSelectors";
import Brand from "./Brand";
import ExplorePanel from "./ExplorePanel";
import { useMeasuredHeight } from "../hooks/useMeasuredHeight";
import { useInputModality } from "../hooks/useInputModality";

export default function Header() {
  useInputModality();
  const headerRef = useMeasuredHeight("--site-header-height");
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const location = useLocation();
  const user = useSelector(s => s.auth?.user);
  const details = useSelector(s => s.auth?.userDetails);
  const categories = useSelector(s => s.categories.categories) ?? [];
  const count = useSelector(selectCartQty);
  const signedIn = Boolean(user?.token && !isTokenExpired(user.tokenExpirationTime));
  const [exploreOpen, setExploreOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const searchInputRef = useRef(null);
  const searchTriggerRef = useRef(null);
  useEffect(() => { if (searchOpen) searchInputRef.current?.focus({ preventScroll: true }); }, [searchOpen]);
  const checkout = location.pathname.startsWith("/checkout");
  const links = [["My account", "/profile"], ["Your orders", "/orders"], ["Saved items", "/wishlist"],
    ...(details?.roles?.includes("ROLE_ADMIN") ? [["Admin dashboard", "/admin/dashboard"]] : []),
    ...(details?.roles?.some(r => ["ROLE_SELLER", "ROLE_ADMIN"].includes(r)) ? [["Seller dashboard", "/seller/dashboard"]] : [])];
  const search = event => {
    event.preventDefault();
    navigate("/products" + (keyword.trim() ? "?keyword=" + encodeURIComponent(keyword.trim()) : ""));
    setSearchOpen(false);
    setExploreOpen(false);
  };
  const closeSearch = () => {
    setSearchOpen(false);
    searchTriggerRef.current?.focus({ preventScroll: true });
  };
  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header ref={headerRef} className="site-header">
      <div className="site-container header-main">
        <Brand />
        {checkout ? <p className="checkout-lock" aria-label="Secure checkout"><FiLock /><span>Secure checkout</span></p> :
          <button className="header-explore" aria-label="Explore marketplace" aria-expanded={exploreOpen} aria-haspopup="dialog" onClick={() => {
            setKeyword(new URLSearchParams(location.search).get("keyword") ?? "");
            setSearchOpen(false);
            setExploreOpen(true);
          }}><FiGrid aria-hidden="true" /><span>Explore</span></button>}
        <div className="header-actions">
          {!checkout && <button ref={searchTriggerRef} className="icon-button" aria-label="Open search" aria-expanded={searchOpen} onClick={() => {
            setKeyword(new URLSearchParams(location.search).get("keyword") ?? "");
            setSearchOpen(value => !value);
          }}><FiSearch /></button>}
          <Link to="/wishlist" className="icon-button saved-link" aria-label="Saved items"><FiHeart /></Link>
          <Link to="/help" className="header-help" aria-label="Help"><FiHelpCircle /><span>Help</span></Link>
          <Menu>
            <MenuButton className="header-account" aria-label="Account menu"><FiUser /><span>Account</span><FiChevronDown className="account-chevron" aria-hidden="true" /></MenuButton>
            <MenuItems anchor="bottom end" className="account-menu" transition>
              {signedIn ? <>
                <p className="account-name">{details?.firstName || user.username || "Your account"}</p>
                {links.map(([label, href]) => <MenuItem key={href}><Link to={href}>{label}</Link></MenuItem>)}
                <MenuItem><button onClick={() => dispatch(logOutUser(navigate))}>Sign out</button></MenuItem>
              </> : <><MenuItem><Link to="/login">Sign in</Link></MenuItem><MenuItem><Link to="/register">Create an account</Link></MenuItem></>}
            </MenuItems>
          </Menu>
          <Link to="/cart" className="icon-button cart-link" aria-label={"Shopping bag, " + count + (count === 1 ? " item" : " items")}><FiShoppingBag />{count > 0 && <span className="cart-count" aria-hidden="true">{count > 99 ? "99+" : count}</span>}</Link>
        </div>
      </div>
      {searchOpen && <div className="search-panel site-container">
        <form className="site-search" role="search" onSubmit={search}>
          <FiSearch aria-hidden="true" />
          <input ref={searchInputRef} name="keyword" aria-label="Search products" placeholder="What are you looking for?" value={keyword} onChange={event => setKeyword(event.target.value)} maxLength={150} type="search" enterKeyHint="search" onKeyDown={event => { if (event.key === "Escape") closeSearch(); }} />
          <button className="icon-action" aria-label="Submit search"><FiArrowRight /></button>
        </form>
        <button className="icon-button" aria-label="Close search" onClick={closeSearch}><FiX /></button>
      </div>}
    </header>
    <ExplorePanel open={exploreOpen} onClose={() => setExploreOpen(false)} categories={categories} keyword={keyword} onKeywordChange={setKeyword} onSearch={search} />
  </>;
}
