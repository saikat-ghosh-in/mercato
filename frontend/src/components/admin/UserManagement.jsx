import ActionArrow from "../shared/ActionArrow";
import { useEffect, useRef, useState } from "react";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
import { FiSearch, FiX } from "react-icons/fi";
import SelectControl from "../shared/SelectControl";
import UserActivity from "./UserActivity";
import api from "../../backend/api";
import { formatDate } from "../../utils/formatDate";
import { isSellerAccount } from "../../utils/accountRouting";
import { accessActions, accessLabels, accountStatus, filterUsers } from "../../utils/userAccess";
import "./user-access.css";

const roleLabel = role => ({ ROLE_ADMIN: "Admin", ROLE_SELLER: "Seller", ROLE_USER: "User" }[role] || role);
const nameOf = user => [user.firstName, user.lastName].filter(Boolean).join(" ") || user.username;
const consequences = {
  DEACTIVATE: "Sign-in and existing sessions will stop. Listings will be hidden. Account, orders and history will be retained.",
  REACTIVATE: "Sign-in will be available again. Selling stays suspended if it was suspended separately. The user must sign in again.",
  SUSPEND_SELLING: "Listings will be hidden. New listing changes and promotions will stop. Shopping, account history and existing-order management stay available. The user must sign in again.",
  RESTORE_SELLING: "Approved listings will become available again. The seller can manage listings and promotions after signing in again.",
};

function AccessHistory({ user }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api.get(`/admin/users/${user.userId}/access-history`, { params: { page }, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) { setData(data); setError(""); } })
      .catch(err => { if (!controller.signal.aborted) setError(err.response?.data?.message || "Access history couldn’t load."); });
    return () => controller.abort();
  }, [user.userId, user.accessVersion, page, retry]);
  return <section className="access-history" aria-label="Access history">
    <h3>Access history</h3><p>Recorded access decisions, including who made each change.</p>
    {error && <p role="alert" className="inline-error">{error} <button type="button" onClick={() => setRetry(value => value + 1)}>Retry</button></p>}
    {!data && !error && <p role="status">Loading history…</p>}
    {data && <><ol>{data.content.map(event => <li key={event.id}><div><strong>{accessLabels[event.action] || event.action.replaceAll("_", " ").toLowerCase()}</strong><time dateTime={event.createdAt}>{formatDate(event.createdAt)}</time></div><p>{event.reason}</p><small>{event.actorUsername} · {event.previousState} → {event.nextState}</small></li>)}</ol>
      {!data.content.length && <p>No recorded access changes. Earlier changes are not reconstructed.</p>}
      {data.totalPages > 1 && <nav aria-label="Access history pages" className="access-pager"><button className="button button-outline" disabled={!page} onClick={() => setPage(value => value - 1)}>Previous</button><span>{page + 1} / {data.totalPages}</span><button className="button button-outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Next</button></nav>}
    </>}
  </section>;
}

