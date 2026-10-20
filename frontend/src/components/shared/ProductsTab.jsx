import { useSelector } from "react-redux";
import SelectControl from "./SelectControl";
import ProductSubmissionModal from "./ProductSubmissionModal";
import ListingReviewModal from "../admin/ListingReviewModal";

import ModalFrame from "./ModalFrame";
import { useCallback, useEffect, useState } from "react";
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdClose,
  MdWarning, MdInventory,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import SupplyUpdateModal from "./SupplyUpdateModal";


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
  <div className={`surface overflow-hidden ${className}`}>
    {children}
  </div>
);

const DeleteModal = ({ product, onClose, onConfirm, loading }) => (
  <ModalFrame title="Delete Product" onClose={onClose}>
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
        <button  onClick={onClose}
          className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">
          Cancel
        </button>
        <button onClick={onConfirm} disabled={loading}
          className="control flex-1 px-3 py-2.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98]">
          {loading ? "Deleting..." : "Delete"}
        </button>
      </div>
    </div>
  </ModalFrame>
);

const PIMTab = ({ products, categories, loading, onRefresh, showSeller = false }) => {
  const adminActor = useSelector(state => Boolean(state.auth?.userDetails?.roles?.includes("ROLE_ADMIN")));
  const directCreation = showSeller || adminActor;
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [editProduct, setEditProduct] = useState(null);
  const [reviewProduct, setReviewProduct] = useState(null);
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
          <div className="dashboard-search flex-1 min-w-48">
            <MdSearch size={14} className="text-gray-400 shrink-0" />
            <input
              type="text"
              aria-label="Search products..." value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search products..."
              className="text-xs text-gray-700 focus:outline-none w-full"
            />
            {search && (
              <button aria-label="Clear product search" onClick={() => setSearch("")} className="icon-action text-gray-300 hover:text-gray-500 cursor-pointer">
                <MdClose size={13} />
              </button>
            )}
          </div>
          <SelectControl
            aria-label="Filter products by category"
            value={categoryFilter}
            onChange={e => setCategoryFilter(e.target.value)}
            className="text-xs border border-gray-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
          >
            <option value="">All Categories</option>
            {categories.map(c => (
              <option key={c.categoryId} value={c.categoryName}>{c.categoryName}</option>
            ))}
          </SelectControl>
          <button
            onClick={() => setShowAddModal(true)}
            className="control flex items-center gap-1.5 bg-gray-950 hover:bg-gray-800 text-white text-xs font-bold px-4 py-2 rounded-xl transition-[transform,opacity,background-color] cursor-pointer active:scale-[0.98]"
          >
            <MdAdd size={14} /> {directCreation ? "Create product" : "Request listing"}
          </button>
        </div>

        <p className="px-4 py-3 text-xs text-gray-500">{directCreation ? "Create products directly in the catalog. Manage stock separately in Perpetual Inventory. Seller submissions still require your approval." : "Listing requests are free. Approval is required before products appear in the marketplace. Seller edits require another review."}</p>
        {/* Table */}
        {loading && !products.length ? <TableSkeleton cols={6} /> : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-50">
                  {["Product", "Category", ...(showSeller ? ["Seller"] : []), "Price", "Status", ""].map(h => (
                    <th key={h} className="text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest px-4 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr><td colSpan={showSeller ? 6 : 5} className="text-center py-10 text-sm text-gray-400">No products found</td></tr>
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
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${p.active ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-500"
                        }`}>
                        {p.listingStatus !== "APPROVED" ? p.listingStatus : p.active ? "Approved" : "Disabled"}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1 justify-end">
                        {showSeller && p.listingStatus === "PENDING" && <button className="button button-outline" onClick={() => setReviewProduct(p)}>Review listing</button>}
                        <button
                          aria-label={"Edit " + p.productName} onClick={() => setEditProduct(p)}
                          className="icon-action p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded-lg transition-colors cursor-pointer"
                        >
                          <MdEdit size={14} />
                        </button>
                        <button
                          aria-label={"Delete " + p.productName} onClick={() => setDeleteProduct(p)}
                          className="icon-action p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
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
        <ProductSubmissionModal
          admin={directCreation}
          product={editProduct}
          categories={categories}
          onClose={() => { setShowAddModal(false); setEditProduct(null); }}
          onSave={() => { setShowAddModal(false); setEditProduct(null); onRefresh(); }}
        />
      )}
      {reviewProduct && <ListingReviewModal product={reviewProduct} onClose={() => setReviewProduct(null)} onSave={() => { setReviewProduct(null); onRefresh(); }} />}
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
          <div className="dashboard-search flex-1 min-w-48">
            <MdSearch size={14} className="text-gray-400 shrink-0" />
            <input
              type="text"
              aria-label="Search products..." value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search products..."
              className="text-xs text-gray-700 focus:outline-none w-full"
            />
            {search && (
              <button aria-label="Clear inventory search" onClick={() => setSearch("")} className="icon-action text-gray-300 hover:text-gray-500 cursor-pointer">
                <MdClose size={13} />
              </button>
            )}
          </div>
          <button
            onClick={() => setShowSupplyModal(true)}
            className="control flex items-center gap-1.5 bg-gray-950 hover:bg-gray-800 text-white text-xs font-bold px-4 py-2 rounded-xl transition-[transform,opacity,background-color] cursor-pointer active:scale-[0.98]"
          >
            <MdInventory size={14} /> Supply Update
          </button>
        </div>

        {loading && !products.length ? <TableSkeleton cols={5} /> : (
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
                            "bg-blue-100 text-blue-700"
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
            className={`control px-4 py-2 rounded-lg text-xs font-semibold transition-[transform,opacity,background-color] cursor-pointer ${subtab === id
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
