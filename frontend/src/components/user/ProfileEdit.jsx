import Field from "../shared/AuthField";
import { useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
  MdArrowBack, MdSave, MdPerson, MdPhone,
  MdStorefront, MdImage, MdVerified,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { fetchUserDetails } from "../../reduxStore/actions/authActions";


const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";

const ReadOnlyField = ({ label, value, hint }) => (
  <div>
    <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1.5">{label}</label>
    <div className="flex items-center gap-2.5 border border-gray-100 rounded-xl px-3 py-2.5 bg-gray-50">
      <span className="text-sm text-gray-400 select-none">{value ?? "—"}</span>
    </div>
    {hint && <p className="text-[11px] text-gray-400 mt-1">{hint}</p>}
  </div>
);


const ProfileEdit = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const user = useSelector(s => s.auth?.userDetails);

  const isSeller = user?.roles?.some(role => ["ROLE_SELLER", "ROLE_ADMIN"].includes(role));
  const [form, setForm] = useState({
    firstName: user?.firstName ?? "",
    lastName: user?.lastName ?? "",
    phoneNumber: user?.phoneNumber ?? "",
    profileImageUrl: user?.profileImageUrl ?? "",
    sellerDisplayName: user?.sellerDisplayName ?? "",
  });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const set = (key, val) => {
    setForm(f => ({ ...f, [key]: val }));
    if (errors[key]) setErrors(e => ({ ...e, [key]: "" }));
  };

  const validate = () => {
    const e = {};
    if (form.phoneNumber && !/^[0-9]{10}$/.test(form.phoneNumber)) {
      e.phoneNumber = "Invalid phone number";
    }
    if (form.firstName && form.firstName.length > 50) {
      e.firstName = "Max 50 characters";
    }
    if (form.lastName && form.lastName.length > 50) {
      e.lastName = "Max 50 characters";
    }
    if (form.sellerDisplayName && form.sellerDisplayName.length > 100) {
      e.sellerDisplayName = "Max 100 characters";
    }
    return e;
  };

  const handleSave = async () => {
    const e = validate();
    if (Object.keys(e).length > 0) { setErrors(e); return; }

    setSaving(true);
    try {
      const payload = {
        firstName: form.firstName,
        lastName: form.lastName,
        phoneNumber: form.phoneNumber || null,
        profileImageUrl: form.profileImageUrl || null,
        sellerDisplayName: isSeller ? form.sellerDisplayName : undefined,
      };
      await api.put("/user/edit-profile", payload);
      await dispatch(fetchUserDetails());
      toast.success("Profile updated");
      navigate("/profile");
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to update profile");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-2xl mx-auto px-4 sm:px-6">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-blue-50 rounded-xl">
            <MdPerson size={20} className="text-blue-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">Edit Profile</h1>
            <p className="text-xs text-gray-500 mt-0.5">Update your personal information</p>
          </div>
          <button
            onClick={() => navigate("/profile")}
            className="ml-auto flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer"
          >
            <MdArrowBack size={13} /> Back
          </button>
        </div>

        <div className="space-y-4">

          {/* Avatar preview */}
          {/* <div className="surface p-5">
            <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-4">Profile Picture</h3>
            <div className="flex items-center gap-5">
              <div className="shrink-0">
                {!previewError && form.profileImageUrl ? (
                  <img
                    src={form.profileImageUrl}
                    alt="Preview"
                    onError={() => setPreviewError(true)}
                    className="w-16 h-16 rounded-full object-cover ring-2 ring-blue-100"
                  />
                ) : (
                  <div className="w-16 h-16 rounded-full bg-linear-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xl font-bold ring-2 ring-blue-100">
                    {initials}
                  </div>
                )}
              </div>
              <div className="flex-1">
                <Field label="Image URL" icon={MdImage} error={errors.profileImageUrl}>
                  <input
                    type="text"
                    value={form.profileImageUrl}
                    onChange={e => { set("profileImageUrl", e.target.value); setPreviewError(false); }}
                    placeholder="https://example.com/photo.jpg"
                    className={inputCls}
                  />
                </Field>
                <p className="text-[11px] text-gray-400 mt-1">Paste a direct image URL. Leave empty to use your initials.</p>
              </div>
            </div>
          </div> */}

          {/* Personal info */}
          <div className="surface p-5 space-y-4">
            <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest">Personal Information</h3>

            <div className="grid grid-cols-2 gap-3">
              <Field label="First Name" icon={MdPerson} error={errors.firstName}>
                <input
                  type="text"
                  value={form.firstName}
                  onChange={e => set("firstName", e.target.value)}
                  placeholder="John"
                  className={inputCls}
                />
              </Field>
              <Field label="Last Name" error={errors.lastName}>
                <input
                  type="text"
                  value={form.lastName}
                  onChange={e => set("lastName", e.target.value)}
                  placeholder="Doe"
                  className={inputCls}
                />
              </Field>
            </div>

            <Field label="Phone Number" icon={MdPhone} error={errors.phoneNumber} hint="10-digit valid mobile number">
              <span className="text-sm font-semibold text-gray-500 shrink-0 select-none">+91</span>
              <div className="w-px h-4 bg-gray-200 shrink-0" />
              <input
                type="tel"
                value={form.phoneNumber}
                onChange={e => set("phoneNumber", e.target.value.replace(/[^0-9]/g, "").slice(0, 10))}
                placeholder="9876543210"
                maxLength={10}
                className={inputCls}
              />
            </Field>
          </div>

          {/* Locked fields */}
          <div className="surface p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest">Account Details</h3>
              <span className="text-[10px] font-semibold text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">Read only</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <ReadOnlyField label="Username" value={user?.username} hint="Cannot be changed" />
              <ReadOnlyField label="Email" value={user?.email} hint="Contact support to change" />
            </div>
            <div className="flex items-center gap-2 bg-gray-50 rounded-xl px-3 py-2.5">
              {user?.emailVerified
                ? <><MdVerified size={14} className="text-blue-500 shrink-0" /><span className="text-xs text-gray-600 font-medium">Email verified</span></>
                : <><span className="w-3.5 h-3.5 rounded-full border border-amber-400 shrink-0" /><span className="text-xs text-amber-600 font-medium">Email not verified</span></>
              }
            </div>
          </div>

          {/* Seller section */}
          {isSeller && (
            <div className="surface p-5 space-y-4">
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest flex items-center gap-1.5">
                <MdStorefront size={13} /> Seller Information
              </h3>
              <Field label="Store Display Name" icon={MdStorefront} error={errors.sellerDisplayName} hint="This name appears on your products">
                <input
                  type="text"
                  value={form.sellerDisplayName}
                  onChange={e => set("sellerDisplayName", e.target.value)}
                  placeholder="Your Store Name"
                  className={inputCls}
                />
              </Field>
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3">
            <button
              onClick={() => navigate("/profile")}
              className="flex-1 px-4 py-3 text-sm border border-gray-200 text-gray-700 rounded-xl
              hover:bg-gray-100 transition-colors cursor-pointer font-medium"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-3 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl transition-[transform,opacity,background-color] cursor-pointer font-bold active:scale-[0.98] disabled:opacity-50"
            >
              <MdSave size={15} />
              {saving ? "Saving..." : "Save Changes"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProfileEdit;
