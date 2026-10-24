import test from "node:test";
import assert from "node:assert/strict";
import axios from "axios";
import { combineReducers, configureStore } from "@reduxjs/toolkit";
import { authReducer } from "../src/reduxStore/reducers/authReducer.js";
import { cartReducer } from "../src/reduxStore/reducers/cartReducer.js";
import { wishlistReducer } from "../src/reduxStore/reducers/wishlistReducer.js";
import { withSessionBoundary } from "../src/reduxStore/reducers/sessionBoundary.js";

let contextId = "alice";
let account = { userId: "alice", firstName: "Alice", roles: ["ROLE_USER"] };
let transport = async config => {
  if (config.url === "/auth/session") return { authenticated: true, user: account, contextId, expiresAt: null, mfa: { state: "NONE" } };
  if (config.url === "/auth/csrf") return { token: "csrf", headerName: "X-CSRF-TOKEN" };
  throw Error(`Unexpected request ${config.url}`);
};
axios.defaults.adapter = async config => ({ config, status: 200, statusText: "OK", headers: {}, data: await transport(config) });
const { default: api, session } = await import("../src/backend/api.js");
const { privateApi } = await import("../src/reduxStore/services/privateApi.js");
const { getUserAddresses } = await import("../src/reduxStore/actions/addressActions.js");
let afterAction = () => {};
const store = configureStore({
  reducer: withSessionBoundary(combineReducers({ auth: authReducer, cart: cartReducer, wishlist: wishlistReducer, [privateApi.reducerPath]: privateApi.reducer })),
  middleware: getDefaultMiddleware => getDefaultMiddleware().concat(() => next => action => { const result = next(action); afterAction(action); return result; }, privateApi.middleware),
});
session.subscribe(snapshot => store.dispatch({ type: "SESSION_STATE", payload: snapshot }));

test("actual Axios adapter sends CSRF/context and removes caller-provided bearer credentials", async () => {
  await session.bootstrap();
  const original = transport;
  let observed;
  transport = async config => {
    if (config.url === "/cart/add") { observed = config; return { cartItems: [], total: 0 }; }
    return original(config);
  };
  await api.post("/cart/add", { quantity: 1 }, { headers: { Authorization: "Bearer obsolete" } });
  assert.equal(observed.headers.get("Authorization"), undefined);
  assert.equal(observed.headers.get("X-CSRF-TOKEN"), "csrf");
  assert.equal(observed.headers.get("X-Account-Context"), "alice");
  assert.equal(observed.withCredentials, true);
  transport = original;
});

test("a delayed account A query cannot populate the real RTK cache after switching to B", async () => {
  let release;
  let started;
  const ready = new Promise(resolve => { started = resolve; });
  const original = transport;
  transport = async config => {
    if (config.url === "/user/orders/summary") { started(); return new Promise(resolve => { release = resolve; }); }
    return original(config);
  };
  store.dispatch({ type: "USER_ADDRESS", payload: [{ addressId: "alice-address" }] });
  store.dispatch({ type: "SET_PAYMENT_DATA", payload: { paymentSessionId: "alice-secret" } });
  store.dispatch({ type: "SYNC_CART_SUCCESS", payload: { cartItems: [{ productId: "alice-item", quantity: 1 }] } });
  const query = store.dispatch(privateApi.endpoints.orders.initiate());
  await ready;
  account = { userId: "bob", firstName: "Bob", roles: ["ROLE_USER"] };
  contextId = "bob";
  await session.refresh();
  assert.deepEqual(store.getState().auth.addresses, []);
  assert.equal(store.getState().auth.paymentData, null);
  assert.deepEqual(store.getState().cart.cartItems, []);
  release([{ orderId: "alice-private-order" }]);
  await query;
  assert.equal(store.getState().auth.user.userId, "bob");
  assert.equal(JSON.stringify(store.getState()).includes("alice-private-order"), false);
  query.unsubscribe();
  transport = original;
});

test("a server context conflict clears stale state and never replays the rejected mutation", async () => {
  const original = transport;
  let mutationCount = 0;
  transport = async config => {
    if (config.url === "/cart/add") {
      mutationCount += 1;
      contextId = "bob-new-session";
      throw { config, response: { status: 409, headers: {}, data: { code: "SESSION_CONTEXT_CHANGED" } } };
    }
    return original(config);
  };
  await assert.rejects(api.post("/cart/add", { productId: "one" }), error => error.code === "SESSION_CONTEXT_CHANGED");
  await session.bootstrap();
  assert.equal(session.getSnapshot().contextId, "bob-new-session");
  assert.equal(mutationCount, 1);
  transport = original;
  session.invalidate();
});

test("account changes between RTK completion and thunk continuation cannot restore an old address", async () => {
  account = { userId: "alice", firstName: "Alice", roles: ["ROLE_USER"] };
  contextId = "alice";
  await session.bootstrap();
  const original = transport;
  transport = async config => config.url === "/user/addresses" ? [{ addressId: "alice-private-address" }] : original(config);
  let switched = false;
  afterAction = action => {
    if (!switched && action.type.endsWith("/fulfilled") && action.meta?.arg?.endpointName === "addresses") {
      switched = true;
      account = { userId: "bob", roles: ["ROLE_USER"] };
      contextId = "bob";
      session.refresh().catch(() => {});
    }
  };
  await store.dispatch(getUserAddresses(() => {}));
  await session.bootstrap();
  const result = store.getState();
  afterAction = () => {};
  transport = original;
  session.invalidate();
  store.dispatch(privateApi.util.resetApiState());
  assert.equal(switched, true);
  assert.deepEqual(result.auth.addresses, []);
  assert.equal(result.auth.user.userId, "bob");
});
