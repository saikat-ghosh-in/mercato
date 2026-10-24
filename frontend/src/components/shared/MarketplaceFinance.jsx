import { useEffect, useState } from "react";
import api from "../../backend/api";
import { formatCurrency as money } from "../../utils/formatCurrency";
import ReportTimeframe from "./ReportTimeframe";

const Metric = ({ label, value, detail, dark }) => <div className={`finance-metric ${dark ? "finance-metric-dark" : ""}`}><span>{label}</span><strong>{money(value)}</strong><p>{detail}</p></div>;
export default function MarketplaceFinance({ admin = false }) {
  const [data, setData] = useState(null);
  const [query, setQuery] = useState({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api.get(admin ? "/admin/finance" : "/seller/finance", { params: query, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) { setData(data); setError(""); } })
      .catch(err => { if (!controller.signal.aborted) setError(err.response?.data?.message || "Financial report could not load."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [admin, query, attempt]);
  const apply = range => { setLoading(true); setQuery(range); };
  return <section className="finance-section" aria-busy={loading}>
    <div className="finance-heading"><div><p className="eyebrow">{admin ? "MARKETPLACE FINANCES" : "YOUR BUSINESS, IN NUMBERS"}</p><h2>{admin ? "Sales are only part of the picture." : "See what your sales earned."}</h2><p>Paid orders created in the selected period</p></div></div>
    <div className="finance-period"><ReportTimeframe range={data ? { from: data.from, through: data.through } : undefined} loading={loading} onApply={apply} /><span role="status">{loading && data ? "Updating report…" : ""}</span></div>
    {error && <div className="inline-error" role="alert">{error}<button onClick={() => { setLoading(true); setAttempt(a => a + 1); }}>Retry report</button></div>}
    {!data ? <p role="status">{loading ? "Loading financial breakdown…" : "No report available."}</p> : <>
      <div className="finance-metrics">
        <Metric label="Fulfilled merchandise" value={data.totals.fulfilledSales} detail="Shipped units from paid orders" />
        <Metric label="Sales commission" value={data.totals.commission} detail="At each order’s captured rate" />
        {admin ? <Metric label="Customer platform fees" value={data.platformFees} detail="Buyer-paid; excluded from seller deductions" /> : <Metric label="Promotion expense" value={data.totals.promotionFees} detail="Bookings less cancellation credits in this period" />}
        <Metric dark label={admin ? "Commission + platform fees" : "Known net earnings"} value={admin ? data.platformAccrualBeforeCosts : data.totals.knownNetEarnings} detail={admin ? "Accrued before operating costs and taxes" : "After fees, delivery and refund holds"} />
      </div>
      <div className="finance-detail-grid"><div className="finance-breakdown"><h3>{admin ? "Funds and fees" : "Your earnings breakdown"}</h3><dl>
        {(admin ? [["Customer payments captured", data.capturedPayments], ...(Number(data.unverifiedLegacyCollections) > 0 ? [["Legacy collections awaiting verification", data.unverifiedLegacyCollections]] : []), ["Successful customer refunds", data.successfulRefunds], ["Refunds awaiting completion", data.pendingRefunds], ["Customer delivery charges", data.shippingCollected], ["Seller-paid delivery", data.totals.sellerPaidDelivery], ["Promotion bookings", data.promotionBookings], ["Promotion cancellation credits", data.promotionCredits], ["Legacy charges without a breakdown", data.unclassifiedLegacyCharges]] : [["Paid merchandise ordered", data.totals.paidMerchandise], ["Cancelled merchandise (excluded from fulfilled sales)", data.totals.cancelledMerchandise], ["Fulfilled sales", data.totals.fulfilledSales], ["Less refunded fulfilled merchandise", data.totals.refundedFulfilledSales], ["Less sales commission", data.totals.commission], ["Less seller-paid delivery", data.totals.sellerPaidDelivery], ["Less net promotion expense", data.totals.promotionFees], ["Less unallocated refund hold", data.totals.refundHold], ["Legacy sales excluded: rate unknown", data.totals.unpricedLegacySales], ["Known net earnings", data.totals.knownNetEarnings]]).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{money(value)}</dd></div>)}
      </dl></div><aside className="finance-explainer"><h3>{admin ? "Read the numbers correctly." : "Earnings, with the details visible."}</h3><p>{admin ? "Customer payments include money owed to sellers and delivery charges. They are not Mercato’s revenue. Promotion bookings fund future advertising and are shown separately from earned sales commission." : "Only shipped, paid merchandise earns commission. Unshipped and cancelled units do not inflate your net earnings. Promotions use eligible earnings and their expense is shown explicitly."}</p><p>Older sales without a captured commission rate remain unpriced. Unknown refund allocations are held for reconciliation. Values are accruals before tax adjustments; bank settlement and payout transfers are not connected.</p><p>Seller-paid delivery is deducted only for shipped units and shown separately. Promotion bookings and credits follow their own booking or cancellation dates. Delivery collections are pass-through charges, not shipping profit.</p></aside></div>
      {admin && <div className="finance-table surface"><h3>Seller earnings breakdown</h3><div className="overflow-x-auto"><table><thead><tr>{["Seller", "Fulfilled sales", "Refunded sales", "Commission", "Delivery", "Promotions", "Refund hold", "Unpriced sales", "Known net"].map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{data.sellerBreakdown.length ? data.sellerBreakdown.map((s, i) => <tr key={i}><td>{s.seller}</td>{[s.fulfilledSales, s.refundedFulfilledSales, s.commission, s.sellerPaidDelivery, s.promotionFees, s.refundHold, s.unpricedLegacySales, s.knownNetEarnings].map((v, j) => <td key={j}>{money(v)}</td>)}</tr>) : <tr><td colSpan={9}>No paid sales in this period.</td></tr>}</tbody></table></div></div>}
      <details className="finance-table surface"><summary>Order-line calculations · latest {data.lines.length} lines</summary><div className="overflow-x-auto"><table><thead><tr>{["Order / product", "Fulfilled", "Refunded", "Rate", "Commission", "Seller-paid delivery", "Net before promotions"].map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{data.lines.map((line, i) => <tr key={i}><td>{line.product}<small>{line.orderId}</small></td><td>{money(line.fulfilled)}</td><td>{money(line.refunded)}</td><td>{line.commissionRate == null ? "Unknown" : `${line.commissionRate}%`}{line.commissionCapPerItem != null && <small>Max {money(line.commissionCapPerItem)} / item</small>}</td><td>{money(line.commission)}</td><td>{money(line.sellerPaidDelivery)}</td><td>{line.netBeforePromotions == null ? "Unpriced" : money(line.netBeforePromotions)}</td></tr>)}</tbody></table></div></details>
    </>}
  </section>;
}
