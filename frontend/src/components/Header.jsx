import { Link, useNavigate, useLocation } from "react-router-dom";
import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import { FiSearch, FiShoppingBag, FiUser, FiLock, FiHelpCircle, FiChevronDown, FiCheck } from "react-icons/fi";
import { useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { logOutUser } from "../reduxStore/actions/authActions";
import { selectCartQty } from "../reduxStore/selectors/cartSelectors";
import Brand from "./Brand";
import ExplorePanel from "./ExplorePanel";
import { useMeasuredHeight } from "../hooks/useMeasuredHeight";
import { useInputModality } from "../hooks/useInputModality";
import { isSellerAccount } from "../utils/accountRouting";
import UserAvatar from "./shared/UserAvatar";

export default function Header() {
  useInputModality();
  const headerRef = useMeasuredHeight("--site-header-height");
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const location = useLocation();
  const user = useSelector(s => s.auth?.user);
  const details = useSelector(s => s.auth?.userDetails);
  const sessionStatus = useSelector(s => s.auth?.sessionStatus);
  const categories = useSelector(s => s.categories.categories) ?? [];
  const count = useSelector(selectCartQty);
  const signedIn = Boolean(user);
  const displayName=details?.firstName?.trim() || user?.firstName?.trim() || user?.username || details?.username || "Your account";
  const initializing=!signedIn && ["idle","loading"].includes(sessionStatus);
  const accountDescription=signedIn?`Signed in as ${displayName}`:initializing?"Checking your account":"Sign in or create an account";
  const [discoveryOpen, setDiscoveryOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const checkout = location.pathname.startsWith("/checkout");
  const links = [["My account", "/profile"], ["Your orders", "/orders"], ["Saved items", "/wishlist"],
    ...(details?.roles?.includes("ROLE_ADMIN") ? [["Admin dashboard", "/admin/dashboard"]] : []),
    ...(isSellerAccount(details) || details?.roles?.includes("ROLE_ADMIN") ? [["Seller dashboard", "/seller/dashboard"]] : [])];
  const search = event => {
    event.preventDefault();
    navigate("/products" + (keyword.trim() ? "?keyword=" + encodeURIComponent(keyword.trim()) : ""));
    setDiscoveryOpen(false);
  };
  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header ref={headerRef} className="site-header">
      <div className="site-container header-main">
        <Brand />
        {checkout && <p className="checkout-lock" aria-label="Secure checkout"><FiLock /><span>Secure checkout</span></p>}
        <div className="header-actions">
          <Link to="/products" className="header-catalog" aria-label="All products" title="All products"><span>All products</span></Link>
          <button className="icon-button" aria-label="Open search" title="Search and discover" aria-expanded={discoveryOpen} aria-haspopup="dialog" onClick={() => {
            setKeyword(new URLSearchParams(location.search).get("keyword") ?? "");
            setDiscoveryOpen(true);
          }}><FiSearch /></button>
          <Link to="/help" className="header-help" aria-label="Help"><FiHelpCircle /><span>Help</span></Link>
          <Menu>
            <MenuButton className="header-account" aria-label="Account menu" aria-description={accountDescription} title={accountDescription} data-signed-in={signedIn}>
              {signedIn?<span className="account-avatar" aria-hidden="true"><UserAvatar name={displayName} imageUrl={details?.profileImageUrl ?? user?.profileImageUrl}/><FiCheck className="account-signed-check"/></span>:<FiUser aria-hidden="true"/>}
              <span className="account-label">{signedIn?`Hi, ${displayName}`:initializing?"Account":"Sign in"}</span><FiChevronDown className="account-chevron" aria-hidden="true" />
            </MenuButton>
            <MenuItems anchor="bottom end" className="account-menu" transition>
              {signedIn ? <>
                <p className="account-name"><span className="account-signed-label">Signed in as</span><strong>{displayName}</strong></p>
                {links.map(([label, href]) => <MenuItem key={href}><Link to={href}>{label}</Link></MenuItem>)}
                <MenuItem><button onClick={() => dispatch(logOutUser(navigate))}>Sign out</button></MenuItem>
              </> : <><MenuItem><Link to="/login">Sign in</Link></MenuItem><MenuItem><Link to="/register">Create an account</Link></MenuItem></>}
            </MenuItems>
          </Menu>
          <Link to="/cart" className="icon-button cart-link" aria-label={"Shopping bag, " + count + (count === 1 ? " item" : " items")}><FiShoppingBag />{count > 0 && <span className="cart-count" aria-hidden="true">{count > 99 ? "99+" : count}</span>}</Link>
        </div>
      </div>
    </header>
    <ExplorePanel open={discoveryOpen} onClose={() => setDiscoveryOpen(false)} categories={categories} keyword={keyword} onKeywordChange={setKeyword} onSearch={search} />
  </>;
}
