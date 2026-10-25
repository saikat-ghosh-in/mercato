import { useCallback, useEffect, useRef, useState } from "react";
import api from "../../backend/api";
import SelectControl from "../shared/SelectControl";
import { LoadingLayout } from "../shared/LoadingStates";
import { Spinner } from "../shared/Spinner";
import ModalFrame from "../shared/ModalFrame";
import "../auth/email-delivery.css";

const labels = { QUEUED: "Queued", SENDING: "Sending", RETRYING: "Retry scheduled", FAILED: "Needs attention", ACCEPTED: "Accepted by email service", SKIPPED: "Superseded / skipped", LEGACY_COMPLETED: "Completed before tracking" };
const diagnostics = {
  SMTP_IP_NOT_AUTHORIZED: "Authorize this server’s sending IP in Brevo → Security → Authorized IPs.",
  SMTP_AUTH_FAILED: "Check the SMTP key status, login and account settings in Brevo.",
  SMTP_REJECTED: "Review the sender, recipient and transactional logs in Brevo before retrying.",
  SMTP_TEMPORARILY_UNAVAILABLE: "The email service is temporarily unavailable. A retry is scheduled.",
  EMAIL_COMPOSITION_FAILED: "The email could not be prepared. Review the template and sender settings before retrying.",
};
const date = value => value ? new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : "—";
export default function EmailActivityTab() {
  const [data, setData] = useState(null), [error, setError] = useState("");
  const [filter, setFilter] = useState("ALL"), [page, setPage] = useState(0), [refreshing, setRefreshing] = useState(false);
  const [selected, setSelected] = useState(null), [reason, setReason] = useState(""), [retrying, setRetrying] = useState(false);
  const generation = useRef(0);
  const invalidate = useCallback(() => { generation.current++; }, []);
  const load = useCallback(async () => {
    const current = ++generation.current; setRefreshing(true);
    try { const response = await api.get("/admin/operations/emails", { params: { page, state: filter } }); if (current === generation.current) { setData(response.data); setError(""); } }
    catch (e) { if (current === generation.current && e.name !== "SessionChangedError") setError("Email activity couldn’t load. Please retry."); }
    finally { if (current === generation.current) setRefreshing(false); }
  }, [page, filter]);
  useEffect(() => { load(); return invalidate; }, [load, invalidate]);
  const retry = async event => {
    event.preventDefault(); if (retrying || !reason.trim()) return; setRetrying(true);
    try { await api.post(`/admin/operations/emails/${selected.id}/retry`, { reason: reason.trim() }); setSelected(null); setReason(""); await load(); }
    catch (e) { setError(e?.response?.data?.message || "This email couldn’t be retried. Refresh its status and try again."); }
    finally { setRetrying(false); }
  };
  if (!data && !error) return <LoadingLayout variant="dashboard" label="Loading email activity" />;
  return <section className="email-activity">
    <div className="flex flex-wrap items-start justify-between gap-4 mb-6"><div><h1 className="text-xl font-semibold">Email activity</h1><p className="text-sm text-gray-500 mt-2">Track queued messages and sending failures. SMTP acceptance confirms handoff, not inbox delivery.</p></div><button className="button button-outline" onClick={load} disabled={refreshing}>{refreshing && <Spinner size={14} />}Refresh</button></div>
    {error && <div role="alert" className="mail-delivery-status mail-delivery-error mb-4">{error} <button className="underline" onClick={load}>Retry</button></div>}
    {data && <>
      <div className="email-summary-grid mb-6">
        <div className="surface p-4"><p className="text-xs text-gray-500">Needs attention</p><p className="text-2xl font-semibold mt-2">{data.summary.failed}</p></div>
        <div className="surface p-4"><p className="text-xs text-gray-500">Queued or retrying</p><p className="text-2xl font-semibold mt-2">{data.summary.pending}</p></div>
        <div className="surface p-4"><p className="text-xs text-gray-500">Last provider acceptance</p><p className="text-sm font-medium mt-2">{date(data.summary.last_accepted_at)}</p></div>
      </div>
      <p className="text-sm text-gray-500 mb-5">Monthly reports: <strong>{data.monthlyReportsEnabled ? "On" : "Off"}</strong>. Active, verified admins receive marketplace totals; approved sellers receive only their own figures. Reports include months with no sales.</p>
      <div className="flex flex-wrap items-center gap-3 mb-4"><SelectControl aria-label="Email status" value={filter} onChange={e => { setFilter(e.target.value); setPage(0); }}><option value="ALL">All messages</option><option value="FAILED">Needs attention</option><option value="PENDING">Queued / retrying</option><option value="ACCEPTED">Accepted</option></SelectControl><span className="text-sm text-gray-500">{data.total} {data.total === 1 ? "message" : "messages"}</span></div>
      <div className="email-activity-list" aria-busy={refreshing}>{data.items.length === 0 ? <p className="py-10 text-gray-500">No email activity matches this filter.</p> : data.items.map(item => <article className="email-activity-row" key={item.id}>
        <div className="min-w-0"><p className="font-medium break-all">{item.recipient || "Recipient unavailable"}</p><p className="text-xs text-gray-500 mt-1">{item.purpose.replaceAll("_", " ")} · {date(item.created_at)}</p><p className={`text-sm mt-2 ${item.state === "FAILED" ? "text-red-700" : "text-gray-600"}`}>{labels[item.state] || "Status unavailable"}{item.smtp_code ? ` · SMTP ${item.smtp_code}` : ""}</p>{item.failure_code && <p className="text-sm text-gray-500 mt-1">{diagnostics[item.failure_code] || "Review the job and provider logs before retrying."}</p>}</div>
        <div className="text-xs text-gray-500"><p>{item.attempts} attempt{item.attempts === 1 ? "" : "s"}</p>{item.state === "RETRYING" && <p className="mt-1">Next: {date(item.due_at)}</p>}{item.accepted_at && <p className="mt-1">Accepted: {date(item.accepted_at)}</p>}{["WAITING", "NEEDS_ATTENTION"].includes(item.job_status) && <button className="button button-outline mt-3" onClick={() => { setSelected(item); setReason(""); }}>Retry email</button>}</div>
      </article>)}</div>
      {data.total > 30 && <div className="flex items-center justify-end gap-4 mt-5"><button className="button button-outline" disabled={page === 0 || refreshing} onClick={() => setPage(value => value - 1)}>Previous</button><span className="text-sm">Page {page + 1}</span><button className="button button-outline" disabled={(page + 1) * 30 >= data.total || refreshing} onClick={() => setPage(value => value + 1)}>Next</button></div>}
    </>}
    {selected && <ModalFrame title="Retry email" onClose={() => !retrying && setSelected(null)}><form className="space-y-4" onSubmit={retry}><p className="text-sm text-gray-600">Retry sending to <span className="break-all">{selected.recipient}</span> after correcting the cause. Expired or replaced verification links will be skipped.</p><label className="block text-sm">What was corrected?<textarea className="mt-2 w-full border rounded-md p-3" value={reason} onChange={e => setReason(e.target.value)} maxLength={500} required /></label><button className="button button-dark" disabled={retrying || !reason.trim()}>{retrying && <Spinner size={14} />}Queue retry</button></form></ModalFrame>}
  </section>;
}
