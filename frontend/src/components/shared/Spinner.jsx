/** Segmented, current-color loading indicator inspired by Interfere's spinner. */
export function Spinner({ size = 16, label, className = '' }) {
  return <span className={`spinner ${className}`} style={{ width: size, height: size }} role={label ? 'status' : undefined} aria-hidden={label ? undefined : true}>
    {Array.from({ length: 12 }, (_, index) => <span key={index} className="spinner-segment" style={{ '--segment': index }} />)}
    {label && <span className="sr-only">{label}</span>}
  </span>;
}
