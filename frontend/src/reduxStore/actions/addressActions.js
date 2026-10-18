import api from "../../backend/api";
import toast from "react-hot-toast";

export const getUserAddresses = (setLoadingAddresses, setAddressError = () => {}) => async (dispatch) => {
    try {
        setLoadingAddresses(true);
        setAddressError(false);
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get(`/user/addresses`);
        dispatch({ type: "USER_ADDRESS", payload: data });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        setAddressError(true);
        console.error(error);
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch user addresses",
        });
    } finally {
        setLoadingAddresses(false);
    }
};

export const addUpdateUserAddress = (sendData, addressId, setOpenAddressModal, setLoadingAddresses) => async (dispatch) => {
    dispatch({ type: "BUTTON_LOADER" });
    try {
        if (!addressId) {
            await api.post("/user/addresses/add", sendData);
        } else {
            await api.put(`/user/addresses/${addressId}`, sendData);
        }
        await dispatch(getUserAddresses(setLoadingAddresses));
        if (addressId) {
            dispatch((innerDispatch, getState) => {
                if (getState().auth.selectedAddressForCheckout?.addressId === addressId)
                    innerDispatch(setSelectedAddressForCheckout({ ...sendData, addressId }));
            });
        }
        toast.success("Address saved successfully");
        dispatch({ type: "IS_SUCCESS" });
        setOpenAddressModal(false);
    } catch (error) {
        console.error(error);
        toast.error(error?.response?.data?.message || "Couldn’t save the address. Please try again.");
        dispatch({ type: "IS_ERROR", payload: null });
    }
};

export const deleteUserAddress = (addressId, isSelectedForCheckout, setOpenDeleteModal, setLoadingAddresses) => async (dispatch) => {
    try {
        dispatch({ type: "BUTTON_LOADER" });
        await api.delete(`/user/addresses/${addressId}`);
        dispatch({ type: "IS_SUCCESS" });
        dispatch(getUserAddresses(setLoadingAddresses));
        if (isSelectedForCheckout) dispatch(setSelectedAddressForCheckout(null));
        toast.success("Address deleted successfully");
    } catch (error) {
        console.error(error);
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Oops! Something went wrong!",
        });
    } finally {
        setOpenDeleteModal(false);
    }
};

export const setSelectedAddressForCheckout = (address) => ({
    type: "SELECT_CHECKOUT_ADDRESS",
    payload: address,
});
