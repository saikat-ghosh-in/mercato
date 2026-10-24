import { Spinner } from './Spinner';

export function Skeleton({ shape = 'text', className = '', style }) {
  return <span aria-hidden="true" data-shape={shape} className={`skeleton ${className}`} style={style} />;
}
function Lines({ count = 3 }) {
  return <div className="skeleton-lines">{Array.from({ length: count }, (_, index) => <Skeleton key={index} style={{ width: index === count - 1 ? '62%' : index === 0 ? '42%' : '100%' }} />)}</div>;
}
export function ProductSkeleton() {
  return <div className="product-card skeleton-product" aria-hidden="true"><Skeleton shape="block" className="skeleton-product-image" /><div className="product-copy"><Lines /><Skeleton className="skeleton-price" /></div></div>;
}
function ListRows({ count = 3 }) {
  return <div className="skeleton-list">{Array.from({ length: count }, (_, index) => <div className="skeleton-list-row" key={index}><Skeleton shape="block" className="skeleton-thumbnail" /><Lines /><Skeleton shape="block" className="skeleton-action" /></div>)}</div>;
}
function Summary() {
  return <div className="skeleton-summary"><Skeleton className="skeleton-title" /><Lines count={4} /><Skeleton shape="block" className="skeleton-submit" /></div>;
}
/** Initial loads only: callers retain real content during background refreshes. */
export function LoadingLayout({ variant = 'list', label = 'Loading content', className = '', rows = 4, cols = 5 }) {
  let content;
  switch (variant) {
    case 'catalog': content = <div className="product-grid">{Array.from({ length: 6 }, (_, i) => <ProductSkeleton key={i} />)}</div>; break;
    case 'product': content = <div className="pdp-layout skeleton-pdp"><Skeleton shape="block" className="skeleton-gallery" /><div className="skeleton-product-detail"><Skeleton className="skeleton-title" /><Lines /><Skeleton className="skeleton-price" /><Skeleton shape="block" className="skeleton-submit" /><Lines count={6} /></div></div>; break;
    case 'bag': case 'checkout': content = <div className="bag-layout"><div><ListRows /><div className="skeleton-address-grid"><Summary /><Summary /></div></div><Summary /></div>; break;
    case 'profile': content = <div className="skeleton-profile"><div className="skeleton-profile-heading"><Skeleton shape="circle" className="skeleton-avatar" /><Lines count={2} /></div><div className="skeleton-address-grid"><Summary /><Summary /></div><ListRows count={2} /></div>; break;
    case 'form': content = <div className="skeleton-form"><Skeleton className="skeleton-title" />{Array.from({ length: 4 }, (_, i) => <div key={i}><Skeleton className="skeleton-label" /><Skeleton shape="block" className="skeleton-input" /></div>)}<Skeleton shape="block" className="skeleton-submit" /></div>; break;
    case 'dashboard': content = <><div className="skeleton-stats">{Array.from({ length: 4 }, (_, i) => <Summary key={i} />)}</div><ListRows count={4} /></>; break;
    case 'table': content = <div className="skeleton-table">{Array.from({ length: rows }, (_, i) => <div className="skeleton-table-row" key={i} style={{ '--skeleton-columns': cols }}>{Array.from({ length: cols }, (_, j) => <Skeleton key={j} style={{ width: j ? '68%' : '85%' }} />)}</div>)}</div>; break;
    case 'address': content = <div className="skeleton-address-grid"><Summary /><Summary /></div>; break;
    case 'payment': content = <div className="skeleton-payment"><Spinner size={24} /><Skeleton className="skeleton-title" /><Lines /><Skeleton shape="block" className="skeleton-submit" /></div>; break;
    default: content = <ListRows count={rows} />;
  }
  return <div className={`loading-layout loading-${variant} ${className}`}><span className="sr-only" role="status">{label}</span><div aria-hidden="true" aria-busy="true">{content}</div></div>;
}
export function RouteSkeleton({ pathname = '/' }) {
  const variant = /^\/(admin|seller)\/dashboard/.test(pathname) ? 'dashboard'
    : /^\/products\//.test(pathname) ? 'product' : pathname === '/products' || pathname === '/wishlist' ? 'catalog'
    : pathname === '/cart' ? 'bag' : pathname === '/checkout' ? 'checkout'
    : /payment/.test(pathname) ? 'payment' : /login|register|password|auth\/mfa/.test(pathname) ? 'form'
    : /profile/.test(pathname) ? 'profile' : 'list';
  return <div className="site-container skeleton-page"><LoadingLayout variant={variant} label="Loading page" /></div>;
}
