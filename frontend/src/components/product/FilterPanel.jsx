import { useId, useState } from "react";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
import { FiChevronDown, FiX } from "react-icons/fi";
import SelectControl from "../shared/SelectControl";
import { SORT_OPTIONS } from "../../utils/productUtils";

function FilterSection({ title, children }) {
  const [open, setOpen] = useState(true);
  const id = useId();
  return <section className="filter-section"><button type="button" className="filter-section-toggle" aria-expanded={open} aria-controls={id} onClick={() => setOpen(value => !value)}>{title}<FiChevronDown aria-hidden="true" style={{ transform: open ? "rotate(180deg)" : undefined }} /></button><div id={id} hidden={!open}>{children}</div></section>;
}

function FilterOptions({ items, selected, onChange, multiple = false }) {
  const [expanded, setExpanded] = useState(false);
  const visible = expanded ? items : items.slice(0, 5);
  return <div className="filter-options">{!multiple && <button type="button" aria-pressed={!selected} className="filter-option" onClick={() => onChange("")}>All categories</button>}
    {visible.map(item => multiple ? <label key={item} className="filter-option"><input type="checkbox" checked={selected.includes(item)} onChange={() => onChange(selected.includes(item) ? selected.filter(value => value !== item) : [...selected, item])} /><span>{item}</span></label> : <button type="button" key={item} className="filter-option" aria-pressed={selected === item} onClick={() => onChange(item)}>{item}</button>)}
    {items.length > 5 && <button type="button" className="filter-more" onClick={() => setExpanded(value => !value)}>{expanded ? "Show less" : `Show ${items.length - 5} more`}</button>}
  </div>;
}

export default function FilterPanel({ open, categories, sellers, draft, error, onChange, onClear, onApply, onClose }) {
  const id = useId();
  const priceError = error ? id + "-price-error" : undefined;
  return <Dialog open={open} onClose={onClose} className="relative z-60">
    <DialogBackdrop transition className="dialog-backdrop" />
    <DialogPanel transition className="filter-drawer">
      <div className="filter-drawer-heading"><DialogTitle>Filter &amp; sort</DialogTitle><button type="button" className="icon-button" aria-label="Close filters" onClick={onClose}><FiX /></button></div>
      <form className="filter-form" noValidate onSubmit={event => { event.preventDefault(); onApply(); }}>
        <div className="filter-drawer-body">
          <section className="filter-section filter-sort"><label htmlFor={id + "-sort"}>Sort by</label><SelectControl id={id + "-sort"} value={(draft.sortBy || "createdAt") + ":" + (draft.sortingOrder || "desc")} onChange={event => { const [sortBy, sortingOrder] = event.target.value.split(":"); onChange({ sortBy, sortingOrder }); }}>{SORT_OPTIONS.map(option => <option key={option.label} value={option.sortBy + ":" + option.sortingOrder}>{option.label}</option>)}</SelectControl></section>
          <FilterSection title="Category"><FilterOptions items={categories.map(category => category.categoryName)} selected={draft.category} onChange={category => onChange({ category })} /></FilterSection>
          {sellers.length > 0 && <FilterSection title="Seller"><FilterOptions multiple items={sellers.map(seller => seller.sellerDisplayName)} selected={draft.sellers} onChange={values => onChange({ sellers: values })} /></FilterSection>}
          <FilterSection title="Price range"><div className="filter-price-fields">
            <label htmlFor={id + "-min"}>Min price (₹)<input id={id + "-min"} type="number" min="0" step="0.01" inputMode="decimal" placeholder="0" value={draft.minPrice} onChange={event => onChange({ minPrice: event.target.value })} aria-invalid={Boolean(error)} aria-describedby={priceError} /></label>
            <label htmlFor={id + "-max"}>Max price (₹)<input id={id + "-max"} type="number" min="0" step="0.01" inputMode="decimal" placeholder="Any" value={draft.maxPrice} onChange={event => onChange({ maxPrice: event.target.value })} aria-invalid={Boolean(error)} aria-describedby={priceError} /></label>
          </div>{error && <p id={priceError} className="filter-error" role="alert">{error}</p>}</FilterSection>
          <FilterSection title="Availability"><label className="filter-option"><input type="checkbox" checked={draft.inStockOnly} onChange={event => onChange({ inStockOnly: event.target.checked })} />In stock only</label></FilterSection>
        </div>
        <div className="filter-drawer-footer"><button type="button" className="filter-clear" onClick={onClear}>Clear filters</button><div><button type="button" className="button button-outline" onClick={onClose}>Cancel</button><button type="submit" className="button button-dark">Apply filters</button></div></div>
      </form>
    </DialogPanel>
  </Dialog>;
}
