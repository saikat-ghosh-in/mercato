import SelectControl from "../shared/SelectControl";
import ModalFrame from "../shared/ModalFrame";
import Field from "../shared/AuthField";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  MdLocationOn, MdAdd, MdEdit, MdDelete,
  MdClose, MdWarning, MdSave, MdPerson, MdPhone,
  MdArrowBack,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { useAddressesQuery } from "../../reduxStore/services/privateApi.js";

const INDIAN_STATES = [
  "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
  "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka",
  "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
  "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
  "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
  "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
  "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry",
];


const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";

const EmptyState = ({ onAdd }) => (
  <div className="surface p-12 text-center">
    <div className="w-16 h-16 rounded-2xl bg-blue-50 flex items-center justify-center mx-auto mb-4">
      <MdLocationOn size={28} className="text-blue-300" />
    </div>
    <h2 className="text-base font-bold text-gray-800 mb-1">No addresses saved</h2>
    <p className="text-sm text-gray-400 mb-6">Add a delivery address to get started</p>
    <button
      onClick={onAdd}
      className="control inline-flex items-center gap-2 bg-gray-950 hover:bg-gray-800 text-white text-sm font-bold px-5 py-2.5 rounded-xl transition-[transform,opacity,background-color] cursor-pointer active:scale-[0.98]"
    >
      <MdAdd size={15} /> Add Address
    </button>
  </div>
);

const AddressSkeleton = () => (
  <div className="surface p-5 animate-pulse space-y-3">
    <div className="h-4 bg-gray-100 rounded w-1/3" />
    <div className="h-3 bg-gray-100 rounded w-full" />
    <div className="h-3 bg-gray-100 rounded w-2/3" />
    <div className="h-3 bg-gray-100 rounded w-1/4 mt-2" />
  </div>
);

const DeleteModal = ({ address, onClose, onConfirm, loading }) => (
  <ModalFrame title="Delete Address" onClose={onClose}>
    <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2 text-red-600">
          <MdWarning size={20} />
          <h2 className="text-sm font-bold">Delete Address</h2>
        </div>
        <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
          <MdClose size={18} />
        </button>
      </div>
      <p className="text-sm text-gray-600 mb-1">
        Delete address for <span className="font-semibold text-gray-900">{address.recipientName}</span>?
      </p>
      <p className="text-xs text-gray-400 mb-6">{address.addressLine1}, {address.city}</p>
      <div className="flex gap-3">
        <button  onClick={onClose}
          className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium transition-colors">
          Cancel
        </button>
        <button onClick={onConfirm} disabled={loading}
          className="control flex-1 px-3 py-2.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98] transition-[transform,opacity,background-color]">
          {loading ? "Deleting..." : "Delete"}
        </button>
      </div>
    </div>
  </ModalFrame>
);

