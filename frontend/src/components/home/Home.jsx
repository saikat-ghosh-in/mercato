import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { FiArrowRight, FiArrowUpRight, FiShield, FiPackage, FiTruck, FiRefreshCw, FiHeart } from "react-icons/fi";
import api from "../../backend/api";
import ProductCard, { CardSkeleton } from "../product/ProductCard";

const photo = (id, width = 900) => "https://images.unsplash.com/" + id + "?auto=format&fit=crop&w=" + width + "&q=85";
const collectionImages = ["photo-1600210492486-724fe5c67fb0", "photo-1441986300917-64674bd600d8", "photo-1498049794561-7780e7231661", "photo-1541643600914-78b084683601"];
const tabs = [{ label: "Just landed", sort: "createdAt" }, { label: "Good deals", sort: "discountPercent" }];

export default function HomePage() {
  const categories = useSelector(s => s.categories.categories) ?? [];
  const [tab, setTab] = useState("createdAt");
  const [attempt, setAttempt] = useState(0);
  const [catalog, setCatalog] = useState({ products: [], loading: true, error: false });
  useEffect(() => {
    const controller = new AbortController();
    api.get("/public/products", { params: { pageSize: 8, sortBy: tab, sortingOrder: "desc" }, signal: controller.signal })
      .then(({ data }) => setCatalog({ products: data?.content ?? [], loading: false, error: false }))
      .catch(() => { if (!controller.signal.aborted) setCatalog({ products: [], loading: false, error: true }); });
    return () => controller.abort();
  }, [tab, attempt]);
  const changeTab = sort => { setCatalog(s => ({ ...s, loading: true, error: false })); setTab(sort); };
  return <div className="home-page">
    <section className="site-container hero-wrap">
      <div className="editorial-hero">
        <div className="hero-copy"><p className="eyebrow"><span /> THE EVERYDAY, REIMAGINED</p>
          <h1>Good things.<br />Great <em>everydays.</em></h1>
          <p>For the spaces you love and the life you live.<br className="desktop-break" /> Discover your next favourite, all in one place.</p>
          <Link className="button button-dark" to="/products">Explore the collection <FiArrowRight /></Link>
          <div className="hero-footnote"><span className="tiny-line" /> Thoughtful finds. Refreshingly simple.</div>
        </div>
        <div className="hero-photo"><img src={photo("photo-1600210492486-724fe5c67fb0", 1400)} alt="A sunlit living room with warm wood, natural textures and considered details" fetchPriority="high" />
          <span className="photo-caption">MAKE ROOM FOR THE GOOD THINGS</span>
          <Link className="hero-photo-link" to="/products">Find your everyday favourites <span><FiArrowUpRight /></span></Link>
        </div>
      </div>
      <div className="service-strip">
        <div><FiTruck /><span><strong>A little extra, on us</strong><small>Free delivery above ₹{import.meta.env.VITE_FREE_SHIPPING_ABOVE || 999}</small></span></div>
        <div><FiShield /><span><strong>Pay with confidence</strong><small>Secure checkout with Cashfree</small></span></div>
        <div><FiPackage /><span><strong>Keep it all in view</strong><small>Track orders in your account</small></span></div>
        <div><FiHeart /><span><strong>Find it. Love it. Save it.</strong><small>Your favourites, in one place</small></span></div>
      </div>
    </section>
    {categories.length > 0 && <section className="site-container home-section category-section">
      <div className="section-heading"><div><p className="eyebrow">A WORLD OF POSSIBILITIES</p><h2>Find your kind of good.</h2></div><Link className="text-link" to="/products">All categories <FiArrowUpRight /></Link></div>
      <div className="collection-grid">{categories.slice(0, 4).map((cat, i) => <Link className={"collection-card collection-" + i} key={cat.categoryId} to={"/products?category=" + encodeURIComponent(cat.categoryName)}>
        <img src={photo(collectionImages[i], 650)} alt="" loading="lazy" /><div><span>EXPLORE THE COLLECTION</span><h3>{cat.categoryName}</h3><span className="collection-arrow"><FiArrowUpRight /></span></div>
      </Link>)}</div>
    </section>}
    <section className="home-products"><div className="site-container home-section">
      <div className="section-heading"><div><p className="eyebrow">WORTH A CLOSER LOOK</p><h2>Your next favourite is here.</h2></div><Link className="text-link" to={"/products?sortBy=" + tab + "&sortingOrder=desc"}>Shop the edit <FiArrowUpRight /></Link></div>
      <div className="product-tabs" role="group" aria-label="Featured collection">{tabs.map(t => <button key={t.sort} aria-pressed={tab === t.sort} onClick={() => changeTab(t.sort)} disabled={tab === t.sort}>{t.label}</button>)}<span>Good finds, fresh perspectives.</span></div>
      {catalog.error ? <div className="state-panel" role="alert"><FiRefreshCw /><h3>We couldn’t load the collection.</h3><p>Please try again in a moment.</p><button className="button button-dark" onClick={() => { setCatalog(s => ({ ...s, loading: true })); setAttempt(a => a + 1); }}>Try again</button></div>
        : catalog.loading ? <div className="product-grid" aria-label="Loading products" aria-busy="true">{Array.from({ length: 8 }, (_, i) => <CardSkeleton key={i} />)}</div>
        : catalog.products.length ? <div className="product-grid">{catalog.products.map(p => <ProductCard key={p.productId} product={p} />)}</div>
        : <div className="state-panel"><FiPackage /><h3>Good things are on their way.</h3><p>Check back soon for new arrivals.</p><Link to="/products" className="text-link">Browse the catalog <FiArrowRight /></Link></div>}
    </div></section>
    <section className="site-container home-section"><div className="brand-story"><p className="eyebrow">THE MERCATO WAY</p><h2>Less searching.<br /><em>More finding.</em></h2><div><p>From a small upgrade to a whole new chapter. Bring together the things that make your everyday feel a little more like you.</p><Link to="/products" className="text-link">Make yourself at home <FiArrowUpRight /></Link></div></div></section>
  </div>;
}
