import api, { session } from "../../backend/api.js";
import toast from "../../utils/toastUtils.js";
import { syncCartFromBackend, waitForCartRequests } from "./cartActions";
import { loginDestination } from "../../utils/accountRouting";
import { privateApi } from "../services/privateApi.js";

export const authenticateUser = (loginRequest, navigate, resetForm, redirect = "/") => async (dispatch, getState) => {
    const epoch = session.getSnapshot().epoch;
    try {
        await waitForCartRequests();
        if (epoch !== session.getSnapshot().epoch) return;
        await api.post("/auth/signin", loginRequest);
        const identity = await session.refresh({ broadcast: true });
        resetForm();
        if (identity.mfa?.state !== "NONE" && identity.mfa?.state !== "VERIFIED") {
            navigate(`/auth/mfa?redirect=${encodeURIComponent(redirect)}`);
            return;
        }
        await dispatch(syncCartFromBackend());
        if (identity.epoch !== session.getSnapshot().epoch) return;
        const destination = loginDestination(identity.user, redirect);
        navigate(destination.startsWith("/checkout") && getState().cart.cartError ? "/cart" : destination);
    } catch (error) {
        toast.error(error?.response?.data?.message || error.message || "Oops! Something went wrong!");
    }
};
export const registerNewUser = (signUpRequest, resetForm, navigate) => async () => {
    await api.post("/auth/register", signUpRequest);
    resetForm();
    navigate("/verify-email-pending", { state: { email: signUpRequest.email } });
};
export const logOutUser = navigate => async () => {
    try {
        await api.post("/auth/signout");
        await session.refresh({ broadcast: true });
        if (navigate) navigate("/login");
    } catch (error) {
        toast.error(error?.response?.data?.message || "Sign out couldn’t complete. Please try again.");
    }
};
export const initializeAuth = () => () => session.bootstrap().catch(() => {});
export const fetchUserDetails = () => async dispatch => {
    const epoch = session.getSnapshot().epoch;
    try {
        const userDetails = await dispatch(privateApi.endpoints.profile.initiate(undefined, { forceRefetch: true, subscribe: false })).unwrap();
        if (epoch !== session.getSnapshot().epoch) return;
        dispatch({ type: "SET_USER_DETAILS", payload: userDetails });
    } catch { /* The session gate handles expiry; profile requests can be retried. */ }
};
