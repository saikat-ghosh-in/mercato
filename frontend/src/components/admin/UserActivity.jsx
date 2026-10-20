import { useEffect, useState } from "react";
import api from "../../backend/api";
import SelectControl from "../shared/SelectControl";
import { formatCurrency as money } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";

const statusLabel = value => ({ PENDING: "Open", APPROVED: "Approved", REJECTED: "Rejected", SUPERSEDED: "Superseded", WITHDRAWN: "Withdrawn" }[value] || value?.replaceAll("_", " "));

export default function UserActivity({ user }) {
  const [section, setSection] = useState("overview");
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("ALL");
  const [cache, setCache] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [retry, setRetry] = useState(0);
  const key = `${section}:${page}:${status}`;
  useEffect(() => {
    const controller = new AbortController();
    api.get(`/admin/users/${user.userId}/activity`, { params: { section, page, status }, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) setCache(previous => ({ ...previous, [key]: data })); })
      .catch(err => { if (!controller.signal.aborted) setError({ key, message: err.response?.data?.message || "Activity couldn’t load. Try again." }); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [user.userId, user.accessVersion, section, page, status, key, retry]);
  const data = cache[key];
  const summary = data || Object.values(cache).at(-1);
  const tabs = [["overview", "Active bag", summary?.cart?.units ?? 0], ["orders", "Orders", summary?.orderCount],
    ...(summary?.sellerAccount ? [["products", "Products", summary.productCount], ["requests", "Listing requests", summary.requestCount]] : [])];
  const begin = () => { setLoading(true); setError(null); };
  const select = next => { if (next === section && page === 0 && status === "ALL") return; begin(); setSection(next); setPage(0); setStatus("ALL"); };
  return <section className="user-activity" aria-label="User activity">
    <h3>Account activity</h3>
    <nav className="user-activity-tabs" aria-label="User activity sections">{tabs.map(([id, label, count]) => <button key={id} type="button" aria-pressed={section === id} onClick={() => select(id)}>{label}{count != null && <span>{count}</span>}</button>)}</nav>
    {section === "requests" && <div className="user-activity-filter"><SelectControl aria-label="Filter listing requests" value={status} onChange={event => { if (event.target.value === status) return; begin(); setStatus(event.target.value); setPage(0); }}>{[["ALL", "All requests"], ["PENDING", "Open"], ["APPROVED", "Approved"], ["REJECTED", "Rejected"], ["SUPERSEDED", "Superseded"], ["WITHDRAWN", "Withdrawn"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectControl><span>{summary?.openRequestCount ?? 0} open</span></div>}
    <p className="user-activity-status" role="status">{loading ? "Loading activity…" : ""}</p>
    {error?.key === key && <p className="inline-error" role="alert">{error.message}<button type="button" onClick={() => { begin(); setRetry(value => value + 1); }}>Retry</button></p>}
    {data && <>
      {section === "overview" && (data.cart ? <><div className="user-cart-summary"><strong>{data.cart.units} {data.cart.units === 1 ? "item" : "items"} · {money(data.cart.total)}</strong><span>{data.cart.cartId}</span><small>Updated {formatDate(data.cart.updatedAt)} · {statusLabel(data.cart.deliveryMethod)} delivery</small><dl><div><dt>Items</dt><dd>{money(data.cart.subtotal)}</dd></div><div><dt>Saved charges</dt><dd>{money(data.cart.charges)}</dd></div></dl><p>Saved bag snapshot. Prices, stock and delivery are checked again at checkout.</p></div></> : <p className="user-activity-empty">No active bag.</p>)}
      <ul className="user-activity-list">{data.results.content.map(row => <li key={row.id || row.orderId || row.productId}>
        {section === "overview" && <><div><strong>{row.productName}</strong><span>{row.productId}</span><small>{row.quantity} × {money(row.unitPrice)}{!row.available && " · Unavailable at this quantity"}</small></div><b>{money(row.lineTotal)}</b></>}
        {section === "orders" && <><div><strong>{row.orderId}</strong><small>{formatDate(row.createdAt)}</small><span>{statusLabel(row.orderStatus)} · Payment {statusLabel(row.paymentStatus)?.toLowerCase()}</span></div><b>{money(row.totalAmount)}</b></>}
        {section === "products" && <><div><strong>{row.productName}</strong><span>{row.productId}</span><small>{row.category} · {statusLabel(row.status)} · {row.active ? "Enabled" : "Disabled"}</small></div><b>{money(row.price)}</b></>}
        {section === "requests" && <><div><strong>{row.productName}</strong><span>Request #{row.id} · {statusLabel(row.status)}</span><small>Submitted {formatDate(row.requestedAt)}{row.reviewedAt && ` · Closed ${formatDate(row.reviewedAt)}`}</small><small>{row.productId} · {row.categoryName} · {money(row.price)}</small>{row.note && <p>{row.note}</p>}{row.imported && <p className="user-history-note">Imported current state. Earlier submissions are unavailable.</p>}</div></>}
      </li>)}</ul>
      {!data.results.content.length && !(section === "overview" && !data.cart) && <p className="user-activity-empty">{section === "requests" ? "No listing requests match this filter. History begins with recorded submissions; older unrecorded decisions cannot be reconstructed." : section === "products" ? "No products listed." : section === "orders" ? "No orders placed." : "The bag is empty."}</p>}
      {data.results.totalPages > 1 && <nav className="user-activity-pagination" aria-label="Activity pagination"><button type="button" className="button button-outline" disabled={loading || page === 0} onClick={() => { begin(); setPage(value => value - 1); }}>Previous</button><span>Page {page + 1} of {data.results.totalPages}</span><button type="button" className="button button-outline" disabled={loading || page + 1 >= data.results.totalPages} onClick={() => { begin(); setPage(value => value + 1); }}>Next</button></nav>}
    </>}
  </section>;
}
