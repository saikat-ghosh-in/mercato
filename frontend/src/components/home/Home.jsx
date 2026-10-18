import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { FiArrowRight, FiRefreshCw, FiPackage } from "react-icons/fi";
import api from "../../backend/api";
import ProductCard, { CardSkeleton } from "../product/ProductCard";

const images = { Fashion: "photo-1441986300917-64674bd600d8", "Home & Living": "photo-1600210492486-724fe5c67fb0", Electronics: "photo-1498049794561-7780e7231661", "Beauty & Care": "photo-1541643600914-78b084683601" };
const photo = id => "https://images.unsplash.com/" + id + "?auto=format&fit=crop&w=1000&q=85";
const tabs = [{ label: "Just landed", sort: "createdAt" }, { label: "The good finds", sort: "discountPercent" }];
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
    <section className="editorial-hero"><img className="campaign-image" src="/images/everyday-campaign.webp" alt="Two models in pale blue and black everyday clothing in sunlit architecture" width="2400" height="1200" fetchPriority="high" /><div className="hero-copy"><p className="eyebrow">THE EVERYDAY EDIT</p><h1>MADE FOR YOUR<br />EVERYDAY.</h1><p>Fresh perspectives. Everyday essentials.</p><Link className="button button-white" to="/products">Explore the edit <FiArrowRight /></Link></div></section>
    {categories.length > 0 && <section className="site-container home-section"><div className="section-heading"><h2>The latest</h2><Link className="text-link" to="/products">Shop all <FiArrowRight /></Link></div><div className="collection-grid">{categories.slice(0, 4).map(cat => <Link className="collection-card" key={cat.categoryId} to={"/products?category=" + encodeURIComponent(cat.categoryName)}><img src={photo(images[cat.categoryName] || "photo-1441986300917-64674bd600d8")} alt="" loading="lazy" width="900" height="1000" /><div><h3>{cat.categoryName}</h3><span className="button button-white">Explore</span></div></Link>)}</div></section>}
    <section className="site-container home-section"><div className="section-heading"><h2>Find your next favourite.</h2><Link className="text-link" to={"/products?sortBy=" + tab + "&sortingOrder=desc"}>View the collection <FiArrowRight /></Link></div><div className="product-tabs" role="group" aria-label="Featured collection">{tabs.map(t => <button key={t.sort} aria-pressed={tab === t.sort} onClick={() => { setCatalog(s => ({ ...s, loading: true, error: false })); setTab(t.sort); }} disabled={tab === t.sort}>{t.label}</button>)}<span role="status">{catalog.loading && catalog.products.length > 0 ? "Updating collection…" : ""}</span></div>
      {catalog.error && <div className="inline-error" role="alert">We couldn’t refresh the collection. <button onClick={() => { setCatalog(s => ({ ...s, loading: true, error: false })); setAttempt(a => a + 1); }}><FiRefreshCw /> Try again</button></div>}
      {catalog.loading && !catalog.products.length ? <div className="product-grid" aria-label="Loading products" aria-busy="true">{Array.from({ length: 6 }, (_, i) => <CardSkeleton key={i} />)}</div> : catalog.products.length ? <div className="product-grid" aria-busy={catalog.loading}>{catalog.products.map(p => <ProductCard key={p.productId} product={p} />)}</div> : !catalog.error && <div className="state-panel"><FiPackage /><h3>Something good is on its way.</h3><p>Check back for new arrivals.</p></div>}
    </section>
    <section className="brand-story site-container"><p className="eyebrow">YOUR EVERYDAY, REIMAGINED</p><h2>GOOD THINGS.<br />MORE POSSIBILITIES.</h2><p>For the spaces you love and the life you live.</p><Link className="button button-dark" to="/products">Discover Mercato <FiArrowRight /></Link></section>
  </div>;
}
