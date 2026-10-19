import { useCallback, useEffect, useRef, useState } from "react";
import api from "../backend/api";

export default function useSellerNotices(enabled) {
  const [notices, setNotices] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const sequence = useRef(0);
  const mounted = useRef(false);
  const load = useCallback(async signal => {
    if (!enabled) return;
    const request = ++sequence.current;
    try {
      const { data } = await api.get("/account/seller-notices", { signal });
      if (mounted.current && !signal?.aborted && request === sequence.current) {
        const now = Date.now();
        setNotices(data.map(notice => ({ ...notice, daysRemaining: notice.effectiveAt ? Math.max(0, Math.ceil((new Date(notice.effectiveAt).getTime() - now) / 86400000)) : null })));
        setError("");
      }
    } catch {
      if (mounted.current && !signal?.aborted && request === sequence.current) setError("Notices couldn’t load. Please try again.");
    } finally {
      if (mounted.current && !signal?.aborted && request === sequence.current) setLoaded(true);
    }
  }, [enabled]);
  useEffect(() => {
    mounted.current = true;
    const controller = new AbortController();
    const refresh = () => load(controller.signal);
    refresh();
    const interval = enabled ? window.setInterval(refresh, 60000) : null;
    window.addEventListener("focus", refresh);
    return () => { mounted.current = false; controller.abort(); window.clearInterval(interval); window.removeEventListener("focus", refresh); };
  }, [enabled, load]);
  const markRead = async id => {
    try {
      await api.post(`/account/seller-notices/${id}/read`);
      if (mounted.current) {
        sequence.current++; // An older unread snapshot cannot resurrect the badge.
        setNotices(items => items.map(item => item.id === id ? { ...item, read: true } : item));
        setError("");
      }
    } catch { if (mounted.current) setError("Couldn’t mark this notice read. Please retry."); }
  };
  return { notices, loaded, error, refresh: () => load(), markRead, unread: notices.filter(n => !n.read).length };
}
