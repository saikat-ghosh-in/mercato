import React, { useEffect, lazy, Suspense } from 'react';
import { Route, BrowserRouter, Routes, Link, useLocation } from 'react-router-dom';
import './App.css'
const ProductListingPage = lazy(() => import("./components/product/ProductListingPage"));
import HomePage from './components/home/Home';
import Header from './components/Header';
const Cart = lazy(() => import("./components/cart/Cart"));

import toast, { Toaster } from 'react-hot-toast';
const LogIn = lazy(() => import("./components/auth/LogIn"));
import PrivateRoute from "./components/PrivateRoute";
const Register = lazy(() => import("./components/auth/Register"));
const Checkout = lazy(() => import("./components/checkout/Checkout"));
const PaymentConfirmation = lazy(() => import("./components/payment/PaymentConfirmation"));
const Orders = lazy(() => import("./components/orders/Orders"));
import { useDispatch, useSelector } from 'react-redux';
import { fetchCategories } from './reduxStore/actions/categoryActions';
import { initializeAuth, logOutUser } from './reduxStore/actions/authActions';
import { syncCartFromBackend } from './reduxStore/actions/cartActions';
import { fetchWishlistIds } from './reduxStore/actions/wishlistActions';
const Profile = lazy(() => import("./components/user/Profile"));
import { SubHeaderProvider } from './components/shared/SubHeaderContext';
import SubHeaderSlot from './components/shared/SubHeaderSlot';
const ProductDetailsPage = lazy(() => import("./components/product/ProductDetailsPage"));
const CashfreePayment = lazy(() => import("./components/payment/CashfreePayment"));
const SellerDashboard = lazy(() => import("./components/seller/SellerDashboard"));
const AdminDashboard = lazy(() => import("./components/admin/AdminDashboard"));
import { isTokenExpired } from './utils/tokenManager';
const ProfileEdit = lazy(() => import("./components/user/ProfileEdit"));
const Addresses = lazy(() => import("./components/user/Addresses"));
const EmailVerificationPending = lazy(() => import("./components/auth/EmailVerificationPending"));
const VerifyEmail = lazy(() => import("./components/auth/VerifyEmail"));
const ForgotPassword = lazy(() => import("./components/auth/ForgotPassword"));
const ResetPassword = lazy(() => import("./components/auth/ResetPassword"));
const Wishlist = lazy(() => import("./components/wishlist/Wishlist"));
const AccountDeactivated = lazy(() => import("./components/auth/AccountDeactivated"));
import Footer from './components/Footer';
const Help = lazy(() => import("./components/Help"));
import ErrorBoundary from './components/ErrorBoundary';

function NavigationReset() {
  const { pathname, search } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname, search]);
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
          <main id="main-content" tabIndex={-1}>
          <ErrorBoundary>
          <Suspense fallback={<div className="site-container home-section" role="status">Loading your next good find…</div>}><Routes>
            <Route path='/help' element={<Help />} />
            <Route path='/' element={<HomePage />} />
            <Route path='/products' element={<ProductListingPage />} />
            <Route path='/products/:productId' element={<ProductDetailsPage />} />
            <Route path='/cart' element={<Cart />} />

            <Route path='/' element={<PrivateRoute publicPage />}>
              <Route path='/login' element={<LogIn />} />
              <Route path='/register' element={<Register />} />
              <Route path="/forgot-password" element={<ForgotPassword />} />
              <Route path="/reset-password" element={<ResetPassword />} />
              <Route path="/verify-email-pending" element={<EmailVerificationPending />} />
              <Route path="/verify-email" element={<VerifyEmail />} />
              <Route path="/account-deactivated" element={<AccountDeactivated />} />
            </Route>

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

            <Route path='/' element={<PrivateRoute roles={["ROLE_SELLER", "ROLE_ADMIN"]} />}>
              <Route path='/seller/dashboard' element={<SellerDashboard />} />
            </Route>
            <Route path='*' element={<div className="site-container home-section"><div className="state-panel"><p className="eyebrow">404 · PAGE NOT FOUND</p><h1>Let’s find something good.</h1><p>This page may have moved or the link may be incomplete.</p><Link to="/products" className="button button-dark">Explore the collection</Link></div></div>} />
          </Routes></Suspense>
          </ErrorBoundary>
          </main>
          <Footer />

        </SubHeaderProvider>
      </BrowserRouter>
      <Toaster position='bottom-center' />
    </React.Fragment>
  );
}

export default App;
