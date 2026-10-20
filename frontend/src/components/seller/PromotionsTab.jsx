import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import api from "../../backend/api";
import { formatCurrency as money } from "../../utils/formatCurrency";
import SelectControl from "../shared/SelectControl";
import ModalFrame from "../shared/ModalFrame";
import { promotionQuote } from "../../utils/promotionQuote";

export default function PromotionsTab() {
  const suspended = useSelector(state => Boolean(state.auth?.userDetails?.sellerSuspended));
  const [workspace, setWorkspace] = useState(null);
  const [products, setProducts] = useState([]);
  const [form, setForm] = useState({ productId: "", tier: "DISCOVERY", durationDays: 3, startDate: new Date().toISOString().slice(0, 10), authorizeDeduction: false });
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [cancel, setCancel] = useState(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([api.get("/seller/promotions", { signal: controller.signal }), api.get("/seller/products", { signal: controller.signal })])
      .then(([w, p]) => { if (!controller.signal.aborted) { setWorkspace(w.data); setProducts(p.data); } })
      .catch(err => { if (!controller.signal.aborted) setError(err.response?.data?.message || "Promotions could not load."); });
    return () => controller.abort();
  }, [attempt]);
  const change = patch => { setForm(f => ({ ...f, ...patch, authorizeDeduction: false })); setRequestId(crypto.randomUUID()); setError(""); setMessage(""); };
  const tier = workspace?.tiers.find(t => t.code === form.tier);
  const fee = promotionQuote(tier?.dailyRate, form.durationDays) ?? 0;
  const eligible = products.filter(p => p.active && p.listingStatus === "APPROVED" && p.availableQty > 0);
  const book = async event => {
    event.preventDefault(); setBusy(true); setError("");
    try { await api.post("/seller/promotions", { ...form, durationDays: Number(form.durationDays), expectedFee: fee, requestId }); setMessage("Campaign booked. The fee is recorded against your earnings."); setForm(f => ({ ...f, authorizeDeduction: false })); setRequestId(crypto.randomUUID()); setAttempt(a => a + 1); }
    catch (err) { setError(err.response?.data?.message || "Booking could not be confirmed. Retry with the same quote to avoid a duplicate."); }
    finally { setBusy(false); }
  };
  const cancelCampaign = async () => {
    setBusy(true); setError("");
    try { await api.post(`/seller/promotions/${cancel.id}/cancel`); setCancel(null); setMessage("Campaign cancelled. Unused whole days were credited to your earnings."); setAttempt(a => a + 1); }
    catch (err) { setError(err.response?.data?.message || "Campaign could not be cancelled."); }
    finally { setBusy(false); }
  };
  return <section className="promotion-workspace"><div className="finance-heading"><div><p className="eyebrow">OPTIONAL PAID PROMOTIONS</p><h2>Give a good find more visibility.</h2><p>Listing is free. Choose extra exposure only when it works for your business.</p></div><div className="promotion-budget"><span>Eligible earnings budget</span><strong>{money(workspace?.availableBudget || 0)}</strong></div></div>
    {error && <div role="alert" className="inline-error">{error}<button onClick={() => setAttempt(a => a + 1)}>Refresh</button></div>}{message && <p role="status" className="inline-success">{message}</p>}
    {!workspace ? <p role="status">Loading campaign options…</p> : <><form className="promotion-builder" onSubmit={book}><div className="promotion-editor"><h3>Build your campaign</h3><label htmlFor="campaign-product">Product</label><SelectControl id="campaign-product" required value={form.productId} disabled={busy} onChange={e => change({ productId: e.target.value })}><option value="">Choose an approved product</option>{eligible.map(p => <option value={p.productId} key={p.productId}>{p.productName}</option>)}</SelectControl>{!eligible.length && <p className="text-sm text-gray-500">An approved listing with available stock is needed. Submit your product in Products and record a supply update.</p>}
      <fieldset className="promotion-tiers"><legend>Exposure level</legend>{workspace.tiers.map(t => <label key={t.code} className={form.tier === t.code ? "selected" : ""}><input type="radio" name="promotion-tier" value={t.code} checked={form.tier === t.code} disabled={busy} onChange={() => change({ tier: t.code })} /><span><strong>{t.name}</strong><small>{t.placement}</small></span><b>{money(t.dailyRate)}<small>/ day</small></b></label>)}</fieldset>
      <div className="promotion-dates"><label>Duration<SelectControl value={form.durationDays} disabled={busy} onChange={e => change({ durationDays: Number(e.target.value) })}>{workspace.durations.map(d => <option key={d} value={d}>{d} days</option>)}</SelectControl></label><label>Start date (UTC)<input required type="date" min={new Date().toISOString().slice(0, 10)} max={new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10)} value={form.startDate} disabled={busy} onChange={e => change({ startDate: e.target.value })} /></label></div></div>
      <aside className="promotion-quote"><p className="eyebrow">YOUR QUOTE</p><h3>{money(fee)}</h3><p>{money(tier?.dailyRate)} × {form.durationDays} days · fixed total</p><dl><div><dt>Upfront listing cost</dt><dd>₹0</dd></div><div><dt>Campaign deduction</dt><dd>{money(fee)}</dd></div><div><dt>Budget after booking</dt><dd>{money(Math.max(0, Number(workspace.availableBudget) - fee))}</dd></div></dl><p>The campaign fee is booked now and deducted from eligible earnings. This does not initiate a bank payment. No automatic renewal.</p><p>Sponsored shelves rotate by level and booking order. Placement or sales are not guaranteed. Listings with no stock or awaiting approval are hidden.</p><p>Cancel before the start for a full credit. After starting, unused whole days are credited; a started day is charged. Up to three current or scheduled campaigns.</p><label className="promotion-consent"><input type="checkbox" checked={form.authorizeDeduction} disabled={busy} onChange={e => setForm(f => ({ ...f, authorizeDeduction: e.target.checked }))} /><span>I authorize the {money(fee)} deduction and accept these campaign terms.</span></label><button className="button button-dark" disabled={suspended || busy || !form.productId || !form.authorizeDeduction || fee > Number(workspace.availableBudget)}>{busy ? "Confirming…" : "Book campaign"}</button>{fee > Number(workspace.availableBudget) && <p>Complete paid fulfillments to build your promotion budget. Refunded orders and unpriced historical sales are excluded.</p>}</aside></form>
      <div className="finance-table surface"><h3>Your campaigns</h3><div className="overflow-x-auto"><table><thead><tr>{["Product / level", "Period (UTC)", "Fee", "Credited", "Status", ""].map((h, i) => <th key={i}>{h}</th>)}</tr></thead><tbody>{workspace.campaigns.length ? workspace.campaigns.map(c => { const status = c.status === "CANCELLED" ? "Cancelled" : new Date(c.endsAt) <= new Date() ? "Completed" : new Date(c.startsAt) > new Date() ? "Scheduled" : "Active"; return <tr key={c.id}><td>{products.find(p => p.productId === c.productId)?.productName || c.productId}<small>{c.tier}</small></td><td>{new Date(c.startsAt).toLocaleString("en-IN", { timeZone: "UTC" })}<small>to {new Date(c.endsAt).toLocaleString("en-IN", { timeZone: "UTC" })}</small></td><td>{money(c.fee)}</td><td>{money(c.credited)}</td><td>{status}</td><td>{["Scheduled", "Active"].includes(status) && <button className="button button-outline" disabled={busy} onClick={() => setCancel(c)}>Cancel campaign</button>}</td></tr>; }) : <tr><td colSpan={6}>No campaigns booked. Your products can sell without a promotion.</td></tr>}</tbody></table></div></div>
    </>}
    {cancel && <ModalFrame title="Cancel campaign" onClose={() => { if (!busy) setCancel(null); }}><div className="p-6 space-y-4"><h2>Cancel this campaign?</h2><p>Any unused whole days will be credited to your earnings. A day that has already started is charged.</p><div className="flex gap-3"><button className="button button-outline" disabled={busy} onClick={() => setCancel(null)}>Keep campaign</button><button className="button button-dark" disabled={busy} onClick={cancelCampaign}>Confirm cancellation</button></div></div></ModalFrame>}
  </section>;
}
