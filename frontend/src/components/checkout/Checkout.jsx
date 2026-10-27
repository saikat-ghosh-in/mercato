import { useEffect, useRef, useState } from "react";
import { useContentTransition } from "../../hooks/useContentTransition";
import { useDispatch, useSelector } from "react-redux";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import toast from "../../utils/toastUtils.js";
import { FiArrowLeft } from "react-icons/fi";
import { checkoutIssue, currentDeliveryAddress, paymentHandoff, createCheckoutIntent, recoveryAction } from "../../utils/checkout";
import api, { session } from "../../backend/api";
import { useWorkflowEpoch } from "../../hooks/useWorkflowResource";
import { syncCartFromBackend } from "../../reduxStore/actions/cartActions";
import { useCartProducts } from "../../hooks/useCartProducts";
import {
  selectCartItems,
  selectCartQty,
  selectSubtotal,
  selectTotal,
  selectCharges,
  selectCartLoaded
} from "../../reduxStore/selectors/cartSelectors";
import { deleteUserAddress, getUserAddresses, setSelectedAddressForCheckout } from "../../reduxStore/actions/addressActions";
import { logOutUser } from "../../reduxStore/actions/authActions";
import { placeOrder } from "../../reduxStore/actions/orderActions";
import { setPaymentData } from "../../reduxStore/actions/paymentActions";
import AddAddressForm from "../address/AddAddressForm";
import AddressInfoModal from "../address/AddressInfoModal";
import { DeleteModal } from "../address/DeleteModal";
import ProgressBar from "./ProgressBar";
import AddressStep from "./steps/AddressStep";
import ReviewStep from "./steps/ReviewPaymentStep";
import OrderSummary from "./OrderSummary";
import PaymentAction from "./PaymentAction";
import { formatCurrency } from "../../utils/formatCurrency";

