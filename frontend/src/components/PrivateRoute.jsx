import { LoadingLayout } from "./shared/LoadingStates";
import { useSelector } from 'react-redux'
import { Navigate, Outlet, useLocation, Link } from 'react-router-dom';
import { accountLanding } from '../utils/accountRouting';

const PrivateRoute = ({ publicPage = false, roles = [], allowSellerApplicant = false }) => {
    const { user, mfa } = useSelector((state) => state.auth);
    const userDetails = useSelector((state) => state.auth?.userDetails);
    const location = useLocation();
    const authenticated = Boolean(user);
    if (["ENROLL_REQUIRED", "CHALLENGE_REQUIRED", "STEP_UP_REQUIRED"].includes(mfa?.state)) {
        return <Navigate to={`/auth/mfa?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />;
    }

    if (publicPage) {
        return authenticated ? <Navigate to={accountLanding(userDetails || user)} replace /> : <Outlet />;
    }

    if (!authenticated) {
        const redirect = encodeURIComponent(location.pathname + location.search);
        return <Navigate to={`/login?redirect=${redirect}`} replace />;
    }

    if (roles.length > 0 && !userDetails) return <div className="site-container skeleton-page"><LoadingLayout variant="form" label="Checking account permissions" /></div>;
    if (roles.length > 0) {
        const userRoles = userDetails?.roles ?? [];
        const hasRole = roles.some(r => userRoles.includes(r)) || (allowSellerApplicant && ["PENDING", "REJECTED"].includes(userDetails?.sellerStatus));
        if (!hasRole) return <div className="site-container state-panel"><h1>Access restricted</h1><p>This page requires a different account role.</p><Link to="/profile" className="button button-dark">Your account</Link></div>;
    }

    return <Outlet />;
};

export default PrivateRoute;
