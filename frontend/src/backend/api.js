import axios from "axios";
import toast from "react-hot-toast";
import {
    getActiveToken, getUserData, setGuestToken,
    clearUserToken, isTokenExpired
} from "../utils/tokenManager";

const api = axios.create({
    baseURL: import.meta.env.VITE_BACKEND_BASE_URL
        ? `${import.meta.env.VITE_BACKEND_BASE_URL.replace(/\/+$/, "")}/api`
        : "/api",
    timeout: 20000,
    paramsSerializer: (params) => {
        return Object.entries(params)
            .map(([key, val]) => `${key}=${encodeURIComponent(val)}`)
            .join("&");
    }
});

const PROTECTED_ENDPOINTS = [
    "/user/",
    "/account/",
    "/admin/",
    "/seller/",
    "/orders"
];

const isProtectedEndpoint = (url) => {
    return PROTECTED_ENDPOINTS.some(endpoint => url.includes(endpoint));
};

api.interceptors.request.use(
    (config) => {
        const userData = getUserData();

        if (userData?.token && isTokenExpired(userData.tokenExpirationTime)) {
            console.warn("Token expired");
            clearUserToken();

            toast("Your session has expired. Please log in again.", { id: "session-expired" });
            if (isProtectedEndpoint(config.url)) {
                window.location.href = "/login";
                return Promise.reject(new Error("Token expired"));
            }
        }

        const token = getActiveToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        return config;
    },
    (error) => Promise.reject(error)
);

api.interceptors.response.use(
    (response) => {
        const guestToken = response.headers['x-guest-token'];
        if (guestToken) {
            setGuestToken(guestToken);
        }
        return response;
    },
    (error) => {
        if (error.response?.status === 401) {
            const url = error.config?.url || "";
            const isLoginPage = window.location.pathname === "/login";

            if (isProtectedEndpoint(url) && !isLoginPage) {
                console.error("Unauthorized - redirecting to login");
                clearUserToken();
                window.location.href = "/login";
            }
        }
        return Promise.reject(error);
    }
);

export default api;
