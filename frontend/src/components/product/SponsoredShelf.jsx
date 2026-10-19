import { useEffect, useState } from "react";
import api from "../../backend/api";
import ProductCard from "./ProductCard";
export default function SponsoredShelf({ placement, category = "", keyword = "" }) {
  const [items, setItems] = useState([]);
  useEffect(() => {
    const controller = new AbortController();
    let sequence = 0;
    const refresh = () => {
      const request = ++sequence;
      return api.get("/public/promotions", { params: { placement, category, keyword }, signal: controller.signal })
        .then(({ data }) => { if (!controller.signal.aborted && request === sequence) setItems(data); })
        .catch(() => { if (!controller.signal.aborted && request === sequence) setItems([]); });
    };
    refresh();
    const interval = setInterval(refresh, 60000);
    return () => { controller.abort(); clearInterval(interval); };
  }, [placement, category, keyword]);
  if (!items.length) return null;
  return <section className="sponsored-shelf"><div className="section-heading"><h2>Discover from our sellers.</h2><span className="eyebrow">SPONSORED</span></div><div className="sponsored-products">{items.map(item => <div key={item.product.productId}><p className="sponsored-label">Sponsored</p><ProductCard product={item.product} showPurchase={placement === "catalog"} /></div>)}</div></section>;
}
