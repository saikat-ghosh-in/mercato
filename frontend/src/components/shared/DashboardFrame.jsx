import { useId, useRef, useState } from "react";
import { FiMenu, FiX } from "react-icons/fi";
import "./dashboard-frame.css";

export default function DashboardFrame({ title, subtitle, items, activeTab, onSelect, children }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuId = useId();
  const heading = useRef(null);
  return <div className="dashboard-frame"><div className="dashboard-container">
    <header className="dashboard-header"><div><h1 ref={heading} tabIndex={-1}>{title}</h1><p>{subtitle}</p></div><button type="button" className="dashboard-menu-toggle icon-action" aria-label="Toggle dashboard navigation" aria-expanded={menuOpen} aria-controls={menuId} onClick={() => setMenuOpen(value => !value)}>{menuOpen ? <FiX aria-hidden="true" /> : <FiMenu aria-hidden="true" />}</button></header>
    <nav id={menuId} className="dashboard-tabs" data-open={menuOpen || undefined} aria-label={`${title} navigation`}>
      {items.map(({ id, label, icon: Icon, count = 0 }) => <button type="button" key={id} aria-pressed={activeTab === id} aria-label={count ? `${label}, ${count} unread` : label} onClick={() => { onSelect(id); if (menuOpen) heading.current?.focus({ preventScroll: true }); setMenuOpen(false); }}><Icon aria-hidden="true" /><span>{label}</span>{count > 0 && <span className="dashboard-notice-count" aria-hidden="true">{count > 99 ? "99+" : count}</span>}</button>)}
    </nav>
    <div className="dashboard-content">{children}</div>
  </div></div>;
}