const CheckoutSession = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();

  const { user, addresses, selectedAddressForCheckout } = useSelector((s) => s.auth);
  const { cartLoading, cartError, deliveryMethod, version:cartVersion } = useSelector(s => s.cart);
  const cartLoaded = useSelector(selectCartLoaded);
  const cartItems = useSelector(selectCartItems);
  const cartQty = useSelector(selectCartQty);
  const subtotal = useSelector(selectSubtotal);
  const total = useSelector(selectTotal);
  const { shipping, platformFee, processingAndHandling } = useSelector(selectCharges);

  const { items: cartWithProducts, loading: loadingProducts, failed: productsUnavailable } = useCartProducts(cartItems);

  const [step, setStep] = useState(2);
  const [loadingAddresses, setLoadingAddresses] = useState(false);
  const [openAddressModal, setOpenAddressModal] = useState(false);
  const [editingAddress, setEditingAddress] = useState(null);
  const [openDeleteModal, setOpenDeleteModal] = useState(false);
  const [deletingAddress, setDeletingAddress] = useState(false);
  const [deleteAddressId, setDeleteAddressId] = useState(null);
  const [placingOrder, setPlacingOrder] = useState(false);
  const [paymentError, setPaymentError] = useState("");
  const [addressError, setAddressError] = useState(false);
  const [intent] = useState(createCheckoutIntent);
  const [initialOperation] = useState(() => new URLSearchParams(location.search).get("checkoutIntent"));
  const [recovering, setRecovering] = useState(Boolean(initialOperation));
  const [recovered, setRecovered] = useState(null);
  const [recoveryBlocked, setRecoveryBlocked] = useState(false);
  const lifetime = useRef(true);
  useEffect(() => { lifetime.current = true; return () => { lifetime.current = false; }; }, []);
  const paymentPending = useRef(false);
  const previousStep = useRef(1);
  const checkoutPanel = useRef(null);
  const selectedAddress = currentDeliveryAddress(addresses, selectedAddressForCheckout);
  const visibleStep = selectedAddress || intent.current() || recovered ? step : 1;
  useContentTransition(checkoutPanel, visibleStep);
  const bagIssue = checkoutIssue({ cartLoaded, cartLoading, cartError, loadingProducts, productsUnavailable, cartQty });
  const resuming = Boolean(intent.current() || recovered);
  const paymentDisabled = recovering || recoveryBlocked || (!resuming && (Boolean(bagIssue) || loadingAddresses || addressError || !Number.isSafeInteger(cartVersion)));
  const displayTotal = resuming ? recovered?.order?.totalAmount ?? intent.current()?.expectedTotal : total;

  const isChildRoute = location.pathname !== "/checkout";

  useEffect(() => {
    if (cartLoaded && cartItems.length === 0 && !isChildRoute && !initialOperation && !intent.current()) {
      toast.error("Your cart is empty");
      navigate("/cart");
    }
  }, [cartLoaded, cartItems.length, navigate, isChildRoute, initialOperation, intent]);
  useEffect(() => {
    dispatch(getUserAddresses(setLoadingAddresses, setAddressError));
  }, [dispatch]);
  useEffect(() => {
    if (!initialOperation || isChildRoute) return;
    const controller = new AbortController();
    const epoch = session.getSnapshot().epoch;
    api.get(`/user/checkout-intents/${encodeURIComponent(initialOperation)}`, {signal:controller.signal}).then(({data}) => {
      if (controller.signal.aborted || epoch !== session.getSnapshot().epoch) return;
      if (recoveryAction(data) === "order") {
        navigate(`/orders?orderId=${encodeURIComponent(data.orderId)}`, {replace:true}); return;
      }
      intent.restore(data.request); setRecovered(data);
      setPaymentError("Your original checkout is ready. Continue to payment to resume it.");
    }).catch(error => {
      if (controller.signal.aborted || epoch !== session.getSnapshot().epoch) return;
      setPaymentError(error.response?.status === 404 ? "This checkout could not be found for this account. Review your bag before starting again." : "Your checkout could not be recovered. Reload to check it before starting another payment.");
      if (error.response?.status === 404) {
        navigate("/checkout", {replace:true});
        setRecovering(false);
      } else setRecoveryBlocked(true);
    }).finally(() => { if (!controller.signal.aborted && epoch === session.getSnapshot().epoch) setRecovering(false); });
    return () => controller.abort();
  }, [initialOperation, isChildRoute, intent, navigate]);
  useEffect(() => {
    if (previousStep.current !== visibleStep) {
      checkoutPanel.current?.querySelector("h2")?.focus({ preventScroll: true });
      checkoutPanel.current?.closest(".site-container")?.querySelector(".checkout-steps")?.scrollIntoView({ block: "start", behavior: "instant" });
      previousStep.current = visibleStep;
    }
  }, [visibleStep]);

  if (isChildRoute) return <Outlet />;

  const handleAddAddress = () => {
    setEditingAddress(null);
    setOpenAddressModal(true);
  };

  const handleEditAddress = (addr) => {
    setEditingAddress(addr);
    setOpenAddressModal(true);
  };

  const handleDeleteAddress = (addr) => {
    setDeleteAddressId(addr.addressId);
    setOpenDeleteModal(true);
  };

  const handleModalClose = () => {
    setOpenAddressModal(false);
  };

  const onDeleteHandler = async () => {
    if (deletingAddress) return;
    setDeletingAddress(true);
    const isSelected = deleteAddressId === selectedAddressForCheckout?.addressId;
    try { await dispatch(deleteUserAddress(deleteAddressId, isSelected, setOpenDeleteModal, setLoadingAddresses)); }
    finally { setDeletingAddress(false); }
  };

  const handlePlaceOrder = async () => {
    if (paymentPending.current) return;
    if (paymentDisabled) { setPaymentError(bagIssue || "Please refresh your delivery address before paying."); return; }
    if (!resuming && !selectedAddress) {
      toast.error("Please select a shipping address");
      setStep(1);
      return;
    }

    if (!user) {
      toast.error("User information not found");
      dispatch(logOutUser(navigate));
      return;
    }

    if (!resuming && cartItems.length === 0) {
      toast.error("Cart is empty");
      navigate("/cart");
      return;
    }

    paymentPending.current = true;
    const epoch = session.getSnapshot().epoch;
    const current = () => lifetime.current && epoch === session.getSnapshot().epoch;
    setPlacingOrder(true);
    setPaymentError("");
    try {
      const request = intent.payload({ addressId: selectedAddress?.addressId, expectedTotal: total, deliveryMethod, expectedCartVersion:cartVersion });
      const query = new URLSearchParams(location.search); query.set("checkoutIntent",request.operationId);
      navigate({pathname:"/checkout",search:query.toString()},{replace:true});
      const response = recovered && recoveryAction(recovered) === "payment" ? recovered : await dispatch(placeOrder(request));
      if (!current()) return;
      if (!response.paymentSessionId || response.order?.paymentSummary?.status === "SUCCESS") {
        navigate(`/orders?orderId=${encodeURIComponent(response.order.orderId)}`); return;
      }

      const handoff = paymentHandoff(response);
      dispatch(setPaymentData(handoff));
      if (selectedAddress) dispatch(setSelectedAddressForCheckout(selectedAddress));
      navigate(`cashfree-payment?orderId=${encodeURIComponent(handoff.orderId)}`);
    } catch (err) {
      if (!current()) return;
      if (err.response?.status === 409) {
        intent.clear(); setRecovered(null); navigate("/checkout",{replace:true});
        setPaymentError("Your bag or checkout changed. Your address is retained. Review the refreshed items and total before continuing.");
        await dispatch(syncCartFromBackend());
      } else setPaymentError(err?.response?.data?.message || "The result is uncertain. Continue to payment to safely retry this same checkout.");
    } finally {
      if (current()) { setPlacingOrder(false); paymentPending.current = false; }
    }
  };

  return (
    <div className={`checkout-shell${visibleStep === 2 ? " checkout-with-payment-action" : ""}${paymentError ? " checkout-with-payment-error" : ""}`}>
      <div className="site-container">
        <div className="checkout-page-heading"><div><Link className="checkout-bag-link" to="/cart"><FiArrowLeft /> Your bag</Link><h1>Checkout</h1></div><span>{cartQty} {cartQty === 1 ? "item" : "items"}</span></div>
        <ProgressBar step={visibleStep} setStep={setStep} disabled={placingOrder} />
        <div className="checkout-layout">
          <div className="checkout-panel" ref={checkoutPanel} aria-labelledby="checkout-step-heading">
            {recovering && <p role="status">Recovering your checkout…</p>}
            {!resuming && bagIssue && <div className="checkout-bag-notice" role="status">{bagIssue}{!cartLoading && !loadingProducts && <Link to="/cart">Return to bag</Link>}</div>}
            {visibleStep === 1 && (
              <AddressStep
                addresses={addresses}
                selectedAddress={selectedAddress}
                loadingAddresses={loadingAddresses}
                addressError={addressError}
                onRetry={() => dispatch(getUserAddresses(setLoadingAddresses, setAddressError))}
                onAddAddress={handleAddAddress}
                onEditAddress={handleEditAddress}
                onDeleteAddress={handleDeleteAddress}
                onNext={() => { if (selectedAddress && !loadingAddresses && !addressError) setStep(2); }}
              />
            )}
            {visibleStep === 2 && resuming && <section className="surface p-4 space-y-3"><h2 id="checkout-step-heading" tabIndex={-1}>Resume your checkout.</h2><p>Your original checkout and total are retained. Changes to your bag or address selection do not change this payment.</p>{recovered?.orderId && <Link className="text-link" to={`/orders?orderId=${encodeURIComponent(recovered.orderId)}`}>View original order</Link>}</section>}
            {visibleStep === 2 && !resuming && (
              <ReviewStep
                selectedAddress={selectedAddress}
                cartWithProducts={cartWithProducts}
                onBack={() => setStep(1)}
                onEditAddress={handleEditAddress}
                loading={placingOrder}
              />
            )}
          </div>

          {resuming ? <aside className="checkout-summary" aria-label="Original checkout"><h2>Original checkout</h2><dl className="checkout-charges"><div className="checkout-total"><dt>Total</dt><dd>{formatCurrency(displayTotal)}</dd></div></dl><div className="checkout-summary-payment"><PaymentAction onSubmit={handlePlaceOrder} loading={placingOrder} disabled={paymentDisabled} />{paymentError && <p className="checkout-action-error" role="alert">{paymentError}</p>}</div></aside> : <OrderSummary
            cartWithProducts={cartWithProducts}
            cartQty={cartQty}
            subtotal={subtotal}
            shipping={shipping}
            platformFee={platformFee}
            processingAndHandling={processingAndHandling}
            total={total}
            onPay={visibleStep === 2 ? handlePlaceOrder : undefined}
            paymentLoading={placingOrder}
            paymentDisabled={paymentDisabled}
            paymentError={paymentError}
          />}
        </div>
      </div>

      {visibleStep === 2 && <div className="checkout-payment-bar" aria-label="Payment action"><div className="site-container checkout-payment-bar-inner">
        {paymentError && <p className="checkout-action-error checkout-payment-bar-error" id="checkout-mobile-payment-error" role="alert">{paymentError}</p>}
        <div className="checkout-payment-bar-total"><span>Total</span><strong>{formatCurrency(displayTotal)}</strong></div>
        <PaymentAction onSubmit={handlePlaceOrder} loading={placingOrder} disabled={paymentDisabled} describedBy={paymentError ? "checkout-mobile-payment-error" : undefined} />
      </div></div>}
      <p className="sr-only" role="status">{placingOrder ? "Preparing your secure payment…" : ""}</p>

      <AddressInfoModal open={openAddressModal} setOpen={handleModalClose}>
        <AddAddressForm
          address={editingAddress}
          setOpenAddressModal={handleModalClose}
          setLoadingAddresses={setLoadingAddresses}
        />
      </AddressInfoModal>

      <DeleteModal
        open={openDeleteModal}
        setOpen={setOpenDeleteModal}
        title="Delete Address"
        onDeleteHandler={onDeleteHandler}
        loader={deletingAddress}
      />
    </div>
  );
};

export default function Checkout() {
  const epoch = useWorkflowEpoch();
  return <CheckoutSession key={epoch} />;
}
