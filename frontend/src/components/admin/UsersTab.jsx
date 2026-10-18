import SelectControl from "../shared/SelectControl";
import { Dialog, DialogPanel, DialogTitle } from "@headlessui/react";
import ModalFrame from "../shared/ModalFrame";
import { useEffect, useState } from "react";
import {
    MdSearch, MdClose, MdPerson, MdAdminPanelSettings,
    MdStorefront, MdVerified, MdEdit,
    MdCheck, MdLock,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatDate } from "../../utils/formatDate";

const ALL_ROLES = ["ROLE_USER", "ROLE_SELLER", "ROLE_ADMIN"];

const ROLE_CONFIG = {
    ROLE_ADMIN: { label: "Admin", color: "bg-purple-100 text-purple-700", icon: MdAdminPanelSettings },
    ROLE_SELLER: { label: "Seller", color: "bg-amber-100 text-amber-700", icon: MdStorefront },
    ROLE_USER: { label: "User", color: "bg-blue-100 text-blue-700", icon: MdPerson },
};

const TableSkeleton = () => (
    <div className="animate-pulse space-y-2 p-4">
        {[1, 2, 3, 4, 5].map(i => (
            <div key={i} className="flex gap-4 py-3">
                <div className="h-8 w-8 bg-gray-100 rounded-full shrink-0" />
                <div className="flex-1 space-y-1.5">
                    <div className="h-3 bg-gray-100 rounded w-1/3" />
                    <div className="h-2.5 bg-gray-100 rounded w-1/4" />
                </div>
                <div className="h-5 bg-gray-100 rounded w-16" />
                <div className="h-5 bg-gray-100 rounded w-20" />
            </div>
        ))}
    </div>
);

const EditRolesModal = ({ user, onClose, onSave }) => {
    const [roles, setRoles] = useState(new Set(user.roles));
    const [saving, setSaving] = useState(false);

    const toggleRole = (role) => {
        setRoles(prev => {
            const next = new Set(prev);
            if (next.has(role)) {
                if (next.size === 1) { toast.error("User must have at least one role"); return prev; }
                next.delete(role);
            } else {
                next.add(role);
            }
            return next;
        });
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            await api.put(`/admin/${user.userId}/roles`, Array.from(roles));
            toast.success("Roles updated");
            onSave();
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to update roles");
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalFrame title="Edit Roles" onClose={onClose}>
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
                <div className="flex items-center justify-between p-5 border-b border-gray-100">
                    <div>
                        <h2 className="text-sm font-bold text-gray-800">Edit Roles</h2>
                        <p className="text-[11px] text-gray-400 mt-0.5">{user.username}</p>
                    </div>
                    <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
                        <MdClose size={18} />
                    </button>
                </div>

                <div className="p-5 space-y-2">
                    {ALL_ROLES.map(role => {
                        const cfg = ROLE_CONFIG[role];
                        const Icon = cfg.icon;
                        const active = roles.has(role);
                        return (
                            <button
                                key={role}
                                onClick={() => toggleRole(role)}
                                className={`control w-full flex items-center gap-3 px-4 py-3 rounded-xl border transition-[transform,opacity,background-color] cursor-pointer ${active
                                        ? "border-gray-950 bg-gray-950 text-white"
                                        : "border-gray-200 text-gray-600 hover:bg-gray-50"
                                    }`}
                            >
                                <Icon size={16} className={active ? "text-white" : "text-gray-400"} />
                                <span className="text-sm font-semibold flex-1 text-left">{cfg.label}</span>
                                {active && <MdCheck size={16} className="text-white" />}
                            </button>
                        );
                    })}
                </div>

                <div className="flex gap-3 p-5 border-t border-gray-100">
                    <button  onClick={onClose}
                        className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">
                        Cancel
                    </button>
                    <button onClick={handleSave} disabled={saving}
                        className="control flex-1 px-3 py-2.5 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98]">
                        {saving ? "Saving..." : "Save Roles"}
                    </button>
                </div>
            </div>
        </ModalFrame>
    );
};

