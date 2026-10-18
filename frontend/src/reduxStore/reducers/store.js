import { configureStore } from "@reduxjs/toolkit";
import { persistStore, persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";
import { productReducer } from "./productReducer";
import { statusReducer } from "./statusReducer";
import { cartReducer } from "./cartReducer";
import { authReducer } from "./authReducer";
import { categoryReducer } from "./categoryReducer";
import { sellerReducer } from "./sellerReducer";
import { wishlistReducer } from "./wishlistReducer";

const authPersistConfig = {
  key: "auth",
  storage,
  whitelist: [
    "user",
    "userDetails",
    "addresses",
    "selectedAddressForCheckout",
    "clientSecret",
    "orderResponse",
    "paymentData",
    "paymentStatus"
  ]
};

const categoryPersistConfig = {
  key: "categories",
  storage,
  whitelist: [
    "categories",
    "totalCategories"
  ]
};

const sellerPersistConfig = {
  key: "sellers",
  storage,
  whitelist: [
    "sellers",
    "totalSellers"
  ]
};

const cartPersistConfig = {
  key: "cart",
  storage,
  whitelist: [
    "cartItems",
    "subtotal",
    "shipping",
    "platformFee",
    "processingAndHandling",
    "totalCharges",
    "total",
    "cartId"
  ]
};

const persistedAuthReducer = persistReducer(authPersistConfig, authReducer);
const persistedCategoryReducer = persistReducer(categoryPersistConfig, categoryReducer);
const persistedSellerReducer = persistReducer(sellerPersistConfig, sellerReducer);
const persistedCartReducer = persistReducer(cartPersistConfig, cartReducer);

export const store = configureStore({
  reducer: {
    products: productReducer,
    status: statusReducer,
    categories: persistedCategoryReducer,
    sellers: persistedSellerReducer,
    cart: persistedCartReducer,
    auth: persistedAuthReducer,
    wishlist: wishlistReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: [
          "persist/PERSIST",
          "persist/REHYDRATE",
          "persist/REGISTER",
          "persist/PAUSE",
        ],
        ignoredPaths: [
          "auth",
          "cart",
        ],
      },
    }),
  devTools: import.meta.env.DEV,
});

export const persistor = persistStore(store);

export default store;