const AddressFormModal = ({ address, onClose, onSave }) => {
  const isEdit = !!address;
  const [form, setForm] = useState({
    recipientName: address?.recipientName ?? "",
    recipientPhone: address?.recipientPhone ?? "",
    addressLine1: address?.addressLine1 ?? "",
    addressLine2: address?.addressLine2 ?? "",
    city: address?.city ?? "",
    state: address?.state ?? "",
    pincode: address?.pincode ?? "",
    country: address?.country ?? "India",
  });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const set = (key, val) => {
    setForm(f => ({ ...f, [key]: val }));
    if (errors[key]) setErrors(e => ({ ...e, [key]: "" }));
  };

  const validate = () => {
    const e = {};
    if (!form.recipientName.trim()) e.recipientName = "Recipient name is required";
    if (!form.recipientPhone.trim()) e.recipientPhone = "Phone number is required";
    else if (!/^[0-9]{10}$/.test(form.recipientPhone)) e.recipientPhone = "Invalid phone number";
    if (!form.addressLine1.trim()) e.addressLine1 = "Address line 1 is required";
    if (!form.city.trim()) e.city = "City is required";
    if (!form.state.trim()) e.state = "State is required";
    if (!form.pincode.trim()) e.pincode = "Pincode is required";
    else if (!/^[0-9]{6}$/.test(form.pincode)) e.pincode = "Must be 6 digits";
    return e;
  };

  const handleSave = async () => {
    const e = validate();
    if (Object.keys(e).length > 0) { setErrors(e); return; }
    setSaving(true);
    try {
      if (isEdit) {
        await api.put(`/user/addresses/${address.addressId}`, form);
        toast.success("Address updated");
      } else {
        await api.post("/user/addresses/add", form);
        toast.success("Address added");
      }
      onSave();
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to save address");
    } finally {
      setSaving(false);
    }
  };

  return (
    <ModalFrame title="Manage details" onClose={onClose}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[90vh] flex flex-col">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100 shrink-0">
          <h2 className="text-sm font-bold text-gray-800">
            {isEdit ? "Edit Address" : "Add New Address"}
          </h2>
          <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
            <MdClose size={18} />
          </button>
        </div>

        {/* Form */}
        <div className="overflow-y-auto flex-1 px-5 py-4 space-y-4">

          {/* Recipient */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Recipient Name" required error={errors.recipientName}>
              <MdPerson size={15} className="text-gray-400 shrink-0" />
              <input
                type="text"
                value={form.recipientName}
                onChange={e => set("recipientName", e.target.value)}
                placeholder="Full name"
                className={inputCls}
              />
            </Field>
            <Field label="Phone Number" required error={errors.recipientPhone}>
              <MdPhone size={15} className="text-gray-400 shrink-0" />
              <span className="text-sm font-semibold text-gray-500 shrink-0 select-none">+91</span>
              <div className="w-px h-4 bg-gray-200 shrink-0" />
              <input
                type="tel"
                value={form.recipientPhone}
                onChange={e => set("recipientPhone", e.target.value.replace(/[^0-9]/g, "").slice(0, 10))}
                placeholder="9876543210"
                maxLength={10}
                className={inputCls}
              />
            </Field>
          </div>

          {/* Address lines */}
          <Field label="Address Line 1" required error={errors.addressLine1}>
            <input
              type="text"
              value={form.addressLine1}
              onChange={e => set("addressLine1", e.target.value)}
              placeholder="House, street"
              className={inputCls}
            />
          </Field>
          <Field label="Address Line 2" error={errors.addressLine2}>
            <input
              type="text"
              value={form.addressLine2}
              onChange={e => set("addressLine2", e.target.value)}
              placeholder="Area or landmark"
              className={inputCls}
            />
          </Field>

          {/* City, State, Pincode */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <Field label="City" required error={errors.city}>
              <input
                type="text"
                value={form.city}
                onChange={e => set("city", e.target.value)}
                placeholder="Mumbai"
                className={inputCls}
              />
            </Field>
            <Field label="Pincode" required error={errors.pincode}>
              <input
                type="text"
                value={form.pincode}
                onChange={e => set("pincode", e.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
                placeholder="400001"
                maxLength={6}
                className={inputCls}
              />
            </Field>
            <div className="col-span-2 sm:col-span-1">
              <Field label="State" required error={errors.state}>
                <SelectControl
                  value={form.state}
                  onChange={e => set("state", e.target.value)}
                  className="w-full text-sm text-gray-800 focus:outline-none bg-transparent"
                >
                  <option value="">Select state</option>
                  {INDIAN_STATES.map(s => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </SelectControl>
              </Field>
            </div>
          </div>

        </div>

        {/* Footer */}
        <div className="flex gap-3 px-5 py-4 border-t border-gray-100 shrink-0">
          <button  onClick={onClose}
            className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium transition-colors">
            Cancel
          </button>
          <button onClick={handleSave} disabled={saving}
            className="control flex-1 flex items-center justify-center gap-2 px-3 py-2.5 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98] transition-[transform,opacity,background-color]">
            <MdSave size={14} />
            {saving ? "Saving..." : isEdit ? "Save Changes" : "Add Address"}
          </button>
        </div>
      </div>
    </ModalFrame>
  );
};

const AddressCard = ({ address, onEdit, onDelete, onDefault, defaulting }) => (
  <div className="surface p-5 hover:shadow-md transition-shadow">
    <div className="flex items-start justify-between gap-3">
      <div className="flex items-start gap-3 min-w-0">
        <div className="p-2 bg-blue-50 rounded-xl shrink-0 mt-0.5">
          <MdLocationOn size={16} className="text-blue-500" />
        </div>
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <p className="text-sm font-bold text-gray-900">{address.recipientName}</p>
            <span className="text-[11px] text-gray-400 flex items-center gap-1">
              <MdPhone size={10} /> +91 {address.recipientPhone}
            </span>
          </div>
          <p className="text-xs text-gray-600 leading-relaxed">
            {address.addressLine1}
            {address.addressLine2 && `, ${address.addressLine2}`}
          </p>
          <p className="text-xs text-gray-600">
            {address.city}, {address.state} — {address.pincode}
          </p>

          {address.defaultAddress ? <span className="status-tag mt-3">Default address</span> : <button className="button button-outline mt-3" disabled={defaulting} onClick={() => onDefault(address)}>Make default</button>}
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1 shrink-0">
        <button
          onClick={() => onEdit(address)}
          className="p-2 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded-xl transition-colors cursor-pointer"
          title="Edit"
        >
          <MdEdit size={16} />
        </button>
        <button
          onClick={() => onDelete(address)}
          className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-xl transition-colors cursor-pointer"
          title="Delete"
        >
          <MdDelete size={16} />
        </button>
      </div>
    </div>
  </div>
);


const Addresses = () => {
  const navigate = useNavigate();
  const { data: addresses = [], isLoading: loading, error: addressError, refetch: fetchAddresses } = useAddressesQuery();
  const [showForm, setShowForm] = useState(false);
  const [editAddress, setEditAddress] = useState(null);
  const [deleteAddress, setDeleteAddress] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [defaulting, setDefaulting] = useState(false);

  const handleDefault = async address => {
    setDefaulting(true);
    try { await api.patch(`/user/addresses/${address.addressId}/default`); await fetchAddresses(); toast.success("Default address updated"); }
    catch (error) { toast.error(error?.response?.data?.message || "Couldn’t change the default address"); }
    finally { setDefaulting(false); }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await api.delete(`/user/addresses/${deleteAddress.addressId}`);
      toast.success("Address deleted");
      setDeleteAddress(null);
      fetchAddresses();
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to delete address");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-2xl mx-auto px-4 sm:px-6">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-blue-50 rounded-xl">
            <MdLocationOn size={20} className="text-blue-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">My Addresses</h1>
            {!loading && (
              <p className="text-xs text-gray-500 mt-0.5">
                {addresses.length} address{addresses.length !== 1 ? "es" : ""} saved
              </p>
            )}
          </div>
          <div className="ml-auto flex items-center gap-2">
            <button
              onClick={() => navigate("/profile")}
              className="control flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer"
            >
              <MdArrowBack size={13} /> Back
            </button>
            {addresses.length > 0 && (
              <button
                onClick={() => { setEditAddress(null); setShowForm(true); }}
                className="control flex items-center gap-1.5 text-xs font-bold bg-gray-950 hover:bg-gray-800 text-white px-3 py-1.5 rounded-xl transition-[transform,opacity,background-color] cursor-pointer active:scale-[0.98]"
              >
                <MdAdd size={14} /> Add New
              </button>
            )}
          </div>
        </div>

        {/* Content */}
        {loading && !addresses.length ? (
          <div className="space-y-4">
            {[1, 2].map(i => <AddressSkeleton key={i} />)}
          </div>
        ) : addressError ? <div className="state-panel" role="alert"><p>Your addresses couldn’t load.</p><button className="button button-dark" onClick={fetchAddresses}>Try again</button></div> : addresses.length === 0 ? (
          <EmptyState onAdd={() => setShowForm(true)} />
        ) : (
          <div className="space-y-4">
            {addresses.map(a => (
              <AddressCard
                key={a.addressId}
                address={a}
                onEdit={addr => { setEditAddress(addr); setShowForm(true); }}
                onDelete={setDeleteAddress}
                onDefault={handleDefault}
                defaulting={defaulting}
              />
            ))}
          </div>
        )}
      </div>

      {/* Add/Edit modal */}
      {showForm && (
        <AddressFormModal
          address={editAddress}
          onClose={() => { setShowForm(false); setEditAddress(null); }}
          onSave={() => { setShowForm(false); setEditAddress(null); fetchAddresses(); }}
        />
      )}

      {/* Delete modal */}
      {deleteAddress && (
        <DeleteModal
          address={deleteAddress}
          onClose={() => setDeleteAddress(null)}
          onConfirm={handleDelete}
          loading={deleting}
        />
      )}
    </div>
  );
};

export default Addresses;
