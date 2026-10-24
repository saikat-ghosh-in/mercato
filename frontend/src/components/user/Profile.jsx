import { LoadingLayout } from "../shared/LoadingStates";
import ActionArrow from "../shared/ActionArrow";
import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { FiBookmark, FiMapPin, FiPackage, FiShoppingBag, FiShield } from "react-icons/fi";
import api, { session } from "../../backend/api";
import { logOutUser } from "../../reduxStore/actions/authActions";
import { formatDate } from "../../utils/formatDate";
import ModalFrame from "../shared/ModalFrame";

const destinations = [["Your orders", "Track deliveries and view past orders.", "/orders", FiPackage], ["Saved items", "The things you’re keeping an eye on.", "/wishlist", FiBookmark], ["Your bag", "Pick up where you left off.", "/cart", FiShoppingBag], ["Addresses", "Manage delivery details and your default.", "/addresses", FiMapPin]];

export default function Profile() {
  const user = useSelector(s => s.auth.userDetails);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [deactivate, setDeactivate] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  if (!user) return <div className="site-container skeleton-page"><LoadingLayout variant="profile" label="Loading your account" /></div>;
  const name = [user.firstName, user.lastName].filter(Boolean).join(" ") || user.username;
  const admin = user.roles.includes("ROLE_ADMIN");
  const seller = user.roles.includes("ROLE_SELLER") || ["PENDING", "REJECTED"].includes(user.sellerStatus);
  const close = () => { if (!pending) { setDeactivate(false); setError(""); } };
  const confirmDeactivation = async () => {
    setPending(true); setError("");
    try { await api.patch("/account/deactivate"); await session.refresh({ broadcast: true }); navigate("/account-deactivated"); }
    catch (e) { setError(e?.response?.data?.message || "Couldn’t deactivate your account. Please try again."); }
    finally { setPending(false); }
  };
  return <div className="site-container account-hub">
    <header className="account-hero"><div><h1>Your account</h1><p>{name}</p></div><Link to="/edit-profile" className="button button-outline">Edit profile <ActionArrow /></Link></header>
    <nav className="account-destinations" aria-label="Your account shortcuts">{destinations.map(([title, description, href, Icon]) => <Link key={href} to={href}><Icon aria-hidden="true" /><h2>{title}</h2><p>{description}</p><ActionArrow className="account-card-arrow" aria-hidden="true" /></Link>)}</nav>
    <div className="account-details-grid"><section className="account-section"><div className="account-section-title"><h2>Account details</h2><span className="status-tag">{user.emailVerified ? "Email verified" : "Email unverified"}</span></div><dl className="account-facts">{[["Name", name], ["Username", user.username], ["Email", user.email], ["Phone", user.phoneNumber || "Not provided"], ["Member since", formatDate(user.createdAt)]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></section>
    <section className="account-section"><div className="account-section-title"><h2>Security & support</h2><FiShield aria-hidden="true" /></div><p>Manage your password and find help with an order.</p><div className="account-settings-links"><Link to="/forgot-password">Reset your password <ActionArrow /></Link>{admin && <Link to="/auth/mfa?replace=1">Replace authenticator <ActionArrow /></Link>}<Link to="/help">Visit marketplace Help <ActionArrow /></Link><button onClick={() => dispatch(logOutUser(navigate))}>Sign out <ActionArrow /></button></div></section></div>
    {(seller || admin) && <section className="account-business"><div><h2>{user.sellerDisplayName || "Mercato workspace"}</h2><p>{user.sellerStatus === "PENDING" ? "Your application is awaiting administrator verification. Selling tools open after approval." : user.sellerStatus === "REJECTED" ? "Your seller application was not approved. Open your dashboard for guidance." : "Your catalogue, operations and seller notices, together."}</p></div><div className="account-business-actions">{seller && <Link to="/seller/dashboard" className="button button-dark">Seller dashboard <ActionArrow /></Link>}{admin && <Link to="/admin/dashboard" className="button button-outline">Admin dashboard</Link>}<Link to="/seller-policies" className="text-link">Seller policies</Link></div></section>}
    {!admin && <details className="account-deactivate"><summary>Account deactivation</summary><p>Pause your account. Sign back in to restore access. Your account, orders and history are retained.</p><button className="button button-outline" onClick={() => setDeactivate(true)}>Deactivate account</button></details>}
    {deactivate && <ModalFrame title="Deactivate account" onClose={close}><div className="account-confirm"><h2>Take a break from Mercato?</h2><p>Your account will be paused now. Sign back in to reactivate it. Your account and order history will be retained.</p>{error && <p role="alert" className="inline-error">{error}</p>}<div className="checkout-inline-actions"><button className="button button-outline" disabled={pending} onClick={close}>Keep my account</button><button className="button button-dark" disabled={pending} onClick={confirmDeactivation}>{pending ? "Deactivating…" : "Deactivate"}</button></div></div></ModalFrame>}
  </div>;
}
