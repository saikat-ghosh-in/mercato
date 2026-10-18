import { Link } from "react-router-dom";
export default function Brand() {
  return <Link to="/" className="wordmark" aria-label="Mercato home"><svg viewBox="0 0 40 32" aria-hidden="true"><path d="M2 2 20 16 38 2v28L20 16 2 30Z" fill="currentColor"/><path d="m2 2 18 14L2 30V18l8-6Z" fill="currentColor" opacity=".5"/></svg><span>mercato</span></Link>;
}
