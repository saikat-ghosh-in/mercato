import React, { useEffect, lazy, Suspense } from 'react';
import { Route, BrowserRouter, Routes, Link, useLocation, Navigate } from 'react-router-dom';
import './App.css'
const routeLoaders = [];
function lazyPage(loader) {
  let promise;
  const load = () => promise ??= loader();
  routeLoaders.push(load);
  return lazy(load);
}
const warmRoutes = () => { routeLoaders.forEach(load => { load().catch(() => {}); }); };
const ProductListingPage = lazyPage(() => import("./components/product/ProductListingPage"));
import HomePage from './components/home/Home';
import Header from './components/Header';
const Cart = lazyPage(() => import("./components/cart/Cart"));

import toast, { Toaster } from 'react-hot-toast';
const LogIn = lazyPage(() => import("./components/auth/LogIn"));
import PrivateRoute from "./components/PrivateRoute";
const Register = lazyPage(() => import("./components/auth/Register"));
const Checkout = lazyPage(() => import("./components/checkout/Checkout"));
const PaymentConfirmation = lazyPage(() => import("./components/payment/PaymentConfirmation"));
const Orders = lazyPage(() => import("./components/orders/Orders"));
import { useDispatch, useSelector } from 'react-redux';
import { fetchCategories } from './reduxStore/actions/categoryActions';
import { initializeAuth, logOutUser } from './reduxStore/actions/authActions';
import { syncCartFromBackend } from './reduxStore/actions/cartActions';
import { fetchWishlistIds } from './reduxStore/actions/wishlistActions';
const Profile = lazyPage(() => import("./components/user/Profile"));
import { SubHeaderProvider } from './components/shared/SubHeaderContext';
import SubHeaderSlot from './components/shared/SubHeaderSlot';
const ProductDetailsPage = lazyPage(() => import("./components/product/ProductDetailsPage"));
const CashfreePayment = lazyPage(() => import("./components/payment/CashfreePayment"));
const SellerDashboard = lazyPage(() => import("./components/seller/SellerDashboard"));
const AdminDashboard = lazyPage(() => import("./components/admin/AdminDashboard"));
import { isTokenExpired } from './utils/tokenManager';
const ProfileEdit = lazyPage(() => import("./components/user/ProfileEdit"));
const Addresses = lazyPage(() => import("./components/user/Addresses"));
const EmailVerificationPending = lazyPage(() => import("./components/auth/EmailVerificationPending"));
const VerifyEmail = lazyPage(() => import("./components/auth/VerifyEmail"));
const ForgotPassword = lazyPage(() => import("./components/auth/ForgotPassword"));
const ResetPassword = lazyPage(() => import("./components/auth/ResetPassword"));
const Wishlist = lazyPage(() => import("./components/wishlist/Wishlist"));
const AccountDeactivated = lazyPage(() => import("./components/auth/AccountDeactivated"));
import Footer from './components/Footer';
const Help = lazyPage(() => import("./components/Help"));
import ErrorBoundary from './components/ErrorBoundary';
import { accountLanding } from './utils/accountRouting';
const SellerPolicies = lazyPage(() => import("./components/seller/SellerPolicies"));

function StorefrontEntry() {
  const { user, userDetails } = useSelector(s => s.auth);
  if (user && !userDetails) return <div className="site-container route-loading" role="status" aria-label="Loading your workspace"><div /><div /></div>;
  const destination = accountLanding(userDetails);
  return destination === "/" ? <HomePage /> : <Navigate to={destination} replace />;
}

function PageFrame({ children }) {
  const { pathname } = useLocation();
  const kind = /^\/(login|register|forgot-password|reset-password|verify-email|account-deactivated)/.test(pathname) ? "auth" : /^\/(admin|seller)/.test(pathname) ? "management" : pathname.startsWith("/checkout") ? "checkout" : pathname.startsWith("/orders") ? "orders" : /^\/(profile|edit-profile|addresses|wishlist)/.test(pathname) ? "account" : "store";
  useEffect(() => { const timer = window.setTimeout(warmRoutes, 1800); return () => window.clearTimeout(timer); }, []);
  return <main id="main-content" tabIndex={-1} className={"app-page " + kind + "-page"}>{children}</main>;
}
function NavigationReset() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  return null;
}

