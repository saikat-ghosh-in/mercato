import { useEffect, useState } from "react";
import { useDispatch } from "react-redux";
import { Link } from "react-router-dom";
import api from "../../backend/api";
import { fetchUserDetails } from "../../reduxStore/actions/authActions";

export default function SellerApplicationStatus({ user }) {
  const [details, setDetails] = useState(null); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const dispatch = useDispatch();
  useEffect(() => { const controller = new AbortController(); api.get("/account/seller-application", { signal: controller.signal }).then(({ data }) => setDetails(data)).catch(() => { if (!controller.signal.aborted) setError("Application details couldn’t load. Please refresh."); }); return () => controller.abort(); }, []);
  return <section className="site-container seller-application-status"><p className="eyebrow">YOUR SELLER DASHBOARD</p><h1>{user.sellerStatus === "REJECTED" ? "Your application needs attention." : "Your store’s next chapter is on its way."}</h1><p>{user.sellerDisplayName}: {user.sellerStatus === "REJECTED" ? "The team could not approve this application. Review the feedback and contact Help." : "Your business details are awaiting administrator verification. Catalogue, inventory and fulfillment tools open after approval."}</p><ol><li>Email verification</li><li>Business, PAN and tax review</li><li>Pickup and category review</li><li>Selling access enabled after approval</li></ol>{details?.reviewNote && <div className="policy-draft"><h2>Review feedback</h2><p>{details.reviewNote}</p></div>}{error && <p className="inline-error" role="alert">{error}</p>}<div className="checkout-inline-actions"><button className="button button-dark" disabled={busy} onClick={async () => { setBusy(true); try { await dispatch(fetchUserDetails()); } finally { setBusy(false); } }}>{busy ? "Checking…" : "Check approval status"}</button><Link to="/seller-policies" className="button button-outline">Seller policies</Link><Link to="/help" className="text-link">Contact Help</Link><Link to="/profile" className="text-link">Your account</Link></div></section>;
}
