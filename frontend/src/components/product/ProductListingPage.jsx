import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { FiX, FiSliders, FiSearch, FiRefreshCw } from "react-icons/fi";
import { fetchProducts } from "../../reduxStore/actions/productActions";
import { fetchSellers } from "../../reduxStore/actions/sellerActions";
import { formatCurrency } from "../../utils/formatCurrency";
import ProductCard, { CardSkeleton } from "./ProductCard";
import FilterPanel from "./FilterPanel";
import Pagination from "./Pagination";
import { SORT_OPTIONS, buildQueryString, parseParams, countActiveFilters } from "../../utils/productUtils";
import { useMeasuredHeight } from "../../hooks/useMeasuredHeight";

export default function ProductListingPage() {
  const toolbarRef = useMeasuredHeight("--catalog-toolbar-height");
  const dispatch = useDispatch();
  const [params, setParams] = useSearchParams();
  const [showFilters, setShowFilters] = useState(true);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const { products, pagination, loading, error } = useSelector(s => s.products);
  const categories = useSelector(s => s.categories.categories) ?? [];
  const sellers = useSelector(s => s.sellers.sellers) ?? [];
  const filters = parseParams(params);
  const { sortBy, sortingOrder, keyword, category, sellers: selectedSellers, minPrice, maxPrice, inStockOnly } = filters;
  const filterCount = countActiveFilters(filters);
  const query = buildQueryString(filters);
  useEffect(() => {
    const controller = new AbortController();
    dispatch(fetchProducts(query, controller.signal));
    return () => controller.abort();
  }, [dispatch, query, attempt]);
  useEffect(() => { dispatch(fetchSellers()); }, [dispatch]);
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
  const clearAllFilters = () => setParams({ sortBy, sortingOrder });
  const props = { categories, sellers, filters, setParam, clearAllFilters, activeFilterCount: filterCount };
  const invalidRange = minPrice !== "" && maxPrice !== "" && Number(minPrice) > Number(maxPrice);
  return <div className="catalog-page pb-12">
    <div className="site-container">
      <div className="page-heading"><p className="eyebrow"><Link to="/">HOME</Link> / THE COLLECTION</p><h1>{keyword ? 'Results for “' + keyword + '”' : category || "Explore the collection"}</h1><p>Everyday essentials. Fresh perspectives.</p></div>
      <div className="catalog-toolbar" ref={toolbarRef} aria-label="Collection controls">
      <div className="catalog-toolbar-row">
        <p className="catalog-result-count" role="status">{loading ? "Updating results…" : error ? "Collection unavailable" : (pagination.totalElements ?? 0) + (pagination.totalElements === 1 ? " product" : " products")}</p>
        <div className="catalog-toolbar-actions">
          <button className="desktop-filter-toggle" aria-expanded={showFilters} onClick={() => setShowFilters(v => !v)}><FiSliders />{showFilters ? "Hide filters" : "Show filters"}</button>
          <button className="catalog-mobile-filters button button-outline" onClick={() => setDrawerOpen(true)}><FiSliders />Filters{filterCount > 0 ? " (" + filterCount + ")" : ""}</button>
          <select aria-label="Sort products" value={sortBy + ":" + sortingOrder} onChange={e => { const [sort, order] = e.target.value.split(":"); setParam({ sortBy: sort, sortingOrder: order }); }}>
            {SORT_OPTIONS.map(o => <option key={o.label} value={o.sortBy + ":" + o.sortingOrder}>{o.label}</option>)}
          </select>
        </div>
      </div>
      {filterCount > 0 && <div className="catalog-filter-chips">
        {keyword && <Chip label={'Search: ' + keyword} onRemove={() => setParam({ keyword: "" })} />}
        {category && <Chip label={category} onRemove={() => setParam({ category: "" })} />}
        {selectedSellers.map(s => <Chip key={s} label={s} onRemove={() => setParam({ sellers: selectedSellers.filter(v => v !== s).join(",") })} />)}
        {(minPrice || maxPrice) && <Chip label={formatCurrency(minPrice || 0) + " – " + (maxPrice ? formatCurrency(maxPrice) : "Any")} onRemove={() => setParam({ minPrice: "", maxPrice: "" })} />}
        {inStockOnly && <Chip label="In stock" onRemove={() => setParam({ inStock: "" })} />}
        <button className="text-xs underline ml-2" onClick={clearAllFilters}>Clear all</button>
      </div>}
      </div>
      <div className="flex gap-8 items-start">
        {showFilters && <FilterPanel {...props} />}
        <div className="flex-1 min-w-0">
          {error && products.length > 0 && <div className="inline-error" role="alert">We couldn’t update the results. Showing the last loaded collection.<button onClick={() => setAttempt(a => a + 1)}>Try again</button></div>}
          {invalidRange ? <div className="state-panel" role="alert"><h3>Check your price range.</h3><p>The minimum price must be less than or equal to the maximum.</p><button className="text-link" onClick={() => setParam({ minPrice: "", maxPrice: "" })}>Reset price range</button></div>
            : loading && !products.length ? <div className="product-grid" aria-busy="true" aria-label="Loading products">{Array.from({ length: 12 }, (_, i) => <CardSkeleton key={i} />)}</div>
            : error && !products.length ? <div className="state-panel" role="alert"><FiRefreshCw /><h3>The collection couldn’t load.</h3><p>{error}</p><button className="button button-dark" onClick={() => setAttempt(a => a + 1)}>Try again</button></div>
            : products.length === 0 ? <div className="state-panel"><FiSearch /><h3>No matches just yet.</h3><p>{filterCount ? "Try a different search or give your filters a little more room." : "Check back soon for new arrivals."}</p>{filterCount > 0 && <button className="button button-dark" onClick={clearAllFilters}>Clear all filters</button>}</div>
            : <div className="product-grid" aria-busy={loading}>{products.map(p => <ProductCard key={p.productId} product={p} />)}</div>}
          {!loading && !error && !invalidRange && pagination.totalPages > 1 && <Pagination pageInfo={pagination} loading={loading} onPageChange={page => { setParam({ pageNumber: page }); toolbarRef.current?.scrollIntoView({ block: "start" }); }} />}
        </div>
      </div>
    </div>
    <FilterPanel asDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} totalElements={pagination.totalElements ?? 0} {...props} />
  </div>;
}

const Chip = ({ label, onRemove }) => <span className="inline-flex items-center gap-2 text-xs bg-blue-50 border border-blue-100 rounded-full pl-3 pr-1 text-blue-700 max-w-full"><span className="truncate">{label}</span><button className="p-2 shrink-0" aria-label={"Remove filter " + label} onClick={onRemove}><FiX /></button></span>;
