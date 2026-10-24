import { Link } from "react-router-dom";
export default function AccountDeactivated() {
  return <main className="auth-page"><section className="paused-account"><h1>Account paused</h1><p>Your account and order history are retained. If you paused your own account, sign in again to restore it.</p><p>If an administrator deactivated your account, contact support through Help. Signing in will not override that decision.</p><div className="checkout-inline-actions"><Link className="button button-dark" to="/login">Sign in</Link><Link className="button button-outline" to="/help">Help</Link></div></section></main>;
}
