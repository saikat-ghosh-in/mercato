import { useState, useRef, useEffect, useId } from "react";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
import {
    MdFilterList, MdClose, MdCategory, MdStorefront,
    MdKeyboardArrowDown, MdKeyboardArrowUp, MdVerified,
} from "react-icons/md";

const COLLAPSED_COUNT = 5;

const FilterSection = ({ title, children, defaultOpen = true }) => {
    const [open, setOpen] = useState(defaultOpen);
    return (
        <div className="border-b border-gray-100 pb-4 mb-4 last:border-0 last:mb-0 last:pb-0">
            <button
                onClick={() => setOpen(v => !v)}
                aria-expanded={open}
                className="w-full flex items-center justify-between text-xs font-bold text-gray-700 uppercase tracking-widest mb-3 cursor-pointer"
            >
                {title}
                {open ? <MdKeyboardArrowUp size={16} /> : <MdKeyboardArrowDown size={16} />}
            </button>
            {open && children}
        </div>
    );
};

const FilterBtn = ({ active, onClick, children }) => (
    <button
        onClick={onClick}
        aria-pressed={Boolean(active)}
        className={`w-full text-left text-xs px-2.5 py-1.5 rounded-lg transition-colors cursor-pointer flex items-center gap-1.5
            ${active
                ? "bg-blue-50 text-blue-600 font-semibold"
                : "text-gray-600 hover:bg-gray-50"
            }`}
    >
        {children}
    </button>
);

const CollapsibleList = ({ items, renderItem, getKey, selected, onSelect }) => {
    const [expanded, setExpanded] = useState(false);
    const containerRef = useRef(null);
    const hasMore = items.length > COLLAPSED_COUNT;
    const visible = expanded ? items : items.slice(0, COLLAPSED_COUNT);

    useEffect(() => {
        const el = containerRef.current;
        if (!el || !hasMore || expanded) return;
        const handleWheel = (e) => { if (e.deltaY > 0) setExpanded(true); };
        el.addEventListener("wheel", handleWheel, { passive: true });
        return () => el.removeEventListener("wheel", handleWheel);
    }, [hasMore, expanded]);

    return (
        <div ref={containerRef}>
            <div className="space-y-1">
                <FilterBtn active={!selected} onClick={() => onSelect("")}>All</FilterBtn>
                {visible.map(item => {
                    const key = getKey(item);
                    return (
                        <FilterBtn key={key} active={selected === key} onClick={() => onSelect(key)}>
                            {renderItem(item)}
                            {selected === key && (
                                <MdClose size={11} className="ml-auto shrink-0"
                                    onClick={e => { e.stopPropagation(); onSelect(""); }} />
                            )}
                        </FilterBtn>
                    );
                })}
            </div>
            {hasMore && (
                <button
                    onClick={() => setExpanded(v => !v)}
                    className="mt-2 text-[11px] font-semibold text-blue-500 hover:text-blue-700 cursor-pointer flex items-center gap-1"
                >
                    {expanded
                        ? <><MdKeyboardArrowUp size={13} />Show less</>
                        : <><MdKeyboardArrowDown size={13} />{items.length - COLLAPSED_COUNT} more</>
                    }
                </button>
            )}
        </div>
    );
};

