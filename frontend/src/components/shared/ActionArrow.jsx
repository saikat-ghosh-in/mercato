// Navigation indicator from the supplied reference. It has no independent hit target.
export default function ActionArrow({ className = "" }) {
  return <svg className={`action-arrow ${className}`} viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M7 17 17 7M7 7h10v10" /></svg>;
}
