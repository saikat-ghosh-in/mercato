import ActionArrow from "./shared/ActionArrow";
import { Link } from "react-router-dom";
import { useRef } from "react";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
import { FiArrowRight, FiGrid, FiHeadphones, FiHome, FiSearch, FiShoppingBag, FiSun, FiX } from "react-icons/fi";

const categoryDetails = {
  "Home & Living": { icon: FiHome, description: "Good things for your space." },
  Fashion: { icon: FiShoppingBag, description: "Style for your kind of day." },
  Electronics: { icon: FiHeadphones, description: "Small upgrades. New possibilities." },
  "Beauty & Care": { icon: FiSun, description: "A little care, every day." },
};

export default function ExplorePanel({ open, onClose, categories, keyword, onKeywordChange, onSearch }) {
  const searchRef = useRef(null);
  return (
    <Dialog open={open} onClose={onClose} initialFocus={searchRef} className="explore-dialog">
      <DialogBackdrop transition className="dialog-backdrop" />
      <DialogPanel transition className="explore-panel">
        <div className="explore-panel-heading">
          <div><p className="eyebrow">MERCATO</p><DialogTitle>Search &amp; discover</DialogTitle></div>
          <button className="icon-button" onClick={onClose} aria-label="Close search"><FiX /></button>
        </div>
        <div className="explore-panel-body">
          <form className="explore-search" role="search" onSubmit={onSearch}>
            <FiSearch aria-hidden="true" />
            <input ref={searchRef} aria-label="Search the marketplace" placeholder="Search products" value={keyword} onChange={event => onKeywordChange(event.target.value)} maxLength={150} type="search" enterKeyHint="search" />
            <button className="icon-action" aria-label="Submit marketplace search"><FiArrowRight /></button>
          </form>
          <nav aria-label="Explore marketplace" onClick={onClose}>
            {categories.length > 0 && <><h3>Find your category</h3><div className="explore-categories">{categories.map(category => {
              const details = categoryDetails[category.categoryName];
              const Icon = details?.icon || FiGrid;
              return <Link key={category.categoryId} to={"/products?category=" + encodeURIComponent(category.categoryName)}><Icon aria-hidden="true" /><strong>{category.categoryName}</strong><span>{details?.description || "Discover this category."}</span><ActionArrow className="explore-category-arrow" aria-hidden="true" /></Link>;
            })}</div></>}
            <h3>A different way to discover</h3>
            <div className="explore-edits">
              <Link to="/products?sortBy=createdAt&sortingOrder=desc"><span><strong>Just added</strong><span>Fresh finds from our sellers</span></span><ActionArrow aria-hidden="true" /></Link>
              <Link to="/products?sortBy=discountPercent&sortingOrder=desc"><span><strong>More for less</strong><span>Discover the biggest savings</span></span><ActionArrow aria-hidden="true" /></Link>
            </div>
            <Link className="explore-all" to="/products">Browse everything <ActionArrow aria-hidden="true" /></Link>
          </nav>
        </div>
      </DialogPanel>
    </Dialog>
  );
}
