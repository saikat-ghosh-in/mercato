import { createApi } from "@reduxjs/toolkit/query/react";
import api, { session } from "../../backend/api.js";

export const privateApi = createApi({
  reducerPath: "privateApi",
  baseQuery: async ({ url, method = "get", data }, { signal }) => {
    const epoch = session.getSnapshot().epoch;
    try {
      const response = await api.request({ url, method, data, signal });
      if (epoch !== session.getSnapshot().epoch) return { error: { status: "SESSION_CHANGED", data: "Account changed" } };
      return { data: response.data };
    } catch (error) { return { error: { status: error.response?.status || error.code || "NETWORK_ERROR", data: error.response?.data || error.message } }; }
  },
  tagTypes: ["Profile", "Addresses", "Wishlist", "Orders", "Management"],
  endpoints: build => ({
    profile: build.query({ query: () => ({ url: "/auth/user" }), providesTags: ["Profile"] }),
    addresses: build.query({ query: () => ({ url: "/user/addresses" }), providesTags: ["Addresses"] }),
    wishlist: build.query({ query: () => ({ url: "/wishlist" }), providesTags: ["Wishlist"] }),
    orders: build.query({ query: () => ({ url: "/user/orders/summary" }), providesTags: ["Orders"] }),
    paymentSession: build.query({ query: orderId => ({ url: `/user/orders/${encodeURIComponent(orderId)}/payment-session` }) }),
    management: build.query({ query: role => ({ url: `/${role === "admin" ? "admin" : "seller"}/dashboard/stats` }), providesTags: ["Management"] }),
  }),
});
export const { useAddressesQuery, useWishlistQuery, useOrdersQuery, useManagementQuery, usePaymentSessionQuery } = privateApi;
