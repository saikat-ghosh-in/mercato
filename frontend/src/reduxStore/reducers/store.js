import { combineReducers, configureStore } from "@reduxjs/toolkit";
import { productReducer } from "./productReducer";
import { statusReducer } from "./statusReducer";
import { cartReducer } from "./cartReducer";
import { authReducer } from "./authReducer";
import { categoryReducer } from "./categoryReducer";
import { sellerReducer } from "./sellerReducer";
import { wishlistReducer } from "./wishlistReducer";
import { session } from "../../backend/api.js";
import { privateApi } from "../services/privateApi.js";
import { withSessionBoundary } from "./sessionBoundary.js";

const reducers = combineReducers({
  products: productReducer, status: statusReducer, categories: categoryReducer,
  sellers: sellerReducer, cart: cartReducer, auth: authReducer, wishlist: wishlistReducer,
  [privateApi.reducerPath]: privateApi.reducer,
});
export const store = configureStore({
  reducer: withSessionBoundary(reducers),
  middleware: getDefaultMiddleware => getDefaultMiddleware().concat(privateApi.middleware),
  devTools: import.meta.env.DEV,
});
let previousEpoch = session.getSnapshot().epoch;
session.subscribe(snapshot => {
  const changed = previousEpoch !== snapshot.epoch;
  previousEpoch = snapshot.epoch;
  store.dispatch({ type: "SESSION_STATE", payload: snapshot });
  // Reset middleware subscriptions and timers as well as the reducer cache.
  if (changed) store.dispatch(privateApi.util.resetApiState());
});
export default store;
