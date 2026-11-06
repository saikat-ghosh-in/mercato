import { useEffect, useState } from "react";
import api from "../backend/api";

export default function useProductReviews(productId) {
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!productId) return;
    const controller = new AbortController();
    api.get(`/public/products/${encodeURIComponent(productId)}/reviews`, { params: { page }, signal: controller.signal })
      .then(({ data }) => {
        if (!controller.signal.aborted) {
          setResult(previous => ({
            ...data,
            page,
            reviews: page ? [...previous.reviews, ...data.reviews.filter(review => !previous.reviews.some(item => item.id === review.id))] : data.reviews,
          }));
        }
      })
      .catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [productId, page, retry]);

  return {
    result,
    failed,
    loading: !failed && result?.page !== page,
    loadMore: () => setPage(page + 1),
    retry: () => { setFailed(false); setRetry(value => value + 1); },
  };
}
