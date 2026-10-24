import ModalFrame from "./ModalFrame";
import { useState } from "react";
import { Combobox, ComboboxInput, ComboboxOptions, ComboboxOption, ComboboxButton } from "@headlessui/react";
import { MdAdd, MdClose, MdInventory } from "react-icons/md";
import { FiMinus, FiPlus, FiChevronDown } from "react-icons/fi";
import toast from "../../utils/toastUtils.js";
import api from "../../backend/api";

const ProductCombobox = ({ products = [], value, onChange, placeholder = "Product name or ID", showAvailability = false }) => {
  const [query, setQuery] = useState("");
  const selected = products.find(p => p.productId === value);
  const filtered = products.filter(p => !query.trim() || (p.productName + " " + p.productId).toLowerCase().includes(query.toLowerCase()));
  return <Combobox value={value} onChange={id => { onChange(id); setQuery(""); }} onClose={() => setQuery("")}>
    <div className="supply-product-picker"><ComboboxInput aria-label="Choose product" title={selected?.productName} displayValue={() => selected?.productName ?? ""} onChange={e => setQuery(e.target.value)} placeholder={placeholder} /><ComboboxButton aria-label="Show products"><FiChevronDown aria-hidden="true" /></ComboboxButton></div>
    <ComboboxOptions transition anchor="bottom start" className="supply-options">{filtered.length ? filtered.map(p => <ComboboxOption key={p.productId} value={p.productId} className="supply-option"><p>{p.productName}</p><small>{p.productId}{showAvailability ? " · " + p.availableQty + " available" : ""}</small></ComboboxOption>) : <p className="p-4">No products found</p>}</ComboboxOptions>
  </Combobox>;
};

const SupplyQtyStepper = ({ value, onChange, min, supplyType }) => {
    const isDelta = supplyType === "DELTA";
    const qty = parseInt(value) || 0;
    const isNegative = isDelta && qty < 0;
    const isPositive = isDelta && qty > 0;

    const decrement = () => {
        const next = qty - 1;
        if (next < min) return;
        onChange(String(next));
    };

    const increment = () => {
        onChange(String(qty + 1));
    };

    const handleInput = (e) => {
        const raw = e.target.value;
        if (raw === "" || raw === "-") { onChange(raw); return; }
        const parsed = parseInt(raw);
        if (isNaN(parsed)) return;
        if (parsed < min) return;
        onChange(String(parsed));
    };

    const borderColor = isNegative
        ? "border-red-200 focus-within:ring-red-200"
        : isPositive
            ? "border-blue-200 focus-within:ring-blue-200"
            : "border-gray-200 focus-within:ring-blue-200";

    const textColor = isNegative ? "text-red-600" : isPositive ? "text-blue-700" : "text-gray-700";

    return (
        <div className={`flex items-center border rounded-xl overflow-hidden focus-within:ring-1 transition-colors ${borderColor}`}>
            <button
                aria-label="Decrease stock quantity" onClick={decrement}
                disabled={qty <= min}
                className="icon-action w-8 h-9 flex items-center justify-center text-gray-400 hover:bg-gray-50 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-gray-400 transition-colors shrink-0"
            >
                <FiMinus size={12} />
            </button>
            <input
                type="text"
                aria-label="Stock quantity" inputMode="numeric"
                value={value}
                onChange={handleInput}
                className={`w-full text-center text-xs font-bold focus:outline-none bg-transparent py-2 ${textColor}`}
            />
            <button
                aria-label="Increase stock quantity" onClick={increment}
                className="icon-action w-8 h-9 flex items-center justify-center text-gray-400 hover:bg-gray-50 hover:text-gray-700 transition-colors shrink-0"
            >
                <FiPlus size={12} />
            </button>
        </div>
    );
};