function App() {
  const dispatch = useDispatch();
  const user = useSelector((state) => state.auth?.user);

  useEffect(() => {
    dispatch(initializeAuth());
  }, [dispatch]);

  useEffect(() => {
    if (user && user.token) {
      if (isTokenExpired(user.tokenExpirationTime)) {
        console.warn("Token expired, logging out");
        toast.error("Your session has expired. Please log in again.");
        dispatch(logOutUser());
      }
    }
  }, [user, dispatch]);

  useEffect(() => {
    dispatch(fetchCategories());
  }, [dispatch]);

  useEffect(() => {
    dispatch(syncCartFromBackend());
  }, [dispatch]);

  useEffect(() => {
    if (user?.token) {
      dispatch(fetchWishlistIds());
    }
  }, [user?.token, dispatch]);


  return (
    <React.Fragment>
      <BrowserRouter>
        <NavigationReset />
        <SubHeaderProvider>
          <Header />
          <SubHeaderSlot />
          <PageFrame>
          <ErrorBoundary>
          <Suspense fallback={<div className="site-container route-loading" role="status" aria-label="Loading page"><div /><div /></div>}><Routes>
            <Route path='/help' element={<Help />} />
            <Route path='/' element={<StorefrontEntry />} />
            <Route path='/seller-policies' element={<SellerPolicies />} />
            <Route path='/products' element={<ProductListingPage />} />
            <Route path='/products/:productId' element={<ProductDetailsPage />} />
            <Route path='/cart' element={<Cart />} />

            <Route path='/' element={<PrivateRoute publicPage />}>
              <Route path='/login' element={<LogIn />} />
              <Route path='/register' element={<Register />} />
            </Route>

              <Route path="/forgot-password" element={<ForgotPassword />} />
              <Route path="/reset-password" element={<ResetPassword />} />
              <Route path="/verify-email-pending" element={<EmailVerificationPending />} />
              <Route path="/verify-email" element={<VerifyEmail />} />
              <Route path="/account-deactivated" element={<AccountDeactivated />} />
            <Route path='/' element={<PrivateRoute />}>
              <Route path='/profile' element={<Profile />} />
              <Route path="/wishlist" element={<Wishlist />} />
              <Route path="/edit-profile" element={<ProfileEdit />} />
              <Route path="/addresses" element={<Addresses />} />
              <Route path='/checkout' element={<Checkout />}>
                <Route path="cashfree-payment" element={<CashfreePayment />} />
                <Route path="payment-confirmation" element={<PaymentConfirmation />} />
              </Route>
              <Route path='/payment-confirmation' element={<PaymentConfirmation />} />
              <Route path='/orders' element={<Orders />} />
            </Route>

            <Route path='/' element={<PrivateRoute roles={["ROLE_ADMIN"]} />}>
              <Route path='/admin/dashboard' element={<AdminDashboard />} />
            </Route>

            <Route path='/' element={<PrivateRoute roles={["ROLE_SELLER", "ROLE_ADMIN"]} allowSellerApplicant />}>
              <Route path='/seller/dashboard' element={<SellerDashboard />} />
            </Route>
            <Route path='*' element={<div className="site-container home-section"><div className="state-panel"><p className="eyebrow">404 · PAGE NOT FOUND</p><h1>Let’s find something good.</h1><p>This page may have moved or the link may be incomplete.</p><Link to="/products" className="button button-dark">Explore the collection</Link></div></div>} />
          </Routes></Suspense>
          </ErrorBoundary>
          </PageFrame>
          <Footer />

        </SubHeaderProvider>
      </BrowserRouter>
      <Toaster position='bottom-center' />
    </React.Fragment>
  );
}

export default App;
