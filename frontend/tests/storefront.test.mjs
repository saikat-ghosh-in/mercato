import test from "node:test";
import assert from "node:assert/strict";
import { productReducer } from "../src/reduxStore/reducers/productReducer.js";
import { cartReducer } from "../src/reduxStore/reducers/cartReducer.js";
import { statusReducer } from "../src/reduxStore/reducers/statusReducer.js";
import { parseParams, buildQueryString } from "../src/utils/productUtils.js";

test("a slow previous catalog response cannot overwrite a newer search", () => {
  let state = productReducer(undefined, { type: "PRODUCTS_LOADING", requestId: 1 });
  state = productReducer(state, { type: "PRODUCTS_LOADING", requestId: 2 });
  state = productReducer(state, { type: "FETCH_PRODUCTS", requestId: 2, payload: [{ productId: "new" }], totalElements: 1 });
  state = productReducer(state, { type: "FETCH_PRODUCTS", requestId: 1, payload: [{ productId: "old" }] });
  assert.equal(state.products[0].productId, "new");
  assert.equal(state.loading, false);
});

test("a refresh failure keeps the last loaded collection and exposes a retryable error", () => {
  let state = productReducer(undefined, { type: "PRODUCTS_LOADING", requestId: 1 });
  state = productReducer(state, { type: "FETCH_PRODUCTS", requestId: 1, payload: [{ productId: "old" }], totalElements: 1 });
  state = productReducer(state, { type: "PRODUCTS_LOADING", requestId: 2 });
  state = productReducer(state, { type: "PRODUCTS_ERROR", requestId: 2, payload: "Try again" });
  assert.equal(state.products[0].productId, "old");
  assert.equal(state.pagination.totalElements, 1);
  assert.equal(state.error, "Try again");
  assert.equal(state.loading, false);
});

test("category requests do not finish an unrelated request", () => {
  let state = statusReducer(undefined, { type: "IS_FETCHING" });
  state = statusReducer(state, { type: "CATEGORY_LOADER" });
  state = statusReducer(state, { type: "CATEGORY_SUCCESS" });
  assert.equal(state.isLoading, true);
  assert.equal(state.categoryLoader, false);
});

test("seller names containing percent signs are decoded exactly once", () => {
  const params = new URLSearchParams({ sellers: "100% Good", keyword: "tea & coffee" });
  const filters = parseParams(params);
  assert.deepEqual(filters.sellers, ["100% Good"]);
  const query = new URLSearchParams(buildQueryString(filters));
  assert.equal(query.get("sellers"), "100% Good");
  assert.equal(query.get("keyword"), "tea & coffee");
});

test("invalid or unbounded page numbers recover to page zero", () => {
  for (const page of ["-1", "NaN", "2.5", "999999999999999999999", "1e3"]) {
    assert.equal(parseParams(new URLSearchParams({ pageNumber: page })).pageNumber, 0);
  }
  assert.equal(parseParams(new URLSearchParams({ pageNumber: "12" })).pageNumber, 12);
});

test("cart sync retains stock flags and server-calculated totals", () => {
  const state = cartReducer(undefined, { type: "SYNC_CART_SUCCESS", payload: {
    cartId: "cart", cartItems: [{ productId: "one", quantity: 2, itemPrice: 10, lineTotal: 20, outOfStock: true, canAddMore: false }],
    subtotal: 20, total: 82, charges: [{ type: "SHIPPING", amount: 50 }, { type: "PLATFORM_FEE", amount: 12 }]
  } });
  assert.equal(state.cartItems[0].outOfStock, true);
  assert.equal(state.cartItems[0].canAddMore, false);
  assert.equal(state.total, 82);
  assert.equal(state.shipping, 50);
});

test("refresh failure keeps the existing bag visible", () => {
  const initial = cartReducer(undefined, { type: "SYNC_CART_SUCCESS", payload: { cartItems: [{ productId: "one", quantity: 1 }] } });
  const failed = cartReducer(initial, { type: "CART_ERROR", payload: "Offline" });
  assert.equal(failed.cartItems.length, 1);
  assert.equal(failed.cartError, "Offline");
  assert.equal(failed.cartLoading, false);
});
