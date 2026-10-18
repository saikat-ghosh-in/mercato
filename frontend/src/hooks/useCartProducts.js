import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import api from "../backend/api";

export function useCartProducts(cartItems) {
  const revision = useSelector(s => s.cart.updateSeq);
  const ids = [...new Set(cartItems.map(item => item.productId))].sort().join(",");
  const [result, setResult] = useState({ key: "", products: {} });
  useEffect(() => {
    if (!ids) return;
    const controller = new AbortController();
    Promise.all(ids.split(",").map(id => api.get("/public/products/" + id, { signal: controller.signal })
      .then(({ data }) => [id, data]).catch(() => [id, null])))
      .then(entries => { if (!controller.signal.aborted) setResult({ key: ids, products: Object.fromEntries(entries) }); });
    return () => controller.abort();
  }, [ids, revision]);
  const loading = Boolean(ids && result.key !== ids);
  return {
    items: cartItems.map(item => ({ ...item, product: result.products[item.productId] ?? null })),
    loading,
    failed: !loading && cartItems.some(item => !result.products[item.productId]),
  };
}
