import { useDispatch } from "react-redux";
import { fetchCategories as refreshCategoryNavigation } from "../../reduxStore/actions/categoryActions";
import ModalFrame from "../shared/ModalFrame";
import { useCallback, useEffect, useState } from "react";
import { MdAdd, MdEdit, MdDelete, MdClose, MdWarning, MdSave, MdCategory } from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatDate } from "../../utils/formatDate";

const CategoryFormModal = ({ category, onClose, onSave }) => {
    const isEdit = !!category;
    const [name, setName] = useState(category?.categoryName ?? "");
    const [saving, setSaving] = useState(false);

    const handleSave = async () => {
        if (!name.trim()) { toast.error("Category name is required"); return; }
        setSaving(true);
        try {
            if (isEdit) {
                await api.put(`/admin/categories/${category.categoryId}`, { categoryName: name.trim() });
                toast.success("Category updated");
            } else {
                await api.post("/admin/categories/create", { categoryName: name.trim() });
                toast.success("Category created");
            }
            onSave();
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to save category");
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalFrame title="Manage details" onClose={onClose}>
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
                <div className="flex items-center justify-between p-5 border-b border-gray-100">
                    <h2 className="text-sm font-bold text-gray-800">{isEdit ? "Edit Category" : "New Category"}</h2>
                    <button aria-label="Close dialog" onClick={onClose} className="icon-action text-gray-400 hover:text-gray-600 cursor-pointer transition-colors">
                        <MdClose size={18} />
                    </button>
                </div>
                <div className="p-5">
                    <label htmlFor="category-name" className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-1.5">
                        Category Name <span className="text-red-400">*</span>
                    </label>
                    <input
                        id="category-name" type="text"
                        value={name}
                        onChange={e => setName(e.target.value)}
                        onKeyDown={e => e.key === "Enter" && handleSave()}
                        placeholder="e.g. Electronics"
                        autoFocus
                        className="w-full text-sm border border-gray-200 rounded-xl px-3 py-2.5 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
                    />
                </div>
                <div className="flex gap-3 p-5 border-t border-gray-100">
                    <button  onClick={onClose} className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">Cancel</button>
                    <button onClick={handleSave} disabled={saving || !name.trim()}
                        className="control flex-1 px-3 py-2.5 text-sm bg-gray-950 hover:bg-gray-800 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98] flex items-center justify-center gap-2">
                        <MdSave size={14} />
                        {saving ? "Saving..." : isEdit ? "Save Changes" : "Create"}
                    </button>
                </div>
            </div>
        </ModalFrame>
    );
};

const DeleteModal = ({ category, onClose, onConfirm, loading }) => (
    <ModalFrame title="Delete Category" onClose={onClose}>
        <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
            <div className="flex items-center gap-2 text-red-600 mb-3">
                <MdWarning size={20} />
                <h2 className="text-sm font-bold">Delete Category</h2>
            </div>
            <p className="text-sm text-gray-600 mb-1">Delete <span className="font-semibold text-gray-900">"{category.categoryName}"</span>?</p>
            <p className="text-xs text-gray-400 mb-6">Products in this category may be affected.</p>
            <div className="flex gap-3">
                <button  onClick={onClose} className="control flex-1 px-3 py-2.5 text-sm border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 cursor-pointer font-medium">Cancel</button>
                <button onClick={onConfirm} disabled={loading}
                    className="control flex-1 px-3 py-2.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded-xl cursor-pointer font-bold disabled:opacity-50 active:scale-[0.98]">
                    {loading ? "Deleting..." : "Delete"}
                </button>
            </div>
        </div>
    </ModalFrame>
);

const CategoriesTab = () => {
    const dispatch = useDispatch();
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showAddModal, setShowAddModal] = useState(false);
    const [editCategory, setEditCategory] = useState(null);
    const [deleteCategory, setDeleteCategory] = useState(null);
    const [deleting, setDeleting] = useState(false);

    const fetchCategories = useCallback(async () => {
        try {
            setLoading(true);
            const { data } = await api.get("/public/categories");
            setCategories(data ?? []);
            dispatch(refreshCategoryNavigation());
        } catch {
            toast.error("Failed to load categories");
        } finally {
            setLoading(false);
        }
    }, [dispatch]);

    useEffect(() => { fetchCategories(); }, [fetchCategories]);

    const handleDelete = async () => {
        setDeleting(true);
        try {
            await api.delete(`/admin/categories/${deleteCategory.categoryId}`);
            toast.success("Category deleted");
            setDeleteCategory(null);
            fetchCategories();
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to delete category");
        } finally {
            setDeleting(false);
        }
    };

    return (
        <>
            <div className="surface overflow-hidden">
                {/* Toolbar */}
                <div className="flex items-center justify-between p-4 border-b border-gray-50">
                    <p className="text-xs text-gray-400 font-semibold">
                        {categories.length} categor{categories.length !== 1 ? "ies" : "y"}
                    </p>
                    <button
                        onClick={() => setShowAddModal(true)}
                        className="control flex items-center gap-1.5 bg-gray-950 hover:bg-gray-800 text-white text-xs font-bold px-4 py-2 rounded-xl transition-[transform,opacity,background-color] cursor-pointer active:scale-[0.98]"
                    >
                        <MdAdd size={14} /> New Category
                    </button>
                </div>

                {/* Grid */}
                {loading && !categories.length ? (
                    <div className="p-4 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 animate-pulse">
                        {[1, 2, 3, 4, 5, 6].map(i => (
                            <div key={i} className="h-20 bg-gray-100 rounded-xl" />
                        ))}
                    </div>
                ) : categories.length === 0 ? (
                    <div className="py-16 text-center">
                        <MdCategory size={32} className="text-gray-200 mx-auto mb-3" />
                        <p className="text-sm text-gray-400">No categories yet</p>
                    </div>
                ) : (
                    <div className="p-4 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                        {categories.map(c => (
                            <div key={c.categoryId} className="group relative bg-gray-50 hover:bg-blue-50 border border-gray-100 hover:border-blue-200 rounded-xl p-4 transition-[transform,opacity,background-color]">
                                <div className="w-8 h-8 rounded-lg bg-white border border-gray-100 flex items-center justify-center mb-2.5 shadow-sm">
                                    <MdCategory size={16} className="text-gray-400" />
                                </div>
                                <p className="text-sm font-bold text-gray-800 line-clamp-1">{c.categoryName}</p>
                                <p className="text-[10px] text-gray-400 mt-0.5">{formatDate(c.createdAt)}</p>

                                {/* Actions */}
                                <div className="absolute top-3 right-3 flex gap-1 opacity-100">
                                    <button
                                        aria-label={"Edit " + c.categoryName} onClick={() => setEditCategory(c)}
                                        className="icon-action p-1 text-gray-400 hover:text-blue-500 hover:bg-white rounded-lg transition-colors cursor-pointer shadow-sm"
                                    >
                                        <MdEdit size={13} />
                                    </button>
                                    <button
                                        aria-label={"Delete " + c.categoryName} onClick={() => setDeleteCategory(c)}
                                        className="icon-action p-1 text-gray-400 hover:text-red-500 hover:bg-white rounded-lg transition-colors cursor-pointer shadow-sm"
                                    >
                                        <MdDelete size={13} />
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {(showAddModal || editCategory) && (
                <CategoryFormModal
                    category={editCategory}
                    onClose={() => { setShowAddModal(false); setEditCategory(null); }}
                    onSave={() => { setShowAddModal(false); setEditCategory(null); fetchCategories(); }}
                />
            )}
            {deleteCategory && (
                <DeleteModal
                    category={deleteCategory}
                    onClose={() => setDeleteCategory(null)}
                    onConfirm={handleDelete}
                    loading={deleting}
                />
            )}
        </>
    );
};

export default CategoriesTab;
