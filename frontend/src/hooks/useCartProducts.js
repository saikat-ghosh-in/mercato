import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import api from "../backend/api";
import { createProductMetadataCache } from "../utils/productMetadataCache.js";

export function useCartProducts(cartItems) {
  const revision = useSelector(s => s.cart.updateSeq);
  const ids = [...new Set(cartItems.map(item => item.productId))].sort().join(",");
  const [result, setResult] = useState({ key: "", products: {} });
  const cache = useRef(null);
  useEffect(() => {
    if (!ids) return;
    if (!cache.current) cache.current = createProductMetadataCache(async (id, signal) => (await api.get("/public/products/" + id, { signal })).data);
    // Quantity/pricing acknowledgements do not invalidate product descriptions or images.
    // Only fetch missing metadata; a later cart revision retries unsuccessful lookups.
    const missing = cache.current.missing(ids.split(","));
    if (!missing.length) return;
    const controller = new AbortController();
    cache.current.load(missing, controller.signal).then(products => {
      if (products) setResult({ key: ids, products });
    });
    return () => controller.abort();
  }, [ids, revision]);
  const loading = Boolean(ids && result.key !== ids && ids.split(",").some(id => !result.products[id]));
  return {
    items: cartItems.map(item => ({ ...item, product: result.products[item.productId] ?? null })),
    loading,
    failed: !loading && cartItems.some(item => !result.products[item.productId]),
  };
}
