import { LoadingLayout } from "../shared/LoadingStates";
import PageHeading from "../shared/PageHeading";
import Field from "../shared/AuthField";
import { useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
  MdArrowBack, MdPerson, MdPhone,
  MdStorefront, MdVerified,
} from "react-icons/md";
import toast from "../../utils/toastUtils.js";
import api from "../../backend/api";
import { fetchUserDetails } from "../../reduxStore/actions/authActions";
import { profileDraft } from "../../utils/accountState.js";


const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";

const ReadOnlyField = ({ label, value, hint }) => (
  <div>
    <label className="text-xs font-bold text-gray-400  block mb-1.5">{label}</label>
    <div className="flex items-center gap-2.5 border border-gray-100 rounded-xl px-3 py-2.5 bg-gray-50">
      <span className="text-sm text-gray-600 select-none break-words min-w-0">{value ?? "—"}</span>
    </div>
    {hint && <p className="text-xs text-gray-400 mt-1">{hint}</p>}
  </div>
);


const ProfileEdit = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const user = useSelector(s => s.auth?.userDetails);

  const isSeller = user?.roles?.some(role => ["ROLE_SELLER", "ROLE_ADMIN"].includes(role));
  const [draft, setDraft] = useState(null);
  const initializedDraft = profileDraft(draft, user);
  const form = initializedDraft?.form;
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  if (!form) return <div className="site-container skeleton-page"><LoadingLayout variant="profile" label="Loading your profile" /></div>;

  const set = (key, val) => {
    setDraft({ ...initializedDraft, dirty: true, form: { ...form, [key]: val } });
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
        <PageHeading title="Edit profile" description="Update your personal information."><button type="button" className="button button-outline" onClick={() => navigate("/profile")}><MdArrowBack aria-hidden="true" /> Back to account</button></PageHeading>

        <div className="space-y-4">
          {/* Personal info */}
          <div className="surface p-5 space-y-4">
            <h3 className="text-xs font-bold text-gray-400 ">Personal information</h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="First name" icon={MdPerson} error={errors.firstName}>
                <input
                  type="text"
                  value={form.firstName}
                  onChange={e => set("firstName", e.target.value)}
                  placeholder="John"
                  className={inputCls}
                />
              </Field>
              <Field label="Last name" error={errors.lastName}>
                <input
                  type="text"
                  value={form.lastName}
                  onChange={e => set("lastName", e.target.value)}
                  placeholder="Doe"
                  className={inputCls}
                />
              </Field>
            </div>

            <Field label="Phone number" icon={MdPhone} error={errors.phoneNumber} hint="10-digit valid mobile number">
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
              <h3 className="text-xs font-bold text-gray-400 ">Account details</h3>
              <span className="text-xs font-semibold text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">Read only</span>
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
              <h3 className="text-xs font-bold text-gray-400  flex items-center gap-1.5">
                <MdStorefront size={13} /> Seller information
              </h3>
              <Field label="Store display name" icon={MdStorefront} error={errors.sellerDisplayName} hint="This name appears on your products">
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
              className="control control-prominent flex-1 px-4 py-3 text-sm border border-gray-200 text-gray-700 rounded-xl
              hover:bg-gray-100 transition-colors cursor-pointer font-medium"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="control control-prominent flex-1 flex items-center justify-center gap-2 px-4 py-3 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl transition-[transform,opacity,background-color] cursor-pointer font-bold  disabled:opacity-50"
            >

              {saving ? "Saving..." : "Save changes"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProfileEdit;