const PriceFilter = ({ minPrice, maxPrice, setParam }) => {
    const id = useId();
    return (
    <div className="space-y-3">
        <div className="space-y-2">
            <div>
                <label htmlFor={id + "-min"} className="text-[10px] text-gray-400 mb-1 block">Min</label>
                <div className="flex items-center border border-gray-200 rounded-lg overflow-hidden focus-within:ring-1 focus-within:ring-blue-400">
                    <span className="px-2 text-xs text-gray-400 bg-gray-50 border-r border-gray-200 py-2 shrink-0">₹</span>
                    <input id={id + "-min"} type="number" min="0" step="0.01" placeholder="0" value={minPrice}
                        onChange={e => setParam({ minPrice: e.target.value })}
                        className="w-full px-2 py-2 text-xs text-gray-700 focus:outline-none"
                    />
                </div>
            </div>
            <div>
                <label htmlFor={id + "-max"} className="text-[10px] text-gray-400 mb-1 block">Max</label>
                <div className="flex items-center border border-gray-200 rounded-lg overflow-hidden focus-within:ring-1 focus-within:ring-blue-400">
                    <span className="px-2 text-xs text-gray-400 bg-gray-50 border-r border-gray-200 py-2 shrink-0">₹</span>
                    <input id={id + "-max"} type="number" min="0" step="0.01" placeholder="Any" value={maxPrice}
                        onChange={e => setParam({ maxPrice: e.target.value })}
                        className="w-full px-2 py-2 text-xs text-gray-700 focus:outline-none"
                    />
                </div>
            </div>
        </div>
        {(minPrice || maxPrice) && (
            <button onClick={() => setParam({ minPrice: "", maxPrice: "" })}
                className="text-[11px] text-blue-500 hover:text-blue-700 cursor-pointer">
                Clear price filter
            </button>
        )}
    </div>
); };

const MultiCollapsibleList = ({ items, renderItem, getKey, selected, onToggle, onClearAll }) => {
    const [expanded, setExpanded] = useState(false);
    const containerRef = useRef(null);
    const hasMore = items.length > COLLAPSED_COUNT;
    const visible = expanded ? items : items.slice(0, COLLAPSED_COUNT);

    useEffect(() => {
        const el = containerRef.current;
        if (!el || !hasMore || expanded) return;
        const handleWheel = (e) => { if (e.deltaY > 0) setExpanded(true); };
        el.addEventListener("wheel", handleWheel, { passive: true });
        return () => el.removeEventListener("wheel", handleWheel);
    }, [hasMore, expanded]);

    const hasSelection = selected.length > 0;

    return (
        <div ref={containerRef}>
            <div className="space-y-1">
                <FilterBtn active={!hasSelection} onClick={onClearAll}>All</FilterBtn>
                {visible.map(item => {
                    const key = getKey(item);
                    const isSelected = selected.includes(key);
                    return (
                        <FilterBtn key={key} active={isSelected} onClick={() => onToggle(key)}>
                            {/* Checkbox indicator */}
                            <span className={`w-3.5 h-3.5 rounded border shrink-0 flex items-center justify-center transition-colors ${isSelected ? "bg-blue-500 border-blue-500" : "border-gray-300"
                                }`}>
                                {isSelected && (
                                    <svg viewBox="0 0 10 8" className="w-2 h-2">
                                        <path d="M1 4l3 3 5-6" stroke="white" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                                    </svg>
                                )}
                            </span>
                            {renderItem(item)}
                        </FilterBtn>
                    );
                })}
            </div>
            {hasMore && (
                <button
                    onClick={() => setExpanded(v => !v)}
                    className="mt-2 text-[11px] font-semibold text-blue-500 hover:text-blue-700 cursor-pointer flex items-center gap-1"
                >
                    {expanded
                        ? <><MdKeyboardArrowUp size={13} />Show less</>
                        : <><MdKeyboardArrowDown size={13} />{items.length - COLLAPSED_COUNT} more</>
                    }
                </button>
            )}
        </div>
    );
};

