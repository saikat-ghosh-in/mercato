import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { MenuItem } from "@headlessui/react";
import api from "../../backend/api";

export default function SellerNoticeIndicator({ menu = false }) {
  const [count, setCount] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    const load = async () => { try { const { data } = await api.get("/account/seller-notices", { signal: controller.signal }); if (!controller.signal.aborted) setCount(data.filter(notice => !notice.read).length); } catch { /* The inbox has a visible retry state. */ } };
    load(); const interval = window.setInterval(load, 60000); window.addEventListener("focus", load);
    return () => { controller.abort(); window.clearInterval(interval); window.removeEventListener("focus", load); };
  }, []);
  if (menu) return <MenuItem><Link to="/seller/dashboard?tab=notices">Seller notices {count > 0 && <span className="status-tag">{count} unread</span>}</Link></MenuItem>;
  return count > 0 && <Link className="seller-notice-banner" to="/seller/dashboard?tab=notices">You have {count} unread seller {count === 1 ? "notice" : "notices"}. Review commission updates and notice periods.<span>View notices →</span></Link>;
}
