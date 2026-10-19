import { useState } from "react";
import api from "../../backend/api";
import ModalFrame from "./ModalFrame";
import SelectControl from "./SelectControl";
import RichProductFields from "./RichProductFields";
import ProductImageUpload from "./ProductImageUpload";
import { detailsForForm, detailsPayload, packagePayload } from "../../utils/productDetails";
import { formatCurrency as money } from "../../utils/formatCurrency";

export default function ProductSubmissionModal({ product, categories, onClose, onSave, admin }) {
  const [step, setStep] = useState(0);
  const [form, setForm] = useState({ ...detailsForForm(product), productName: product?.productName || "", description: product?.description || "", retailPrice: product?.retailPrice || "", discountPercent: product?.discountPercent || 0, primaryImageUrl: product?.primaryImageUrl || "", secondaryImageUrls: product?.secondaryImageUrls || [], active: product?.active ?? true, categoryId: product?.categoryId || "" });
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const set = (key, value) => { setForm(f => ({ ...f, [key]: value })); setError(""); };
  const validate = index => {
    if (index === 0 && (!form.categoryId || form.productName.trim().length < 4 || form.description.trim().length < 7 || !(Number(form.retailPrice) > 0) || Number(form.discountPercent) < 0 || Number(form.discountPercent) > 99.99)) return "Choose a category, add a name (4+ characters), description (7+ characters), valid price and discount from 0–99.99%.";
    if (index === 1 && !form.primaryImageUrl) return "Add a primary product image before reviewing your submission.";
    if (index === 1) { try { packagePayload(form, !product || product.weightKg != null); detailsPayload(form); } catch (err) { return err.message; } }
    return "";
  };
  const next = () => { const message = validate(step); if (message) setError(message); else { setError(""); setStep(s => s + 1); } };
  const submit = async () => {
    const invalid = validate(0) || validate(1); if (invalid) { setError(invalid); return; }
    setBusy(true); setError("");
    try { const payload = { ...detailsPayload(form), productName: form.productName.trim(), description: form.description.trim(), retailPrice: Number(form.retailPrice), discountPercent: Number(form.discountPercent), primaryImageUrl: form.primaryImageUrl, secondaryImageUrls: form.secondaryImageUrls, active: form.active }; if (product) await api.put(`/seller/products/${product.productId}`, payload, { params: form.categoryId !== product.categoryId ? { categoryId: form.categoryId } : {} }); else await api.post(`/seller/categories/${form.categoryId}/product`, payload); onSave(); }
    catch (err) { setError(err.response?.data?.message || "Submission failed. Your details are retained; please retry."); }
    finally { setBusy(false); }
  };
  return <ModalFrame title={product ? "Update product listing" : "Request a product listing"} onClose={() => { if (!busy && !uploading) onClose(); }}><div className="product-submission"><header><div><p className="eyebrow">FREE PRODUCT LISTING</p><h2>{product ? "Make your product shine." : "Bring your next good find."}</h2></div><button className="icon-button" aria-label="Close product submission" disabled={busy || uploading} onClick={onClose}>×</button></header><ol className="submission-steps">{["The essentials", "Product story", "Review & request"].map((label, i) => <li key={label} aria-current={step === i ? "step" : undefined}><span>{i + 1}</span>{label}</li>)}</ol>
    {error && <div role="alert" className="inline-error submission-error">{error}</div>}<div className="submission-body">
      {step === 0 && <div className="submission-fields"><p>Start with the details shoppers need to understand your product.</p><label>Category<SelectControl value={form.categoryId} onChange={e => set("categoryId", e.target.value)}><option value="">Select a category</option>{categories.map(c => <option value={c.categoryId} key={c.categoryId}>{c.categoryName}</option>)}</SelectControl></label><label>Product name<input value={form.productName} maxLength={100} onChange={e => set("productName", e.target.value)} /></label><label>Description<textarea rows={4} value={form.description} onChange={e => set("description", e.target.value)} /></label><div className="grid grid-cols-2 gap-4"><label>Retail price (₹)<input type="number" min="0.01" step="0.01" value={form.retailPrice} onChange={e => set("retailPrice", e.target.value)} /></label><label>Discount (%)<input type="number" min="0" max="99.99" step="0.01" value={form.discountPercent} onChange={e => set("discountPercent", e.target.value)} /></label></div><div className="submission-price"><span>Customer price</span><strong>{money(Number(form.retailPrice || 0) * (1 - Number(form.discountPercent || 0) / 100))}</strong></div></div>}
      {step === 1 && <div className="space-y-5"><p>Add useful highlights, materials, care and specifications. Accurate information builds trust.</p><RichProductFields form={form} set={set} /><ProductImageUpload onBusyChange={setUploading} primaryImage={form.primaryImageUrl} secondaryImages={form.secondaryImageUrls} onPrimaryChange={url => set("primaryImageUrl", url)} onSecondaryChange={urls => set("secondaryImageUrls", urls)} /></div>}
      {step === 2 && <div className="submission-review">{form.primaryImageUrl && <img src={form.primaryImageUrl} alt={form.productName} width="180" height="180" />}<div><p className="eyebrow">{categories.find(c => c.categoryId === form.categoryId)?.categoryName}</p><h3>{form.productName}</h3><strong>{money(Number(form.retailPrice) * (1 - Number(form.discountPercent) / 100))}</strong><p>{form.description}</p><dl>{form.weightKg && <><div><dt>Packed weight</dt><dd>{form.weightKg} kg</dd></div><div><dt>Package dimensions</dt><dd>{form.lengthCm} × {form.widthCm} × {form.heightCm} cm</dd></div></>}<div><dt>Listing submission fee</dt><dd>₹0</dd></div><div><dt>Review</dt><dd>{admin && product ? "Admin catalog update" : "Admin approval required"}</dd></div><div><dt>Stock</dt><dd>Managed in Perpetual Inventory</dd></div></dl><p>{admin && product ? "Your admin update preserves the current approval state." : "This submission stays hidden until approved. Updating a live listing sends it back for review. Approval does not add stock or book a paid promotion."}</p><label className="flex gap-2 items-center"><input type="checkbox" checked={form.active} onChange={e => set("active", e.target.checked)} />Enable this listing after approval</label></div></div>}
    </div><footer><button className="button button-outline" disabled={busy || uploading} onClick={() => step ? setStep(s => s - 1) : onClose()}>{step ? "Back" : "Cancel"}</button><span>Step {step + 1} of 3</span>{step < 2 ? <button className="button button-dark" disabled={uploading} onClick={next}>Continue</button> : <button className="button button-dark" disabled={busy || uploading} onClick={submit}>{busy ? "Submitting…" : admin && product ? "Save catalog update" : "Request approval · ₹0"}</button>}</footer></div></ModalFrame>;
}
