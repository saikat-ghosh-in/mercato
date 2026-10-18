import ModalFrame from "../shared/ModalFrame";
import { useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
    MdLogout, MdShoppingBag, MdShoppingCart, MdLocationOn,
    MdClose, MdEdit, MdCheck, MdLock, MdFavorite,
    MdVerified, MdWarning, MdStorefront, MdPerson,
    MdAdminPanelSettings, MdContentCopy
} from "react-icons/md";
import toast from "react-hot-toast";
import { logOutUser } from "../../reduxStore/actions/authActions";
import profilePlaceholderImage from "../../assets/profile-placeholder.png";
import { formatDate } from "../../utils/formatDate";
import api from "../../backend/api";

const getRoleBadge = (role) => {
    const map = {
        ROLE_ADMIN: { label: "Admin", color: "bg-purple-100 text-purple-800", icon: MdAdminPanelSettings },
        ROLE_SELLER: { label: "Seller", color: "bg-amber-100 text-amber-800", icon: MdStorefront },
        ROLE_USER: { label: "User", color: "bg-blue-100 text-blue-800", icon: MdPerson },
    };
    return map[role] ?? { label: role, color: "bg-gray-100 text-gray-700", icon: MdPerson };
};

const Skeleton = () => (
    <div className="bg-gray-50 min-h-screen py-6">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6 animate-pulse">
            <div className="surface p-5 flex items-center gap-4">
                <div className="w-16 h-16 rounded-full bg-gray-100 shrink-0" />
                <div className="flex-1 space-y-2">
                    <div className="h-4 bg-gray-100 rounded w-1/3" />
                    <div className="h-3 bg-gray-100 rounded w-1/4" />
                    <div className="h-5 bg-gray-100 rounded w-1/5 mt-1" />
                </div>
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-5">
                    {[1, 2, 3].map(i => (
                        <div key={i} className="surface p-5 space-y-3">
                            <div className="h-3 bg-gray-100 rounded w-1/4" />
                            {[1, 2, 3].map(j => <div key={j} className="h-4 bg-gray-100 rounded" />)}
                        </div>
                    ))}
                </div>
                <div className="space-y-5">
                    {[1, 2].map(i => (
                        <div key={i} className="surface p-5 space-y-2">
                            <div className="h-3 bg-gray-100 rounded w-1/3" />
                            {[1, 2, 3].map(j => <div key={j} className="h-9 bg-gray-100 rounded-xl" />)}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    </div>
);

const InfoRow = ({ label, value, copyable, last, children }) => {
    const handleCopy = async () => {
        try { await navigator.clipboard.writeText(value); toast.success("Copied to clipboard"); }
        catch { toast.error("Copy failed. Select and copy the value instead."); }
    };
    return (
        <div className={`flex items-start py-2.5 ${!last ? "border-b border-gray-100" : ""}`}>
            <p className="w-1/3 text-xs text-gray-400 shrink-0 pt-0.5">{label}</p>
            <div className="flex flex-col gap-1.5 w-2/3">
                {value && (
                    <div className="flex items-center gap-2">
                        <p className="text-sm text-gray-900 break-all">{value}</p>
                        {copyable && (
                            <button aria-label={"Copy " + label} onClick={handleCopy} className="icon-action text-gray-400 hover:text-blue-500 transition-colors shrink-0 cursor-pointer">
                                <MdContentCopy size={14} />
                            </button>
                        )}
                    </div>
                )}
                {children}
            </div>
        </div>
    );
};

const SectionCard = ({ title, children }) => (
    <div className="surface p-5">
        {title && (
            <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">{title}</h2>
        )}
        {children}
    </div>
);

const NavButton = ({ icon: Icon, label, onClick, variant = "blue" }) => {
    const colors = {
        blue: "text-blue-600 bg-blue-50 hover:bg-blue-100",
        red: "text-red-500 bg-red-50 hover:bg-red-100",
        gray: "text-gray-600 bg-gray-50 hover:bg-gray-100",
    };
    return (
        <button
            onClick={onClick}
            className={`control w-full flex items-center gap-2.5 px-3 py-2.5 text-sm font-medium rounded-xl transition-colors cursor-pointer ${colors[variant]}`}
        >
            {Icon && <Icon size={17} />}
            {label}
        </button>
    );
};

const DeleteModal = ({ onClose, onConfirm, loading }) => (
    <ModalFrame title="Deactivate Account" onClose={onClose}>
        <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
            <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2 text-amber-600">
                    <MdWarning size={20} />
                    <h2 className="text-sm font-bold">Deactivate Account</h2>
                </div>
                <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
                    <MdClose size={18} />
                </button>
            </div>
            <p className="text-sm text-gray-600 mb-2 leading-relaxed">
                Your account will be <span className="font-semibold text-gray-900">deactivated immediately</span> and
                permanently deleted after <span className="font-semibold text-gray-900">7 days</span>.
            </p>
            <p className="text-xs text-gray-400 mb-2 leading-relaxed">
                You can reactivate anytime within this window by simply logging in.
            </p>
            <div className="bg-amber-50 border border-amber-100 rounded-xl px-3 py-2.5 mb-6">
                <p className="text-xs text-amber-700 font-semibold">
                    All your personal data will be permanently deleted after 7 days.
                </p>
            </div>
            <div className="flex gap-3">
                <button
                    onClick={onClose}
                    className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium transition-colors"
                >
                    Cancel
                </button>
                <button
                    onClick={onConfirm}
                    disabled={loading}
                    className="control flex-1 px-3 py-2.5 text-sm bg-amber-500 hover:bg-amber-600 text-white rounded-xl transition-colors disabled:opacity-50 cursor-pointer font-bold active:scale-[0.98]"
                >
                    {loading ? "Deactivating..." : "Deactivate"}
                </button>
            </div>
        </div>
    </ModalFrame>
);


const Profile = () => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const user = useSelector((state) => state.auth?.userDetails);
    const isAdmin = user?.roles?.includes("ROLE_ADMIN");
    const isSeller = user?.roles?.includes("ROLE_SELLER");

    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleteLoading, setDeleteLoading] = useState(false);

    if (!user) return <Skeleton />;

    const handleLogout = () => dispatch(logOutUser(navigate));


    const handleDeleteAccount = async () => {
        setDeleteLoading(true);
        try {
            await api.patch("/account/deactivate");
            dispatch(logOutUser());
            navigate("/account-deactivated");
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to deactivate account");
        } finally {
            setDeleteLoading(false);
            setShowDeleteModal(false);
        }
    };

    const initials = user.firstName
        ? `${user.firstName[0]}${user.lastName?.[0] ?? ""}`.toUpperCase()
        : user.username?.[0]?.toUpperCase() ?? "?";

    const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ") || user.username;

    return (
        <div className="bg-gray-50 min-h-screen py-6">
            <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">

                {/* Header card */}
                <div className="mb-6 surface p-5">
                    <div className="flex items-center justify-between flex-wrap gap-4">
                        <div className="flex items-center gap-4">
                            {/* Avatar */}
                            <div className="relative shrink-0">
                                <img
                                    src={user.profileImageUrl || profilePlaceholderImage}
                                    alt={user.username}
                                    className="h-16 w-16 rounded-full object-cover ring-2 ring-blue-100"
                                    onError={(e) => {
                                        if (e.target.src !== profilePlaceholderImage) {
                                            e.target.src = profilePlaceholderImage;
                                        } else {
                                            const div = document.createElement("div");
                                            div.className = "h-16 w-16 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xl font-bold ring-2 ring-blue-100";
                                            div.textContent = initials;
                                            e.target.replaceWith(div);
                                        }
                                    }}
                                />
                                <span className="absolute bottom-0.5 right-0.5 h-3 w-3 rounded-full bg-blue-400 ring-2 ring-white" />
                            </div>

                            <div>
                                <div className="flex items-center gap-2 flex-wrap">
                                    <h1 className="text-lg font-bold text-gray-900">{fullName}</h1>
                                    {user.emailVerified && (
                                        <MdVerified className="text-blue-500" size={18} title="Email verified" />
                                    )}
                                </div>
                                <p className="text-sm text-gray-500">{user.email}</p>
                                <div className="flex gap-1.5 mt-1.5 flex-wrap">
                                    {(user.roles ?? []).map(role => {
                                        const { label, color, icon: RoleIcon } = getRoleBadge(role);
                                        return (
                                            <span key={role} className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${color}`}>
                                                <RoleIcon size={11} />
                                                {label}
                                            </span>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>

                        <button
                            onClick={() => navigate("/edit-profile")}
                            className="control flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-blue-600 bg-blue-50 hover:bg-blue-100 rounded-xl transition-colors cursor-pointer"
                        >
                            <MdEdit size={16} />
                            Edit Profile
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-2 space-y-5">

                        {/* Account Information */}
                        <SectionCard title="Account Information">
                            <InfoRow label="User ID" value={user.userId} copyable />
                            <InfoRow label="Username" value={user.username} />
                            <InfoRow label="Email" value={user.email} />
                            <InfoRow label="Account Status" last={!user.sellerDisplayName}>
                                <div className="flex flex-wrap gap-1.5">
                                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${user.enabled ? "bg-blue-100 text-blue-800" : "bg-red-100 text-red-700"
                                        }`}>
                                        {user.enabled ? <MdCheck size={11} /> : <MdClose size={11} />}
                                        {user.enabled ? "Active" : "Disabled"}
                                    </span>
                                    {user.emailVerified === false && (
                                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                                            <MdClose size={11} />  Email Unverified
                                        </span>
                                    )}

                                    {user.accountLocked && (
                                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                                            <MdLock size={11} /> Account Locked
                                        </span>
                                    )}
                                </div>
                            </InfoRow>
                            {user.sellerDisplayName && (
                                <InfoRow label="Seller Name" value={user.sellerDisplayName} last />
                            )}
                        </SectionCard>

                        {/* Personal Information */}
                        <SectionCard title="Personal Information">
                            <InfoRow label="First Name" value={user.firstName} />
                            <InfoRow label="Last Name" value={user.lastName} />
                            <InfoRow label="Phone" value={user.phoneNumber} last />
                        </SectionCard>

                        {/* Activity */}
                        <SectionCard title="Activity">
                            <InfoRow label="Member Since" value={formatDate(user.createdAt)} />
                            <InfoRow label="Last Login" value={formatDate(user.lastLoginAt)} last />
                        </SectionCard>
                    </div>

                    {/* Right sidebar */}
                    <div className="space-y-5">

                        <SectionCard title="Quick Links">
                            <div className="space-y-1.5">
                                <NavButton icon={MdShoppingBag} label="My Orders" onClick={() => navigate("/orders")} />
                                <NavButton icon={MdFavorite} label="Wishlist" onClick={() => navigate("/wishlist")} />
                                <NavButton icon={MdShoppingCart} label="My Cart" onClick={() => navigate("/cart")} />
                                <NavButton icon={MdLocationOn} label="My Addresses" onClick={() => navigate("/addresses")} />
                                {isAdmin === true && (
                                    <NavButton icon={MdAdminPanelSettings} label="Admin Dashboard" onClick={() => navigate("/admin/dashboard")} variant="gray" />
                                )}
                                {isSeller === true && (
                                    <NavButton icon={MdStorefront} label="My Store" onClick={() => navigate("/seller/dashboard")} variant="gray" />
                                )}
                            </div>
                        </SectionCard>

                        <SectionCard title="Account Settings">
                            <div className="space-y-1.5">
                                <NavButton icon={MdLock} label="Change Password" onClick={() => navigate("/forgot-password")} variant="gray" />
                                <NavButton icon={MdLogout} label="Logout" onClick={handleLogout} variant="red" />
                            </div>
                        </SectionCard>

                        <SectionCard>
                            <p className="text-xs font-bold text-red-500 uppercase tracking-widest mb-2">Danger Zone</p>
                            <p className="text-xs text-gray-400 mb-3">Deactivation disables sign-in until you reactivate your account.</p>
                            <NavButton label="Deactivate Account" onClick={() => setShowDeleteModal(true)} variant="red" />
                        </SectionCard>
                    </div>
                </div>
            </div>

            {showDeleteModal && (
                <DeleteModal
                    onClose={() => setShowDeleteModal(false)}
                    onConfirm={handleDeleteAccount}
                    loading={deleteLoading}
                />
            )}
        </div>
    );
};

export default Profile;
