import { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { MdShoppingCart } from "react-icons/md";
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
  const [deleteAddressId, setDeleteAddressId] = useState(null);
  const [placingOrder, setPlacingOrder] = useState(false);

  const isChildRoute = location.pathname !== "/checkout";

  useEffect(() => {
    if (cartLoaded && cartItems.length === 0 && !isChildRoute) {
      toast.error("Your cart is empty");
      navigate("/cart");
    }
    dispatch(getUserAddresses(setLoadingAddresses));
  }, [cartLoaded, cartItems.length, dispatch, navigate, isChildRoute]);

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
    setEditingAddress(null);
  };

  const onDeleteHandler = () => {
    const isSelected = deleteAddressId === selectedAddressForCheckout?.addressId;
    dispatch(deleteUserAddress(deleteAddressId, isSelected, setOpenDeleteModal, setLoadingAddresses));
  };

  const handlePlaceOrder = async () => {
    if (loadingProducts || productsUnavailable) { toast.error("Some items could not be checked. Refresh your bag before paying."); return; }
    if (!selectedAddressForCheckout) {
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

    setPlacingOrder(true);
    try {
      const response = await dispatch(
        placeOrder({ addressId: selectedAddressForCheckout.addressId })
      );

      dispatch(setPaymentData({
        totalAmount: total,
        paymentStatus: "initiated",
        timestamp: Date.now(),
        paymentId: response?.order?.paymentSummary?.paymentId,
        orderId: response?.order?.orderId,
        paymentSessionId: response?.paymentSessionId,
      }));
      dispatch(setSelectedAddressForCheckout(selectedAddressForCheckout));
      navigate("cashfree-payment");
    } catch (err) {
      toast.error("Failed to place order. Please try again.");
      console.error("Order placement error:", err);
    } finally {
      setPlacingOrder(false);
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen py-6">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-blue-50 rounded-xl">
            <MdShoppingCart size={20} className="text-blue-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">Checkout</h1>
            <p className="text-xs text-gray-500 mt-0.5">Complete your purchase securely</p>
          </div>
        </div>

        <ProgressBar step={step} setStep={setStep} />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
          <div className="lg:col-span-2">
            {step === 1 && (
              <AddressStep
                addresses={addresses}
                selectedAddress={selectedAddressForCheckout}
                loadingAddresses={loadingAddresses}
                onAddAddress={handleAddAddress}
                onEditAddress={handleEditAddress}
                onDeleteAddress={handleDeleteAddress}
                onNext={() => setStep(2)}
              />
            )}
            {step === 2 && (
              <ReviewStep
                selectedAddress={selectedAddressForCheckout}
                cartWithProducts={cartWithProducts}
                onBack={() => setStep(1)}
                onNext={() => setStep(3)}
              />
            )}
            {step === 3 && (
              <PaymentStep
                onBack={() => setStep(2)}
                onSubmit={handlePlaceOrder}
                loading={placingOrder}
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
        loader={false}
      />
    </div>
  );
};

export default Checkout;