function UserDrawer({ user, onClose, onUpdate, onRefresh }) {
  const [action, setAction] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const accessTitle = useRef(null);
  const actions = accessActions(user);
  const save = async event => {
    event.preventDefault();
    if (reason.trim().length < 10) { setError("Give a reason of at least 10 characters."); return; }
    setSaving(true); setError("");
    try {
      const { data } = await api.post(`/admin/users/${user.userId}/access`, { action, reason: reason.trim(), expectedVersion: user.accessVersion });
      onUpdate(data); setAction(""); setReason("");
      accessTitle.current?.focus({ preventScroll: true });
    } catch (err) { setError(err.response?.data?.message || "Access couldn’t be updated. Try again."); if (err.response?.status === 409) onRefresh(); }
    finally { setSaving(false); }
  };
  return <Dialog open onClose={() => { if (!saving) onClose(); }} className="relative z-[80]">
    <DialogBackdrop transition className="dialog-backdrop" /><DialogPanel transition className="management-drawer access-drawer">
      <header><div><DialogTitle>User details</DialogTitle><p>{user.username}</p></div><button type="button" className="icon-action" onClick={onClose} disabled={saving} aria-label="Close user details"><FiX aria-hidden="true" /></button></header>
      <div className="management-drawer-body access-drawer-body">
        <div className="access-identity"><span className="access-avatar" aria-hidden="true">{nameOf(user)[0]}</span><div><h2>{nameOf(user)}</h2><p>{user.email}</p></div></div>
        <div className="access-summary"><div><span>Account</span><strong>{accountStatus(user)}</strong></div><div><span>Roles</span><strong>{user.roles?.map(roleLabel).join(", ")}</strong></div>{isSellerAccount(user) && <div><span>Selling</span><strong>{user.sellerSuspended ? "Suspended" : user.sellerStatus === "APPROVED" ? "Approved" : ({ PENDING: "Awaiting approval", REJECTED: "Declined" }[user.sellerStatus] || "Not approved")}</strong></div>}</div>
        <dl className="access-facts">{[["User ID", user.userId], ["Phone", user.phoneNumber || "—"], ["Joined", formatDate(user.createdAt)], ["Last sign-in", formatDate(user.lastLoginAt)], ...(isSellerAccount(user) ? [["Store", user.sellerDisplayName || "—"]] : [])].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        <section className="access-controls" aria-label="Manage access"><h3 tabIndex={-1} ref={accessTitle}>Manage access</h3>{user.roles?.includes("ROLE_ADMIN") ? <p>Administrator accounts are read-only here.</p> : <><p>Every account keeps its base user role. Selling requires an approved application.</p>
          {!action ? <div className="access-actions">{actions.map(value => <button key={value} type="button" className="button button-outline" onClick={() => { setAction(value); setError(""); }}>{accessLabels[value]}</button>)}</div> : <form onSubmit={save} className="access-confirm"><h4>{accessLabels[action]}</h4><p>{consequences[action]}</p><label htmlFor="access-reason">Reason <span>Required · visible to administrators</span></label><textarea autoFocus id="access-reason" value={reason} onChange={event => setReason(event.target.value)} required minLength={10} maxLength={500} rows={3} aria-describedby="access-reason-help" /><small id="access-reason-help">10–500 characters. Explain the decision without sensitive personal data.</small>{error && <p role="alert" className="inline-error">{error}</p>}<div className="access-actions"><button type="button" className="button button-outline" disabled={saving} onClick={() => { setAction(""); setError(""); accessTitle.current?.focus({ preventScroll: true }); }}>Cancel</button><button type="submit" className="button button-dark" disabled={saving}>{saving ? "Saving…" : "Confirm change"}</button></div></form>}
        </>}</section>
        <UserActivity key={user.userId} user={user} />
        <AccessHistory user={user} />
      </div>
    </DialogPanel>
  </Dialog>;
}

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [sort, setSort] = useState("newest");
  const [selectedId, setSelectedId] = useState(null);
  useEffect(() => {
    const controller = new AbortController();
    api.get("/admin/users", { signal: controller.signal }).then(({ data }) => { if (!controller.signal.aborted) { setUsers(data || []); setError(""); } })
      .catch(err => { if (!controller.signal.aborted) setError(err.response?.data?.message || "Users couldn’t load."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [retry]);
  const filtered = filterUsers(users, { search, role, status, sort });
  const selected = users.find(user => user.userId === selectedId);
  return <section className="access-workspace" aria-label="User management">
    <div className="access-heading"><div><h2>Users</h2><p>Account access, selling permissions and activity.</p></div><div className="access-heading-actions"><span className="access-count">{filtered.length} of {users.length}</span><button type="button" className="button button-outline" onClick={() => setRetry(value => value + 1)}>Refresh</button></div></div>
    <div className="access-toolbar"><label className="access-search"><FiSearch aria-hidden="true" /><input type="search" aria-label="Search users" placeholder="Name, email or store" value={search} onChange={event => setSearch(event.target.value)} /></label><SelectControl aria-label="Filter users by role" value={role} onChange={event => setRole(event.target.value)}><option value="">All roles</option>{["ROLE_USER", "ROLE_SELLER", "ROLE_ADMIN"].map(role => <option key={role} value={role}>{roleLabel(role)}</option>)}</SelectControl><SelectControl aria-label="Filter users by status" value={status} onChange={event => setStatus(event.target.value)}>{[["", "All statuses"], ["Active", "Active"], ["Awaiting verification", "Awaiting verification"], ["Deactivated", "Deactivated"], ["Paused", "Paused"], ["Locked", "Locked"], ["selling-suspended", "Selling suspended"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectControl><SelectControl aria-label="Sort users" value={sort} onChange={event => setSort(event.target.value)}><option value="newest">Newest</option><option value="oldest">Oldest</option><option value="name">Name</option></SelectControl></div>
    {error && <p role="alert" className="inline-error">{error} <button type="button" onClick={() => setRetry(value => value + 1)}>Retry</button></p>}
    {loading && !users.length ? <div className="access-loading" role="status">Loading users…</div> : <><div className="access-list-header" aria-hidden="true"><span>User</span><span>Roles</span><span>Account</span><span>Joined</span><span /></div><ul className="access-list">{filtered.map(user => <li key={user.userId}><button type="button" aria-label={`View user details for ${user.username}`} onClick={() => setSelectedId(user.userId)}><div className="access-list-identity"><span className="access-avatar" aria-hidden="true">{nameOf(user)[0]}</span><div><strong>{nameOf(user)}</strong><span>{user.email}</span><small>{user.username}</small></div></div><div className="access-list-roles"><span>{user.roles?.map(roleLabel).join(", ")}</span>{user.sellerSuspended && <small>Selling suspended</small>}</div><span className="access-list-status" data-inactive={!user.enabled || user.accountLocked}>{accountStatus(user)}</span><time dateTime={user.createdAt}>{formatDate(user.createdAt)}</time><ActionArrow aria-hidden="true" /></button></li>)}</ul>{!filtered.length && <div className="access-empty"><h3>No matching users</h3><p>Try another search or clear the filters.</p><button className="button button-outline" onClick={() => { setSearch(""); setRole(""); setStatus(""); }}>Clear filters</button></div>}</>}
    {selected && <UserDrawer key={selected.userId} user={selected} onClose={() => setSelectedId(null)} onRefresh={() => setRetry(value => value + 1)} onUpdate={updated => setUsers(previous => previous.map(user => user.userId === updated.userId ? updated : user))} />}
  </section>;
}
