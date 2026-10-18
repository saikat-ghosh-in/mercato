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

export default function ProductListingPage() {
  const dispatch = useDispatch();
  const [params, setParams] = useSearchParams();
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
      <div className="page-heading"><p className="eyebrow"><Link to="/">HOME</Link> / THE COLLECTION</p><h1>{keyword ? 'Results for “' + keyword + '”' : category || "Good finds, all in one place."}</h1><p>Discover the things that make your everyday a little better.</p></div>
      {filterCount > 0 && <div className="flex flex-wrap items-center gap-2 mb-6">
        {keyword && <Chip label={'Search: ' + keyword} onRemove={() => setParam({ keyword: "" })} />}
        {category && <Chip label={category} onRemove={() => setParam({ category: "" })} />}
        {selectedSellers.map(s => <Chip key={s} label={s} onRemove={() => setParam({ sellers: selectedSellers.filter(v => v !== s).join(",") })} />)}
        {(minPrice || maxPrice) && <Chip label={formatCurrency(minPrice || 0) + " – " + (maxPrice ? formatCurrency(maxPrice) : "Any")} onRemove={() => setParam({ minPrice: "", maxPrice: "" })} />}
        {inStockOnly && <Chip label="In stock" onRemove={() => setParam({ inStock: "" })} />}
        <button className="text-xs underline ml-2" onClick={clearAllFilters}>Clear all</button>
      </div>}
      <div className="flex gap-8 items-start">
        <FilterPanel {...props} />
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
            <p className="text-xs text-gray-500" role="status">{loading ? "Finding the good things…" : error ? "Collection unavailable" : (pagination.totalElements ?? 0) + (pagination.totalElements === 1 ? " product" : " products")}</p>
            <div className="flex gap-3 items-center">
              <button className="lg:hidden button border border-gray-200 bg-white" onClick={() => setDrawerOpen(true)}><FiSliders />Filters{filterCount > 0 ? " (" + filterCount + ")" : ""}</button>
              <select aria-label="Sort products" value={sortBy + ":" + sortingOrder} onChange={e => { const [sort, order] = e.target.value.split(":"); setParam({ sortBy: sort, sortingOrder: order }); }} className="bg-white border border-gray-200 rounded-md px-3 py-2 text-xs max-w-48">
                {SORT_OPTIONS.map(o => <option key={o.label} value={o.sortBy + ":" + o.sortingOrder}>{o.label}</option>)}
              </select>
            </div>
          </div>
          {invalidRange ? <div className="state-panel" role="alert"><h3>Check your price range.</h3><p>The minimum price must be less than or equal to the maximum.</p><button className="text-link" onClick={() => setParam({ minPrice: "", maxPrice: "" })}>Reset price range</button></div>
            : loading ? <div className="product-grid" aria-busy="true" aria-label="Loading products">{Array.from({ length: 12 }, (_, i) => <CardSkeleton key={i} />)}</div>
            : error ? <div className="state-panel" role="alert"><FiRefreshCw /><h3>The collection couldn’t load.</h3><p>{error}</p><button className="button button-dark" onClick={() => setAttempt(a => a + 1)}>Try again</button></div>
            : products.length === 0 ? <div className="state-panel"><FiSearch /><h3>No matches just yet.</h3><p>{filterCount ? "Try a different search or give your filters a little more room." : "Check back soon for new arrivals."}</p>{filterCount > 0 && <button className="button button-dark" onClick={clearAllFilters}>Clear all filters</button>}</div>
            : <div className="product-grid">{products.map(p => <ProductCard key={p.productId} product={p} />)}</div>}
          {!loading && !error && !invalidRange && pagination.totalPages > 1 && <Pagination pageInfo={pagination} loading={loading} onPageChange={page => setParam({ pageNumber: page })} />}
        </div>
      </div>
    </div>
    <FilterPanel asDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} totalElements={pagination.totalElements ?? 0} {...props} />
  </div>;
}

const Chip = ({ label, onRemove }) => <span className="inline-flex items-center gap-2 text-xs bg-blue-50 border border-blue-100 rounded-full pl-3 pr-1 text-blue-700 max-w-full"><span className="truncate">{label}</span><button className="p-2 shrink-0" aria-label={"Remove filter " + label} onClick={onRemove}><FiX /></button></span>;
