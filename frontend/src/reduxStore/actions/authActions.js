import api from "../../backend/api";
import { setUserData, clearUserToken, getUserData } from "../../utils/tokenManager";
import toast from "react-hot-toast";
import { syncCartFromBackend } from "./cartActions";

export const authenticateUser = (loginRequest, navigate, resetForm, redirect = "/") => async (dispatch) => {
    try {
        const { data: loginResponse } = await api.post("/auth/signin", loginRequest);
        setUserData(loginResponse);
        dispatch({ type: "LOGIN", payload: loginResponse });

        const { data: userDetails } = await api.get("/auth/user");
        dispatch({ type: "SET_USER_DETAILS", payload: userDetails });

        resetForm();
        const destination = redirect.startsWith("/") && !redirect.startsWith("//") ? redirect : "/";
        navigate(destination);
        dispatch(syncCartFromBackend());
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
    }
};

export const logOutUser = (navigate) => async (dispatch) => {
    try {
        await api.post("/auth/signout");
    } catch (error) {
        console.error("Logout error:", error);
    } finally {
        clearUserToken();
        dispatch({ type: "LOGOUT" });
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
