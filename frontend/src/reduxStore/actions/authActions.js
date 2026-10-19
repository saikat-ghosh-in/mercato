import api from "../../backend/api";
import { setUserData, clearUserToken, getUserData } from "../../utils/tokenManager";
import toast from "react-hot-toast";
import { syncCartFromBackend, waitForCartRequests, invalidateCartRequests } from "./cartActions";
import { loginDestination } from "../../utils/accountRouting";

export const authenticateUser = (loginRequest, navigate, resetForm, redirect = "/") => async (dispatch, getState) => {
    try {
        await waitForCartRequests();
        const { data: loginResponse } = await api.post("/auth/signin", loginRequest);
        invalidateCartRequests();
        dispatch({ type: "RESET_CART" });
        setUserData(loginResponse);
        dispatch({ type: "LOGIN", payload: loginResponse });

        const { data: userDetails } = await api.get("/auth/user");
        dispatch({ type: "SET_USER_DETAILS", payload: userDetails });

        await dispatch(syncCartFromBackend());
        resetForm();
        const destination = loginDestination(userDetails, redirect);
        navigate(destination.startsWith("/checkout") && getState().cart.cartError ? "/cart" : destination);
    } catch (error) {
        toast.error(error?.response?.data?.message || "Oops! Something went wrong!");
    }
};

export const registerNewUser = (signUpRequest, resetForm, navigate) => async () => {
    try {
        await api.post("/auth/register", signUpRequest);
        resetForm();
        toast.success("User Registered Successfully");
        navigate("/verify-email-pending", { state: { email: signUpRequest.email } });
    } catch (error) {
        toast.error(error?.response?.data?.message || "Oops! Something went wrong!");
        throw error;
    }
};

export const logOutUser = (navigate) => async (dispatch) => {
    invalidateCartRequests();
    try {
        await api.post("/auth/signout");
    } catch (error) {
        console.error("Logout error:", error);
    } finally {
        clearUserToken();
        dispatch({ type: "LOGOUT" });
        dispatch({ type: "CLEAR_WISHLIST" });
        dispatch({ type: "CLEAR_CART" });
        dispatch({ type: "CLEAR_PAYMENT_DATA" });
        toast.success("Logged out successfully");
        if (navigate) navigate("/login");
    }
};

export const initializeAuth = () => (dispatch) => {
    const userData = getUserData();
    if (userData?.token && userData?.tokenExpirationTime) {
        const isExpired = Date.now() >= userData.tokenExpirationTime;
        if (!isExpired) {
            dispatch({ type: "LOGIN", payload: userData });
            dispatch(fetchUserDetails());
        } else {
            clearUserToken();
        }
    }
};

export const fetchUserDetails = () => async (dispatch) => {
    try {
        const { data: userDetails } = await api.get("/auth/user");
        dispatch({ type: "SET_USER_DETAILS", payload: userDetails });
    } catch (error) {
        console.error("Failed to fetch user details:", error);
        if ([401, 403].includes(error?.response?.status)) {
            clearUserToken();
            dispatch({ type: "LOGOUT" });
        }
    }
};
