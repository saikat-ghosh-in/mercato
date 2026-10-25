import Accordion from "../shared/Accordion";
import { useId, useState } from "react";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
import { FiX } from "react-icons/fi";
import SelectControl from "../shared/SelectControl";
import { SORT_OPTIONS } from "../../utils/productUtils";

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
          <Accordion multiple defaultValue={["category", "seller", "price", "availability"]}><Accordion.Item className="filter-section" value="category"><Accordion.Trigger className="filter-section-toggle">Category</Accordion.Trigger><Accordion.Content><FilterOptions items={categories.map(category => category.categoryName)} selected={draft.category} onChange={category => onChange({ category })} /></Accordion.Content></Accordion.Item>
          {sellers.length > 0 && <Accordion.Item className="filter-section" value="seller"><Accordion.Trigger className="filter-section-toggle">Seller</Accordion.Trigger><Accordion.Content><FilterOptions multiple items={sellers.map(seller => seller.sellerDisplayName)} selected={draft.sellers} onChange={values => onChange({ sellers: values })} /></Accordion.Content></Accordion.Item>}
          <Accordion.Item className="filter-section" value="price"><Accordion.Trigger className="filter-section-toggle">Price range</Accordion.Trigger><Accordion.Content><div className="filter-price-fields">
            <label htmlFor={id + "-min"}>Min price (₹)<input id={id + "-min"} type="number" min="0" step="0.01" inputMode="decimal" placeholder="0" value={draft.minPrice} onChange={event => onChange({ minPrice: event.target.value })} aria-invalid={Boolean(error)} aria-describedby={priceError} /></label>
            <label htmlFor={id + "-max"}>Max price (₹)<input id={id + "-max"} type="number" min="0" step="0.01" inputMode="decimal" placeholder="Any" value={draft.maxPrice} onChange={event => onChange({ maxPrice: event.target.value })} aria-invalid={Boolean(error)} aria-describedby={priceError} /></label>
          </div>{error && <p id={priceError} className="filter-error" role="alert">{error}</p>}</Accordion.Content></Accordion.Item>
          <Accordion.Item className="filter-section" value="availability"><Accordion.Trigger className="filter-section-toggle">Availability</Accordion.Trigger><Accordion.Content><label className="filter-option"><input type="checkbox" checked={draft.inStockOnly} onChange={event => onChange({ inStockOnly: event.target.checked })} />In stock only</label></Accordion.Content></Accordion.Item>
          </Accordion>
        </div>
        <div className="filter-drawer-footer"><button type="button" className="filter-clear" onClick={onClear}>Clear filters</button><div><button type="button" className="button button-outline" onClick={onClose}>Cancel</button><button type="submit" className="button button-dark">Apply filters</button></div></div>
      </form>
    </DialogPanel>
  </Dialog>;
}