const UserDrawer = ({ user, onClose, onRolesEdit }) => (
    <Dialog open onClose={onClose} className="relative z-[80]"><div className="dialog-backdrop" aria-hidden="true" /><DialogPanel transition className="mobile-panel management-drawer"><DialogTitle className="sr-only">User details</DialogTitle>
            <div className="flex items-center justify-between p-5 border-b border-gray-100">
                <h2 className="text-sm font-bold text-gray-800">User Details</h2>
                <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer">
                    <MdClose size={18} />
                </button>
            </div>
            <div className="p-5 space-y-5 flex-1">
                {/* Avatar + name */}
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-full bg-linear-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-base shrink-0">
                        {user.firstName?.[0] ?? user.username?.[0] ?? "?"}
                    </div>
                    <div>
                        <p className="text-sm font-bold text-gray-900">
                            {[user.firstName, user.lastName].filter(Boolean).join(" ") || user.username}
                        </p>
                        <p className="text-xs text-gray-400">{user.email}</p>
                    </div>
                </div>

                {/* Roles */}
                <div>
                    <div className="flex items-center justify-between mb-2">
                        <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">Roles</p>
                        <button
                            onClick={onRolesEdit}
                            className="flex items-center gap-1 text-[11px] font-semibold text-blue-500 hover:text-blue-700 cursor-pointer"
                        >
                            <MdEdit size={12} /> Edit
                        </button>
                    </div>
                    <div className="flex gap-1.5 flex-wrap">
                        {user.roles.map(role => {
                            const cfg = ROLE_CONFIG[role] ?? { label: role, color: "bg-gray-100 text-gray-600" };
                            return (
                                <span key={role} className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${cfg.color}`}>
                                    {cfg.label}
                                </span>
                            );
                        })}
                    </div>
                </div>

                {/* Status badges */}
                <div>
                    <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-2">Status</p>
                    <div className="flex gap-1.5 flex-wrap">
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1 ${user.enabled ? "bg-blue-100 text-blue-700" : "bg-red-100 text-red-600"
                            }`}>
                            {user.enabled ? <MdCheck size={10} /> : <MdClose size={10} />}
                            {user.enabled ? "Active" : "Disabled"}
                        </span>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1 ${user.emailVerified ? "bg-blue-100 text-blue-700" : "bg-yellow-100 text-yellow-700"
                            }`}>
                            <MdVerified size={10} />
                            {user.emailVerified ? "Verified" : "Unverified"}
                        </span>
                        {user.accountLocked && (
                            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-600 flex items-center gap-1">
                                <MdLock size={10} /> Locked
                            </span>
                        )}
                    </div>
                </div>

                {/* Info rows */}
                {[
                    ["User ID", user.userId],
                    ["Username", user.username],
                    ["Phone", user.phoneNumber ?? "—"],
                    ["Seller Name", user.sellerDisplayName ?? "—"],
                    ["Member Since", formatDate(user.createdAt)],
                    ["Last Login", formatDate(user.lastLoginAt)],
                ].map(([label, value]) => (
                    <div key={label} className="flex justify-between py-2 border-b border-gray-50 last:border-0">
                        <span className="text-xs text-gray-400">{label}</span>
                        <span className="text-xs font-semibold text-gray-800 text-right max-w-[60%] break-all">{value}</span>
                    </div>
                ))}
            </div>
        </DialogPanel>
    </Dialog>
);


const UsersTab = () => {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [roleFilter, setRoleFilter] = useState("");
    const [selectedUser, setSelectedUser] = useState(null);
    const [editRolesUser, setEditRolesUser] = useState(null);

    const fetchUsers = async () => {
        try {
            setLoading(true);
            const { data } = await api.get("/admin/users");
            setUsers(data ?? []);
        } catch {
            toast.error("Failed to load users");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchUsers(); }, []);

    const filtered = users.filter(u => {
        const matchSearch = !search ||
            u.username?.toLowerCase().includes(search.toLowerCase()) ||
            u.email?.toLowerCase().includes(search.toLowerCase()) ||
            u.firstName?.toLowerCase().includes(search.toLowerCase());
        const matchRole = !roleFilter || u.roles?.includes(roleFilter);
        return matchSearch && matchRole;
    });

    return (
        <div className="surface overflow-hidden">
            {/* Toolbar */}
            <div className="flex items-center gap-3 p-4 border-b border-gray-50 flex-wrap">
                <div className="dashboard-search flex-1 min-w-48">
                    <MdSearch size={14} className="text-gray-400 shrink-0" />
                    <input
                        type="text"
                        aria-label="Search by name, username or email..." value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Search by name, username or email..."
                        className="text-xs text-gray-700 focus:outline-none w-full"
                    />
                    {search && (
                        <button aria-label="Clear search" onClick={() => setSearch("")} className="icon-action text-gray-300 hover:text-gray-500 cursor-pointer">
                            <MdClose size={13} />
                        </button>
                    )}
                </div>
                <SelectControl aria-label="Filter users by role"
                    value={roleFilter}
                    onChange={e => setRoleFilter(e.target.value)}
                    className="text-xs border border-gray-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
                >
                    <option value="">All Roles</option>
                    {ALL_ROLES.map(r => (
                        <option key={r} value={r}>{ROLE_CONFIG[r]?.label ?? r}</option>
                    ))}
                </SelectControl>
                <span className="text-xs text-gray-400 font-semibold shrink-0">
                    {filtered.length} user{filtered.length !== 1 ? "s" : ""}
                </span>
            </div>

            {/* Table */}
            {loading && !users.length ? <TableSkeleton /> : (
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-gray-50">
                                {["User", "Email", "Roles", "Status", "Joined", ""].map(h => (
                                    <th key={h} className="text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest px-4 py-3">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.length === 0 ? (
                                <tr><td colSpan={6} className="text-center py-10 text-sm text-gray-400">No users found</td></tr>
                            ) : filtered.map(u => (
                                <tr
                                    key={u.userId}
                                    className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors cursor-pointer"
                                    tabIndex={0} onKeyDown={e => { if (e.target === e.currentTarget && ["Enter", " "].includes(e.key)) { e.preventDefault(); setSelectedUser(u); } }} onClick={() => setSelectedUser(u)}
                                >
                                    <td className="px-4 py-3">
                                        <div className="flex items-center gap-2.5">
                                            <div className="w-8 h-8 rounded-full bg-linear-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold shrink-0">
                                                {u.firstName?.[0] ?? u.username?.[0] ?? "?"}
                                            </div>
                                            <div>
                                                <p className="text-sm font-semibold text-gray-900">
                                                    {[u.firstName, u.lastName].filter(Boolean).join(" ") || u.username}
                                                </p>
                                                <p className="text-[10px] text-gray-400">{u.username}</p>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-4 py-3 text-xs text-gray-600">{u.email}</td>
                                    <td className="px-4 py-3">
                                        <div className="flex gap-1 flex-wrap">
                                            {u.roles?.map(role => {
                                                const cfg = ROLE_CONFIG[role] ?? { label: role, color: "bg-gray-100 text-gray-600" };
                                                return (
                                                    <span key={role} className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${cfg.color}`}>
                                                        {cfg.label}
                                                    </span>
                                                );
                                            })}
                                        </div>
                                    </td>
                                    <td className="px-4 py-3">
                                        <div className="flex gap-1 flex-wrap">
                                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${u.enabled ? "bg-blue-100 text-blue-700" : "bg-red-100 text-red-600"
                                                }`}>
                                                {u.enabled ? "Active" : "Disabled"}
                                            </span>
                                            {u.accountLocked && (
                                                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-600">
                                                    Locked
                                                </span>
                                            )}
                                        </div>
                                    </td>
                                    <td className="px-4 py-3 text-xs text-gray-400">{formatDate(u.createdAt)}</td>
                                    <td className="px-4 py-3">
                                        <button
                                            aria-label={"Edit roles for " + u.username} onClick={e => { e.stopPropagation(); setEditRolesUser(u); }}
                                            className="icon-action p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded-lg transition-colors cursor-pointer"
                                        >
                                            <MdEdit size={14} />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {selectedUser && (
                <UserDrawer
                    user={selectedUser}
                    onClose={() => setSelectedUser(null)}
                    onRolesEdit={() => { setEditRolesUser(selectedUser); setSelectedUser(null); }}
                />
            )}
            {editRolesUser && (
                <EditRolesModal
                    user={editRolesUser}
                    onClose={() => setEditRolesUser(null)}
                    onSave={() => { setEditRolesUser(null); fetchUsers(); }}
                />
            )}
        </div>
    );
};

export default UsersTab;