const SupplyUpdateModal = ({ products, onClose, onSave }) => {
    const [supplyType, setSupplyType] = useState("DELTA");
    const [rows, setRows] = useState([{ productId: "", quantity: "" }]);
    const [saving, setSaving] = useState(false);

    const isDelta = supplyType === "DELTA";

    const addRow = () => setRows(r => [...r, { productId: "", quantity: "" }]);
    const removeRow = (i) => setRows(r => r.filter((_, idx) => idx !== i));
    const updateRow = (i, key, val) =>
        setRows(r => r.map((row, idx) => idx === i ? { ...row, [key]: val } : row));

    const handleTypeChange = (type) => {
        setSupplyType(type);
        setRows(r => r.map(row => ({ ...row, quantity: "" })));
    };

    const getMin = (row) => {
        if (!isDelta) return 0;
        const product = products.find(p => p.productId === row.productId);
        return product ? -product.physicalQty : -Infinity;
    };

    const handleSave = async () => {
        const valid = rows.filter(r => r.productId && r.quantity !== "" && !isNaN(parseInt(r.quantity)));
        if (valid.length === 0) { toast.error("Add at least one valid row"); return; }
        setSaving(true);
        try {
            const { data } = await api.post("/seller/products/supplyUpdate",
                valid.map(r => ({
                    productId: r.productId,
                    supplyType,
                    quantity: parseInt(r.quantity),
                }))
            );
            const errors = data.filter(r => r.error);
            const success = data.filter(r => !r.error);
            if (errors.length) toast.error(`${errors.length} stock update${errors.length === 1 ? "" : "s"} failed. ${errors[0].productId}: ${errors[0].errorMessage}`);
            if (success.length > 0)
                toast.success(`Updated ${success.length} product${success.length !== 1 ? "s" : ""}`);
            if (errors.length === 0) onSave();
        } catch (err) {
            toast.error(err?.response?.data?.message || "Supply update failed");
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalFrame title="Supply Update" onClose={onClose}>
            <div className="supply-update bg-white rounded-2xl shadow-xl w-full max-w-xl max-h-[90vh] flex flex-col">

                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100 shrink-0">
                    <div className="flex items-center gap-2.5">
                        <div className="p-2 bg-blue-50 rounded-xl">
                            <MdInventory size={16} className="text-blue-500" />
                        </div>
                        <div>
                            <h2 className="text-sm font-bold text-gray-800">Supply Update</h2>
                            <p className="text-xs text-gray-400 mt-0.5">Adjust inventory levels</p>
                        </div>
                    </div>
                    <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
                        <MdClose size={18} />
                    </button>
                </div>

                {/* Global type toggle */}
                <div className="px-5 py-3.5 border-b border-gray-100 shrink-0">
                    <p className="text-xs font-bold text-gray-400  mb-2">
                        Update Mode — applies to all rows
                    </p>
                    <div className="flex gap-2">
                        {[
                            { value: "DELTA", label: "Add or remove", desc: "Add or subtract from current stock" },
                            { value: "ABSOLUTE", label: "Set quantity", desc: "Set to an exact quantity" },
                        ].map(t => (
                            <button
                                key={t.value}
                                onClick={() => handleTypeChange(t.value)}
                                className={`supply-mode-option flex-1 px-4 py-2.5 rounded-xl border text-left transition-[transform,opacity,background-color] cursor-pointer ${supplyType === t.value
                                    ? "bg-gray-950 border-gray-950 text-white"
                                    : "border-gray-200 text-gray-600 hover:bg-gray-50"
                                    }`}
                            >
                                <p className={`text-xs font-bold ${supplyType === t.value ? "text-white" : "text-gray-800"}`}>
                                    {t.label}
                                </p>
                                <p className={`text-xs mt-0.5 ${supplyType === t.value ? "text-gray-300" : "text-gray-400"}`}>
                                    {t.desc}
                                </p>
                            </button>
                        ))}
                    </div>
                    {isDelta && (
                        <p className="text-xs text-blue-500 mt-2 font-medium">
                            Use negative values to reduce stock (e.g. -5 removes 5 units).
                        </p>
                    )}
                </div>

                {/* Column headers */}
                <div className="supply-column-headings px-5 pt-4 pb-1 shrink-0">
                    <p className="text-xs font-bold text-gray-400 ">Product</p>
                    <p className="text-xs font-bold text-gray-400 ">
                        {isDelta ? "Qty (±)" : "Set Qty"}
                    </p>
                    <p />
                </div>

                {/* Rows */}
                <div className="overflow-y-auto flex-1 px-5 pb-3 space-y-2.5 pt-1">
                    {rows.map((row, i) => {
                        const min = getMin(row);
                        return (
                            <div key={i} className="supply-entry">
                                <ProductCombobox
                                    products={products}
                                    value={row.productId}
                                    onChange={val => updateRow(i, "productId", val)}
                                    showAvailability
                                />
                                <SupplyQtyStepper
                                    value={row.quantity}
                                    onChange={val => updateRow(i, "quantity", val)}
                                    min={min}
                                    supplyType={supplyType}
                                />
                                <button
                                    aria-label="Remove supply row" onClick={() => removeRow(i)}
                                    disabled={rows.length === 1}
                                    className="flex items-center justify-center text-gray-300 hover:text-red-500 transition-colors cursor-pointer disabled:opacity-20 disabled:cursor-not-allowed"
                                >
                                    <MdClose size={15} />
                                </button>
                            </div>
                        );
                    })}

                    <button
                        onClick={addRow}
                        className="flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 cursor-pointer mt-1 transition-colors"
                    >
                        <MdAdd size={14} /> Add Product
                    </button>
                </div>

                {/* Footer */}
                <div className="flex gap-3 px-5 py-4 border-t border-gray-100 shrink-0">
                    <button
                        onClick={onClose}
                        className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors cursor-pointer font-medium"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="control flex-1 px-3 py-2.5 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50  flex items-center justify-center gap-2 transition-[transform,opacity,background-color]"
                    >

                        {saving ? "Updating..." : "Apply updates"}
                    </button>
                </div>
            </div>
        </ModalFrame>
    );
};

export default SupplyUpdateModal;
