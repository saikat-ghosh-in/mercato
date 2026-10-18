import { useEffect, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { FiArrowLeft } from "react-icons/fi";
import { checkoutIssue, currentDeliveryAddress, paymentHandoff } from "../../utils/checkout";
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
import ReviewStep from "./steps/ReviewStep";
import PaymentStep from "./steps/PaymentStep";
import OrderSummary from "./OrderSummary";

const Checkout = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();

  const { user, addresses, selectedAddressForCheckout } = useSelector((s) => s.auth);
  const { cartLoading, cartError } = useSelector(s => s.cart);
  const cartLoaded = useSelector(selectCartLoaded);
  const cartItems = useSelector(selectCartItems);
  const cartQty = useSelector(selectCartQty);
  const subtotal = useSelector(selectSubtotal);
  const total = useSelector(selectTotal);
  const { shipping, platformFee, processingAndHandling } = useSelector(selectCharges);

  const { items: cartWithProducts, loading: loadingProducts, failed: productsUnavailable } = useCartProducts(cartItems);

  const [step, setStep] = useState(1);
  const [loadingAddresses, setLoadingAddresses] = useState(false);
  const [openAddressModal, setOpenAddressModal] = useState(false);
  const [editingAddress, setEditingAddress] = useState(null);
  const [openDeleteModal, setOpenDeleteModal] = useState(false);
  const [deletingAddress, setDeletingAddress] = useState(false);
  const [deleteAddressId, setDeleteAddressId] = useState(null);
  const [placingOrder, setPlacingOrder] = useState(false);
  const [paymentError, setPaymentError] = useState("");
  const [addressError, setAddressError] = useState(false);
  const paymentPending = useRef(false);
  const previousStep = useRef(1);
  const checkoutPanel = useRef(null);
  const selectedAddress = currentDeliveryAddress(addresses, selectedAddressForCheckout);
  const visibleStep = selectedAddress ? step : 1;
  const bagIssue = checkoutIssue({ cartLoaded, cartLoading, cartError, loadingProducts, productsUnavailable, cartQty });

  const isChildRoute = location.pathname !== "/checkout";

  useEffect(() => {
    if (cartLoaded && cartItems.length === 0 && !isChildRoute) {
      toast.error("Your cart is empty");
      navigate("/cart");
    }
  }, [cartLoaded, cartItems.length, navigate, isChildRoute]);
  useEffect(() => { dispatch(getUserAddresses(setLoadingAddresses, setAddressError)); }, [dispatch]);
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
    if (bagIssue || loadingAddresses || addressError) { setPaymentError(bagIssue || "Please refresh your delivery address before paying."); return; }
    if (!selectedAddress) {
      toast.error("Please select a shipping address");
      setStep(1);
      return;
    }

    if (!user) {
      toast.error("User information not found");
      dispatch(logOutUser(navigate));
      return;
    }

    if (cartItems.length === 0) {
      toast.error("Cart is empty");
      navigate("/cart");
      return;
    }

    paymentPending.current = true;
    setPlacingOrder(true);
    setPaymentError("");
    try {
      const response = await dispatch(
        placeOrder({ addressId: selectedAddress.addressId })
      );

      dispatch(setPaymentData(paymentHandoff(response)));
      dispatch(setSelectedAddressForCheckout(selectedAddress));
      navigate("cashfree-payment");
    } catch (err) {
      setPaymentError(err?.response?.data?.message || (err.response ? "The payment service is unavailable right now." : err.message) || "The payment service is unavailable right now.");
    } finally {
      setPlacingOrder(false);
      paymentPending.current = false;
    }
  };

  return (
    <div className="checkout-shell">
      <div className="site-container">
        <div className="checkout-page-heading"><div><Link className="checkout-bag-link" to="/cart"><FiArrowLeft /> Your bag</Link><h1>Checkout</h1></div><span>{cartQty} {cartQty === 1 ? "item" : "items"}</span></div>
        <ProgressBar step={visibleStep} setStep={setStep} disabled={placingOrder} />
        <div className="checkout-layout">
          <div className="checkout-panel" ref={checkoutPanel} aria-labelledby="checkout-step-heading">
            {bagIssue && <div className="checkout-bag-notice" role="status">{bagIssue}{!cartLoading && !loadingProducts && <Link to="/cart">Return to bag</Link>}</div>}
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
            {visibleStep === 2 && (
              <ReviewStep
                selectedAddress={selectedAddress}
                cartWithProducts={cartWithProducts}
                onBack={() => setStep(1)}
                onNext={() => { if (!bagIssue) setStep(3); }}
                disabled={Boolean(bagIssue)}
              />
            )}
            {visibleStep === 3 && (
              <PaymentStep
                onBack={() => setStep(2)}
                onSubmit={handlePlaceOrder}
                onChangeAddress={() => setStep(1)}
                selectedAddress={selectedAddress}
                total={total}
                error={paymentError}
                loading={placingOrder}
                disabled={Boolean(bagIssue) || loadingAddresses || addressError}
              />
            )}
          </div>

          <OrderSummary
            cartWithProducts={cartWithProducts}
            cartQty={cartQty}
            subtotal={subtotal}
            shipping={shipping}
            platformFee={platformFee}
            processingAndHandling={processingAndHandling}
            total={total}
          />
        </div>
      </div>

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

export default Checkout;
