import { useCallback, useEffect, useState } from "react";
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdClose,
  MdWarning, MdInventory,
  MdSave,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import SupplyUpdateModal from "./SupplyUpdateModal";
import ProductImageUpload from "./ProductImageUpload";

const SUBTABS = [
  { id: "pim", label: "Product Catalogue" },
  { id: "inventory", label: "Perpetual Inventory" },
];

const TableSkeleton = ({ cols = 5 }) => (
  <div className="animate-pulse space-y-2">
    {[1, 2, 3, 4].map(i => (
      <div key={i} className="flex gap-4 px-4 py-3">
        {Array.from({ length: cols }).map((_, j) => (
          <div key={j} className="h-3 bg-gray-100 rounded flex-1" />
        ))}
      </div>
    ))}
  </div>
);

const SectionCard = ({ children, className = "" }) => (
  <div className={`bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden ${className}`}>
    {children}
  </div>
);

const ProductFormModal = ({ product, categories, onClose, onSave }) => {
  const isEdit = !!product;
  const [form, setForm] = useState({
    productName: product?.productName ?? "",
    description: product?.description ?? "",
    retailPrice: product?.retailPrice ?? "",
    discountPercent: product?.discountPercent ?? 0,
    primaryImageUrl: product?.primaryImageUrl ?? "",
    secondaryImageUrls: product?.secondaryImageUrls ?? [],
    active: product?.active ?? true,
    categoryId: product?.categoryId ?? "",
    physicalQty: product?.physicalQty ?? 0,
  });
  const [saving, setSaving] = useState(false);

  const set = (key, val) => setForm(f => ({ ...f, [key]: val }));

  const handleSave = async () => {
    if (!form.productName || !form.retailPrice || (!isEdit && !form.categoryId)) {
      toast.error("Please fill all required fields");
      return;
    }
    if (!form.primaryImageUrl) {
      toast.error("Primary image is required");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        productName: form.productName,
        description: form.description,
        retailPrice: parseFloat(form.retailPrice),
        discountPercent: parseFloat(form.discountPercent) || 0,
        primaryImageUrl: form.primaryImageUrl,
        secondaryImageUrls: form.secondaryImageUrls,
        active: form.active,
        physicalQty: form.physicalQty,
      };
      if (isEdit) {
        await api.put(`/seller/products/${product.productId}`, payload,
          { params: form.categoryId !== product.categoryId ? { categoryId: form.categoryId } : {} }
        );
        toast.success("Product updated");
      } else {
        await api.post(`/seller/categories/${form.categoryId}/product`, payload);
        toast.success("Product created");
      }
      onSave();
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to save product");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-5 border-b border-gray-100">
          <h2 className="text-sm font-bold text-gray-800">{isEdit ? "Edit Product" : "Add New Product"}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
            <MdClose size={18} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {/* Category */}
          <div>
            <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1">
              Category <span className="text-red-400">*</span>
            </label>
            <div className="relative group">
              <select
                value={form.categoryId}
                onChange={e => set("categoryId", e.target.value)}
                className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2.5 pr-10 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700 appearance-none bg-white cursor-pointer"
              >
                <option value="">Select a category</option>
                {categories.map(c => (
                  <option key={c.categoryId} value={c.categoryId}>{c.categoryName}</option>
                ))}
              </select>
              <div className="absolute inset-y-0 right-3 flex items-center pointer-events-none">
                <svg className="w-4 h-4 text-gray-400 group-hover:text-gray-700 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>
          </div>

          {/* Product name */}
          <div className="flex items-end gap-3">
            <div className="flex-1">
              <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1">
                Product Name <span className="text-red-400">*</span>
              </label>
              <input
                type="text"
                value={form.productName}
                onChange={e => set("productName", e.target.value)}
                placeholder="Enter product name"
                className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
              />
            </div>
            <div className="w-25 shrink-0">
              <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1">
                Stock
              </label>
              <input
                type="number"
                value={form.physicalQty}
                onChange={e => set("physicalQty", e.target.value)}
                placeholder="0"
                min={0}
                className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
              />
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1">Description</label>
            <textarea
              value={form.description}
              onChange={e => set("description", e.target.value)}
              placeholder="Product description..."
              rows={3}
              className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700 resize-none"
            />
          </div>

          {/* Price + Discount */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1">
                Retail Price (₹) <span className="text-red-400">*</span>
              </label>
              <input
                type="number"
                value={form.retailPrice}
                onChange={e => set("retailPrice", e.target.value)}
                placeholder="0.00"
                className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
              />
            </div>
            <div>
              <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1">
                Discount (%)
              </label>
              <input
                type="number"
                value={form.discountPercent}
                onChange={e => set("discountPercent", e.target.value)}
                placeholder="0"
                min={0}
                max={100}
                className="w-full text-xs border border-gray-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
              />
            </div>
          </div>

          {/* Selling price preview */}
          {form.retailPrice > 0 && (
            <div className="bg-blue-50 rounded-xl px-3 py-2 flex items-center justify-between">
              <span className="text-xs text-blue-600 font-semibold">Selling Price</span>
              <span className="text-sm font-black text-blue-700">
                {formatCurrency(form.retailPrice * (1 - (form.discountPercent || 0) / 100))}
              </span>
            </div>
          )}

          {/* Image Upload Component */}
          <ProductImageUpload
            primaryImage={form.primaryImageUrl}
            secondaryImages={form.secondaryImageUrls}
            onPrimaryChange={(url) => set("primaryImageUrl", url)}
            onSecondaryChange={(urls) => set("secondaryImageUrls", urls)}
          />

          {/* Active toggle */}
          <label className="flex items-center gap-2.5 cursor-pointer">
            <span className={`w-4 h-4 rounded flex items-center justify-center border shrink-0 transition-colors ${form.active ? "bg-blue-500 border-blue-500" : "border-gray-300"
              }`} onClick={() => set("active", !form.active)}>
              {form.active && (
                <svg viewBox="0 0 10 8" className="w-2.5 h-2.5">
                  <path d="M1 4l3 3 5-6" stroke="white" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
            </span>
            <span className="text-xs text-gray-600 font-medium">Active (visible to customers)</span>
          </label>
        </div>

        <div className="flex gap-3 p-5 border-t border-gray-100">
          <button
            onClick={onClose}
            className="flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 transition-colors cursor-pointer font-medium"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex-1 px-3 py-2.5 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl transition-colors cursor-pointer font-bold active:scale-[0.98] disabled:opacity-50 flex items-center justify-center gap-2"
          >
            <MdSave size={14} />
            {saving ? "Saving..." : isEdit ? "Save Changes" : "Create Product"}
          </button>
        </div>
      </div>
    </div>
  );
};

const DeleteModal = ({ product, onClose, onConfirm, loading }) => (
  <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
    <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
      <div className="flex items-center gap-2 text-red-600 mb-3">
        <MdWarning size={20} />
        <h2 className="text-sm font-bold">Delete Product</h2>
      </div>
      <p className="text-sm text-gray-600 mb-1">
        Delete <span className="font-semibold text-gray-900">"{product.productName}"</span>?
      </p>
      <p className="text-xs text-gray-400 mb-6">This action cannot be undone.</p>
      <div className="flex gap-3">
        <button onClick={onClose}
          className="flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">
          Cancel
        </button>
        <button onClick={onConfirm} disabled={loading}
          className="flex-1 px-3 py-2.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98]">
          {loading ? "Deleting..." : "Delete"}
        </button>
      </div>
    </div>
  </div>
);

const PIMTab = ({ products, categories, loading, onRefresh, showSeller = false }) => {
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [editProduct, setEditProduct] = useState(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [deleteProduct, setDeleteProduct] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const filtered = products.filter(p => {
    const matchSearch = !search || p.productName.toLowerCase().includes(search.toLowerCase());
    const matchCat = !categoryFilter || p.categoryName === categoryFilter;
    return matchSearch && matchCat;
  });

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await api.delete(`/seller/products/${deleteProduct.productId}`);
      toast.success("Product deleted");
      setDeleteProduct(null);
      onRefresh();
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to delete product");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <SectionCard>
        {/* Toolbar */}
        <div className="flex items-center gap-3 p-4 border-b border-gray-50 flex-wrap">
          <div className="flex items-center gap-2 flex-1 min-w-48 border border-gray-200 rounded-xl px-3 py-2 focus-within:ring-1 focus-within:ring-blue-300">
            <MdSearch size={14} className="text-gray-400 shrink-0" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search products..."
              className="text-xs text-gray-700 focus:outline-none w-full"
            />
            {search && (
              <button onClick={() => setSearch("")} className="text-gray-300 hover:text-gray-500 cursor-pointer">
                <MdClose size={13} />
              </button>
            )}
          </div>
          <select
            value={categoryFilter}
            onChange={e => setCategoryFilter(e.target.value)}
            className="text-xs border border-gray-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
          >
            <option value="">All Categories</option>
            {categories.map(c => (
              <option key={c.categoryId} value={c.categoryName}>{c.categoryName}</option>
            ))}
          </select>
          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center gap-1.5 bg-gray-950 hover:bg-gray-800 text-white text-xs font-bold px-4 py-2 rounded-xl transition-all cursor-pointer active:scale-[0.98]"
          >
            <MdAdd size={14} /> Add Product
          </button>
        </div>

        {/* Table */}
        {loading ? <TableSkeleton cols={6} /> : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-50">
                  {["Product", "Category", ...(showSeller ? ["Seller"] : []), "Price", "Stock", "Status", ""].map(h => (
                    <th key={h} className="text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest px-4 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr><td colSpan={showSeller ? 7 : 6} className="text-center py-10 text-sm text-gray-400">No products found</td></tr>
                ) : filtered.map(p => (
                  <tr key={p.productId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2.5">
                        <div className="w-9 h-9 rounded-lg bg-gray-100 overflow-hidden shrink-0">
                          {p.primaryImageUrl
                            ? <img src={p.primaryImageUrl} alt="" className="w-full h-full object-cover" />
                            : <div className="w-full h-full flex items-center justify-center text-gray-300 text-lg">◈</div>
                          }
                        </div>
                        <div>
                          <p className="text-sm font-semibold text-gray-800 line-clamp-1">{p.productName}</p>
                          <p className="text-[10px] text-gray-400">{p.productId}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-600">{p.categoryName}</td>
                    {showSeller && (
                      <td className="px-4 py-3">
                        <p className="text-xs font-semibold text-gray-600">{p.sellerName ?? "—"}</p>
                        {p.sellerId && (
                          <p className="text-[10px] text-gray-400">{p.sellerId}</p>
                        )}
                      </td>
                    )}
                    <td className="px-4 py-3">
                      <p className="text-sm font-bold text-gray-900">{formatCurrency(p.sellingPrice)}</p>
                      {p.discountPercent > 0 && (
                        <p className="text-[10px] text-gray-400 line-through">{formatCurrency(p.retailPrice)}</p>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <p className="text-xs font-semibold text-gray-700">{p.availableQty} avail.</p>
                      <p className="text-[10px] text-gray-400">{p.reservedQty} reserved</p>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${p.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
                        }`}>
                        {p.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1 justify-end">
                        <button
                          onClick={() => setEditProduct(p)}
                          className="p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded-lg transition-colors cursor-pointer"
                        >
                          <MdEdit size={14} />
                        </button>
                        <button
                          onClick={() => setDeleteProduct(p)}
                          className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
                        >
                          <MdDelete size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      {(showAddModal || editProduct) && (
        <ProductFormModal
          product={editProduct}
          categories={categories}
          onClose={() => { setShowAddModal(false); setEditProduct(null); }}
          onSave={() => { setShowAddModal(false); setEditProduct(null); onRefresh(); }}
        />
      )}
      {deleteProduct && (
        <DeleteModal
          product={deleteProduct}
          onClose={() => setDeleteProduct(null)}
          onConfirm={handleDelete}
          loading={deleting}
        />
      )}
    </>
  );
};

const InventoryTab = ({ products, loading, onRefresh, showSeller = false }) => {
  const [showSupplyModal, setShowSupplyModal] = useState(false);
  const [search, setSearch] = useState("");

  const filtered = products.filter(p =>
    !search || p.productName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <>
      <SectionCard>
        <div className="flex items-center gap-3 p-4 border-b border-gray-50 flex-wrap">
          <div className="flex items-center gap-2 flex-1 min-w-48 border border-gray-200 rounded-xl px-3 py-2 focus-within:ring-1 focus-within:ring-blue-300">
            <MdSearch size={14} className="text-gray-400 shrink-0" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search products..."
              className="text-xs text-gray-700 focus:outline-none w-full"
            />
            {search && (
              <button onClick={() => setSearch("")} className="text-gray-300 hover:text-gray-500 cursor-pointer">
                <MdClose size={13} />
              </button>
            )}
          </div>
          <button
            onClick={() => setShowSupplyModal(true)}
            className="flex items-center gap-1.5 bg-gray-950 hover:bg-gray-800 text-white text-xs font-bold px-4 py-2 rounded-xl transition-all cursor-pointer active:scale-[0.98]"
          >
            <MdInventory size={14} /> Supply Update
          </button>
        </div>

        {loading ? <TableSkeleton cols={5} /> : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-50">
                  {["Product", ...(showSeller ? ["Seller"] : []), "Physical", "Reserved", "Available", "Status"].map(h => (
                    <th key={h} className="text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest px-4 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr><td colSpan={showSeller ? 6 : 5} className="text-center py-10 text-sm text-gray-400">No products found</td></tr>
                ) : filtered.map(p => {
                  const isOut = p.availableQty === 0;
                  const isLow = !isOut && p.availableQty <= 5;
                  return (
                    <tr key={p.productId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2.5">
                          <div className="w-9 h-9 rounded-lg bg-gray-100 overflow-hidden shrink-0">
                            {p.primaryImageUrl
                              ? <img src={p.primaryImageUrl} alt="" className="w-full h-full object-cover" />
                              : <div className="w-full h-full flex items-center justify-center text-gray-300 text-lg">◈</div>
                            }
                          </div>
                          <div>
                            <p className="text-sm font-semibold text-gray-800 line-clamp-1">{p.productName}</p>
                            <p className="text-[10px] text-gray-400">{p.categoryName}</p>
                          </div>
                        </div>
                      </td>
                      {showSeller && (
                        <td className="px-4 py-3">
                          <p className="text-xs font-semibold text-gray-600">{p.sellerName ?? "—"}</p>
                          {p.sellerId && (
                            <p className="text-[10px] text-gray-400">{p.sellerId}</p>
                          )}
                        </td>
                      )}
                      <td className="px-4 py-3 text-sm font-semibold text-gray-700">{p.physicalQty}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-blue-500">{p.reservedQty}</td>
                      <td className="px-4 py-3 text-sm font-bold text-gray-900">{p.availableQty}</td>
                      <td className="px-4 py-3">
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${isOut ? "bg-red-100 text-red-600" :
                          isLow ? "bg-amber-100 text-amber-700" :
                            "bg-green-100 text-green-700"
                          }`}>
                          {isOut ? "Out of Stock" : isLow ? "Low Stock" : "In Stock"}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      {showSupplyModal && (
        <SupplyUpdateModal
          products={products}
          onClose={() => setShowSupplyModal(false)}
          onSave={() => { setShowSupplyModal(false); onRefresh(); }}
        />
      )}
    </>
  );
};



const ProductsTab = ({ mode = 'seller' }) => {
  const [subtab, setSubtab] = useState("pim");
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchProducts = useCallback(async () => {
    try {
      setLoading(true);
      const [productsRes, catsRes] = await Promise.all([
        api.get(mode === "admin" ? "/admin/products" : "/seller/products"),
        api.get("/public/categories"),
      ]);
      setProducts(productsRes.data ?? []);
      setCategories(catsRes.data ?? []);
    } catch {
      toast.error("Failed to load products");
    } finally {
      setLoading(false);
    }
  }, [mode]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  return (
    <div className="space-y-4">
      {/* Subtab switcher */}
      <div className="flex gap-1 bg-white rounded-xl border border-gray-100 shadow-sm p-1 w-fit">
        {SUBTABS.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setSubtab(id)}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${subtab === id
              ? "bg-gray-950 text-white"
              : "text-gray-500 hover:text-gray-800 hover:bg-gray-50"
              }`}
          >
            {label}
          </button>
        ))}
      </div>

      {subtab === "pim"
        ? <PIMTab products={products} categories={categories} loading={loading} onRefresh={fetchProducts} showSeller={mode === "admin"} />
        : <InventoryTab products={products} loading={loading} onRefresh={fetchProducts} showSeller={mode === "admin"} />
      }
    </div>
  );
};

export default ProductsTab;