const FilterBody = ({ categories, sellers, filters, setParam }) => {
    const { category, sellers: selectedSellers, minPrice, maxPrice, inStockOnly } = filters;

    const toggleSeller = (sellerName) => {
        if (!sellerName) {
            setParam({ sellers: "" });
            return;
        }
        const current = selectedSellers ?? [];
        const next = current.includes(sellerName)
            ? current.filter(s => s !== sellerName)
            : [...current, sellerName];
        setParam({ sellers: next.length ? next.join(",") : "" });
    };

    return (
        <>
            <FilterSection title="Category">
                <CollapsibleList
                    items={categories}
                    selected={category}
                    onSelect={v => setParam({ category: v })}
                    getKey={c => c.categoryName}
                    renderItem={c => <><MdCategory size={11} className="shrink-0" />{c.categoryName}</>}
                />
            </FilterSection>

            {sellers.length > 0 && (
                <FilterSection title="Seller">
                    <MultiCollapsibleList
                        items={sellers}
                        selected={selectedSellers ?? []}
                        onToggle={toggleSeller}
                        onClearAll={() => setParam({ sellers: "" })}
                        getKey={s => s.sellerDisplayName}
                        renderItem={s => (
                            <>
                                {s.profileImageUrl
                                    ? <img src={s.profileImageUrl} alt="" className="w-4 h-4 rounded-full object-cover shrink-0 border border-gray-100" />
                                    : <MdStorefront size={11} className="text-gray-400 shrink-0" />}
                                <span className="truncate">{s.sellerDisplayName}</span>
                            </>
                        )}
                    />
                </FilterSection>
            )}

            <FilterSection title="Price Range">
                <PriceFilter minPrice={minPrice} maxPrice={maxPrice} setParam={setParam} />
            </FilterSection>

            <FilterSection title="Availability">
                <label className="flex items-center gap-2.5 cursor-pointer group">
                    <input type="checkbox" checked={inStockOnly} onChange={e => setParam({ inStock: e.target.checked ? "true" : "" })} />
                    <span className="text-xs text-gray-600 flex items-center gap-1">
                        <MdVerified size={12} className={inStockOnly ? "text-blue-500" : "text-gray-300"} />
                        In Stock Only
                    </span>
                </label>
            </FilterSection>
        </>
    );
};

const FilterHeader = ({ activeFilterCount, clearAllFilters, onClose }) => (
    <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-bold text-gray-800 flex items-center gap-2">
            <MdFilterList size={16} /> Filters
            {activeFilterCount > 0 && (
                <span className="text-[10px] font-bold bg-blue-500 text-white px-1.5 py-0.5 rounded-full">
                    {activeFilterCount}
                </span>
            )}
        </h2>
        <div className="flex items-center gap-2">
            {activeFilterCount > 0 && (
                <button onClick={clearAllFilters}
                    className="flex items-center gap-1 text-[11px] font-semibold text-red-500 hover:text-red-700 bg-red-50 hover:bg-red-100 px-2 py-1 rounded-lg transition-colors cursor-pointer" aria-label="Clear all filters">
                    <MdClose size={11} /> Clear
                </button>
            )}
            {onClose && (
                <button onClick={onClose} aria-label="Close filters" className="text-gray-400 hover:text-gray-700 cursor-pointer transition-colors">
                    <MdClose size={20} />
                </button>
            )}
        </div>
    </div>
);

const FilterPanel = ({
    categories, sellers,
    filters, setParam, activeFilterCount, clearAllFilters,
    asDrawer = false, onClose, open, totalElements,
}) => {
    const sharedProps = { categories, sellers, filters, setParam };

    if (!asDrawer) return (
        <aside className="filter-sidebar hidden lg:block w-56 shrink-0" aria-label="Product filters">
            <FilterHeader activeFilterCount={activeFilterCount} clearAllFilters={clearAllFilters} />
            <FilterBody {...sharedProps} />
        </aside>
    );

    return (
        <Dialog open={open} onClose={onClose} className="relative z-60">
            <DialogTitle className="sr-only">Filter products</DialogTitle>
            <DialogBackdrop transition className="dialog-backdrop" />
            <DialogPanel transition className="filter-drawer fixed inset-y-0 right-0 w-80 max-w-[90vw] bg-white shadow-2xl flex flex-col">
                <div className="p-5 border-b border-gray-100">
                    <FilterHeader activeFilterCount={activeFilterCount} clearAllFilters={clearAllFilters} onClose={onClose} />
                </div>
                <div className="flex-1 overflow-y-auto p-5">
                    <FilterBody {...sharedProps} />
                </div>
                <div className="p-4 border-t border-gray-100">
                    <button
                        onClick={onClose}
                        className="w-full bg-blue-500 hover:bg-blue-600 text-white font-semibold py-2.5 rounded-xl text-sm transition-colors cursor-pointer"
                    >
                        Show {totalElements} {totalElements === 1 ? "result" : "results"}
                    </button>
                </div>
            </DialogPanel>
        </Dialog>
    );
};

export default FilterPanel;
