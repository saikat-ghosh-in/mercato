import api, { session } from "../../backend/api.js";
import toast from "react-hot-toast";
import { privateApi } from "../services/privateApi.js";

export const getUserAddresses = (setLoadingAddresses, setAddressError = () => {}) => async (dispatch) => {
    const epoch = session.getSnapshot().epoch;
    try {
        setLoadingAddresses(true);
        setAddressError(false);
        dispatch({ type: "IS_FETCHING" });
        const data = await dispatch(privateApi.endpoints.addresses.initiate(undefined, { forceRefetch: true, subscribe: false })).unwrap();
        if (epoch !== session.getSnapshot().epoch) return;
        dispatch({ type: "USER_ADDRESS", payload: data });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        if (epoch !== session.getSnapshot().epoch) return;
        setAddressError(true);
        console.error(error);
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch user addresses",
        });
    } finally {
        if (epoch === session.getSnapshot().epoch) setLoadingAddresses(false);
    }
};

export const addUpdateUserAddress = (sendData, addressId, setOpenAddressModal, setLoadingAddresses) => async (dispatch) => {
    const epoch = session.getSnapshot().epoch;
    dispatch({ type: "BUTTON_LOADER" });
    try {
        if (!addressId) {
            await api.post("/user/addresses/add", sendData);
        } else {
            await api.put(`/user/addresses/${addressId}`, sendData);
        }
        if (epoch !== session.getSnapshot().epoch) return;
        await dispatch(getUserAddresses(setLoadingAddresses));
        if (epoch !== session.getSnapshot().epoch) return;
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
        if (epoch !== session.getSnapshot().epoch) return;
        console.error(error);
        toast.error(error?.response?.data?.message || "Couldn’t save the address. Please try again.");
        dispatch({ type: "IS_ERROR", payload: null });
    }
};

export const deleteUserAddress = (addressId, isSelectedForCheckout, setOpenDeleteModal, setLoadingAddresses) => async (dispatch) => {
    const epoch = session.getSnapshot().epoch;
    try {
        dispatch({ type: "BUTTON_LOADER" });
        await api.delete(`/user/addresses/${addressId}`);
        if (epoch !== session.getSnapshot().epoch) return;
        dispatch({ type: "IS_SUCCESS" });
        dispatch(getUserAddresses(setLoadingAddresses));
        if (isSelectedForCheckout) dispatch(setSelectedAddressForCheckout(null));
        toast.success("Address deleted successfully");
    } catch (error) {
        if (epoch !== session.getSnapshot().epoch) return;
        console.error(error);
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Oops! Something went wrong!",
        });
    } finally {
        if (epoch === session.getSnapshot().epoch) setOpenDeleteModal(false);
    }
};

export const setSelectedAddressForCheckout = (address) => ({
    type: "SELECT_CHECKOUT_ADDRESS",
    payload: address,
});

export const makeAddressDefault = addressId => async dispatch => {
    const epoch = session.getSnapshot().epoch;
    try {
        const { data } = await api.patch(`/user/addresses/${addressId}/default`);
        if (epoch !== session.getSnapshot().epoch) return;
        dispatch(setSelectedAddressForCheckout(data));
        await dispatch(getUserAddresses(() => {}));
        if (epoch !== session.getSnapshot().epoch) return;
        toast.success("Default address updated");
    } catch (error) { toast.error(error?.response?.data?.message || "Couldn’t change the default address"); }
};
