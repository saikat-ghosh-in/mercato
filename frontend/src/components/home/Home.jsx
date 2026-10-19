import SponsoredShelf from "../product/SponsoredShelf";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { FiArrowRight, FiRefreshCw, FiPackage } from "react-icons/fi";
import api from "../../backend/api";
import ProductCard, { CardSkeleton } from "../product/ProductCard";

const images = { Fashion: "photo-1441986300917-64674bd600d8", "Home & Living": "photo-1600210492486-724fe5c67fb0", Electronics: "photo-1498049794561-7780e7231661", "Beauty & Care": "photo-1541643600914-78b084683601" };
const photo = id => "https://images.unsplash.com/" + id + "?auto=format&fit=crop&w=1000&q=85";
const tabs = [{ label: "Just landed", sort: "createdAt" }, { label: "The good finds", sort: "discountPercent" }];
function RevealSection({ children, className, id }) {
  const section = useRef(null);
  useEffect(() => {
    const element = section.current;
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (!element || motion.matches || !("IntersectionObserver" in window) || element.getBoundingClientRect().top < window.innerHeight) return;
    element.dataset.reveal = "pending";
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        delete element.dataset.reveal;
        observer.disconnect();
      }
    }, { threshold: 0.05 });
    const reveal = () => { delete element.dataset.reveal; observer.disconnect(); };
    observer.observe(element);
    motion.addEventListener("change", reveal);
    return () => { observer.disconnect(); motion.removeEventListener("change", reveal); };
  }, []);
  return <section id={id} className={`home-reveal ${className}`} ref={section}>{children}</section>;
}
export default function HomePage() {
  const categories = useSelector(s => s.categories.categories) ?? [];
  const [tab, setTab] = useState("createdAt");
  const [attempt, setAttempt] = useState(0);
  const [catalog, setCatalog] = useState({ products: [], loading: true, error: false });
  useEffect(() => {
    const controller = new AbortController();
    api.get("/public/products", { params: { pageSize: 6, sortBy: tab, sortingOrder: "desc" }, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) setCatalog({ products: data?.content ?? [], loading: false, error: false }); })
      .catch(() => { if (!controller.signal.aborted) setCatalog(s => ({ ...s, loading: false, error: true })); });
    return () => controller.abort();
  }, [tab, attempt]);
  return <div className="home-page">
    <section className="marketplace-hero"><div className="marketplace-hero-copy"><p className="eyebrow">MANY SELLERS. ENDLESS DISCOVERY.</p><h1>A WORLD<br />OF GOOD<br /><span>FINDS.</span></h1><p>For your home. Your routine. Your next big idea. Discover tech, style and everyday essentials from independent sellers.</p><div className="marketplace-hero-actions"><Link className="button button-dark button-prominent" to="/products">Explore the marketplace <FiArrowRight /></Link>{categories.length > 0 && <a className="text-link" href="#collections">Shop by category</a>}</div></div><div className="marketplace-hero-image"><img src="/images/marketplace-campaign.webp" alt="A camera, headphones, sneaker, cotton shirt, ceramic vase, lamp and skincare arranged on pale blue and cream platforms" width="1448" height="1086" fetchPriority="high" /><span className="marketplace-hero-caption">Different sellers. One place to discover.</span></div></section>
    {categories.length > 0 && <RevealSection id="collections" className="site-container home-section"><div className="section-heading"><h2>A little of everything.</h2><Link className="text-link" to="/products">Shop all <FiArrowRight /></Link></div><div className="collection-grid">{categories.slice(0, 4).map(cat => <Link className="collection-card" key={cat.categoryId} to={"/products?category=" + encodeURIComponent(cat.categoryName)}><img src={photo(images[cat.categoryName] || "photo-1441986300917-64674bd600d8")} alt="" loading="lazy" width="900" height="1000" /><div><h3>{cat.categoryName}</h3><span className="button button-white">Explore</span></div></Link>)}</div></RevealSection>}
    <div className="site-container"><SponsoredShelf placement="home" /></div>
    <RevealSection id="new-featured" className="site-container home-section"><div className="section-heading"><h2>Find your next favourite.</h2><Link className="text-link" to={"/products?sortBy=" + tab + "&sortingOrder=desc"}>View the collection <FiArrowRight /></Link></div><div className="product-tabs" role="group" aria-label="Featured collection">{tabs.map(t => <button key={t.sort} aria-pressed={tab === t.sort} onClick={() => { setCatalog(s => ({ ...s, loading: true, error: false })); setTab(t.sort); }} disabled={tab === t.sort}>{t.label}</button>)}<span role="status">{catalog.loading && catalog.products.length > 0 ? "Updating collection…" : ""}</span></div>
      {catalog.error && <div className="inline-error" role="alert">We couldn’t refresh the collection. <button onClick={() => { setCatalog(s => ({ ...s, loading: true, error: false })); setAttempt(a => a + 1); }}><FiRefreshCw /> Try again</button></div>}
      {catalog.loading && !catalog.products.length ? <div className="product-grid" aria-label="Loading products" aria-busy="true">{Array.from({ length: 6 }, (_, i) => <CardSkeleton key={i} />)}</div> : catalog.products.length ? <div className="product-grid" aria-busy={catalog.loading}>{catalog.products.map(p => <ProductCard key={p.productId} product={p} />)}</div> : !catalog.error && <div className="state-panel"><FiPackage /><h3>Something good is on its way.</h3><p>Check back for new arrivals.</p></div>}
    </RevealSection>
    <RevealSection className="brand-story site-container"><p className="eyebrow">THE MERCATO MARKETPLACE</p><h2>YOUR WORLD.<br />MORE POSSIBILITIES.</h2><p>Different shops, fresh discoveries. Bring your favourites together.</p><Link className="button button-dark" to="/products">Find something good <FiArrowRight /></Link></RevealSection>
  </div>;
}
