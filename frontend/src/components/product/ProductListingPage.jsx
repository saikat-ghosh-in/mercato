import SponsoredShelf from "./SponsoredShelf";
import { useEffect, useReducer, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { FiX, FiSliders, FiSearch, FiRefreshCw } from "react-icons/fi";
import { fetchProducts } from "../../reduxStore/actions/productActions";
import { fetchSellers } from "../../reduxStore/actions/sellerActions";
import { formatCurrency } from "../../utils/formatCurrency";
import ProductCard, { CardSkeleton } from "./ProductCard";
import FilterPanel from "./FilterPanel";
import Pagination from "./Pagination";
import { buildQueryString, parseParams, countActiveFilters } from "../../utils/productUtils";
import { useMeasuredHeight } from "../../hooks/useMeasuredHeight";
import { applyFilterDraft, emptyFilterDraft, filterDraftReducer, validateFilterDraft } from "../../utils/filterDraft.js";

export default function ProductListingPage() {
  const toolbarRef = useMeasuredHeight("--catalog-toolbar-height");
  const dispatch = useDispatch();
  const [params, setParams] = useSearchParams();
  const [drawer, updateDrawer] = useReducer(filterDraftReducer, { open: false, draft: emptyFilterDraft(), error: "" });
  const resultsRef = useRef(null);
  const [scrollRequest, requestResultsScroll] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const { products, pagination, loading, error } = useSelector(s => s.products);
  const categories = useSelector(s => s.categories.categories) ?? [];
  const sellers = useSelector(s => s.sellers.sellers) ?? [];
  const filters = parseParams(params);
  const { sortBy, sortingOrder, keyword, category, sellers: selectedSellers, minPrice, maxPrice, inStockOnly } = filters;
  const filterCount = countActiveFilters({ ...filters, keyword: "" });
  const query = buildQueryString(filters);
  useEffect(() => {
    const controller = new AbortController();
    dispatch(fetchProducts(query, controller.signal));
    return () => controller.abort();
  }, [dispatch, query, attempt]);
  useEffect(() => { dispatch(fetchSellers()); }, [dispatch]);
  useEffect(() => {
    if (!scrollRequest) return;
    const frame = requestAnimationFrame(() => {
      const headerHeight = parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--site-header-height")) || 0;
      const toolbarHeight = toolbarRef.current?.getBoundingClientRect().height || 0;
      if (resultsRef.current) window.scrollTo({ top: window.scrollY + resultsRef.current.getBoundingClientRect().top - headerHeight - toolbarHeight - 12, behavior: "instant" });
    });
    return () => cancelAnimationFrame(frame);
  }, [scrollRequest, toolbarRef]);
  const setParam = updates => {
    setParams(previous => {
      const next = new URLSearchParams(previous);
      Object.entries(updates).forEach(([key, value]) => {
        if (value === "" || value == null) next.delete(key);
        else next.set(key, String(value));
      });
      if (!("pageNumber" in updates)) next.set("pageNumber", "0");
      return next;
    });
  };
  const clearAllFilters = () => { setParams(previous => applyFilterDraft(previous, emptyFilterDraft())); requestResultsScroll(value => value + 1); };
  const applyFilters = () => {
    const error = validateFilterDraft(drawer.draft);
    if (error) { updateDrawer({ type: "ERROR", message: error }); return; }
    setParams(previous => applyFilterDraft(previous, drawer.draft));
    updateDrawer({ type: "CLOSE" });
    requestResultsScroll(value => value + 1);
  };
  const invalidRange = validateFilterDraft(filters);
  return <div className="catalog-page pb-12">
    <div className="site-container">
      <p className="catalog-breadcrumb"><Link to="/">Home</Link> / The collection</p>
      <div className="catalog-toolbar" ref={toolbarRef} aria-label="Collection controls">
      <div className="catalog-toolbar-row">
        <div className="catalog-heading"><h1>{keyword ? 'Results for “' + keyword + '”' : category || "All products"}</h1><p className="catalog-result-count" role="status">{loading ? "Updating results…" : error ? "Collection unavailable" : (pagination.totalElements ?? 0) + (pagination.totalElements === 1 ? " product" : " products")}</p></div>
        <div className="catalog-toolbar-actions">
          <button className="icon-button catalog-filter-trigger" aria-label={"Filter and sort products" + (filterCount ? `, ${filterCount} active filter${filterCount === 1 ? "" : "s"}` : "")} title="Filter and sort" aria-expanded={drawer.open} aria-haspopup="dialog" onClick={() => updateDrawer({ type: "OPEN", filters })}><FiSliders aria-hidden="true" />{(filterCount > 0 || sortBy !== "createdAt" || sortingOrder !== "desc") && <span className="filter-active-dot" aria-hidden="true" />}</button>
        </div>
      </div>
      {(filterCount > 0 || keyword) && <div className="catalog-filter-chips">
        {keyword && <Chip label={'Search: ' + keyword} onRemove={() => setParam({ keyword: "" })} />}
        {category && <Chip label={category} onRemove={() => setParam({ category: "" })} />}
        {selectedSellers.map(s => <Chip key={s} label={s} onRemove={() => setParam({ sellers: selectedSellers.filter(v => v !== s).join(",") })} />)}
        {(minPrice || maxPrice) && <Chip label={formatCurrency(minPrice || 0) + " – " + (maxPrice ? formatCurrency(maxPrice) : "Any")} onRemove={() => setParam({ minPrice: "", maxPrice: "" })} />}
        {inStockOnly && <Chip label="In stock" onRemove={() => setParam({ inStock: "" })} />}
        {filterCount > 0 && <button className="text-xs underline ml-2" onClick={clearAllFilters}>Clear filters</button>}
      </div>}
      </div>
      <div ref={resultsRef} className="catalog-results">
          {error && products.length > 0 && <div className="inline-error" role="alert">We couldn’t update the results. Showing the last loaded collection.<button onClick={() => setAttempt(a => a + 1)}>Try again</button></div>}
          {invalidRange ? <div className="state-panel" role="alert"><h3>Check your price range.</h3><p>{invalidRange}</p><button className="text-link" onClick={() => setParam({ minPrice: "", maxPrice: "" })}>Reset price range</button></div>
            : loading && !products.length ? <div className="product-grid" aria-busy="true" aria-label="Loading products">{Array.from({ length: 12 }, (_, i) => <CardSkeleton key={i} />)}</div>
            : error && !products.length ? <div className="state-panel" role="alert"><FiRefreshCw /><h3>The collection couldn’t load.</h3><p>{error}</p><button className="button button-dark" onClick={() => setAttempt(a => a + 1)}>Try again</button></div>
            : products.length === 0 ? <div className="state-panel"><FiSearch /><h3>No matches just yet.</h3><p>{filterCount || keyword ? "Try a different search or give your filters a little more room." : "Check back soon for new arrivals."}</p>{filterCount > 0 ? <button className="button button-dark" onClick={clearAllFilters}>Clear filters</button> : keyword && <button className="button button-dark" onClick={() => setParam({ keyword: "" })}>Clear search</button>}</div>
            : <div className="product-grid" aria-busy={loading}>{products.map(p => <ProductCard key={p.productId} product={p} showPurchase />)}</div>}
          {!loading && !error && !invalidRange && !minPrice && !maxPrice && !selectedSellers.length && <SponsoredShelf placement="catalog" category={category} keyword={keyword} />}
          {!loading && !error && !invalidRange && pagination.totalPages > 1 && <Pagination pageInfo={pagination} loading={loading} onPageChange={page => { setParam({ pageNumber: page }); requestResultsScroll(value => value + 1); }} />}
      </div>
    </div>
    <FilterPanel open={drawer.open} categories={categories} sellers={sellers} draft={drawer.draft} error={drawer.error} onChange={updates => updateDrawer({ type: "CHANGE", updates })} onClear={() => updateDrawer({ type: "CLEAR" })} onApply={applyFilters} onClose={() => updateDrawer({ type: "CLOSE" })} />
  </div>;
}

const Chip = ({ label, onRemove }) => <span className="inline-flex items-center gap-2 text-xs bg-blue-50 border border-blue-100 rounded-full pl-3 pr-1 text-blue-700 max-w-full"><span className="truncate">{label}</span><button className="icon-action p-2 shrink-0" aria-label={"Remove filter " + label} onClick={onRemove}><FiX /></button></span>;
