import test from "node:test";
import assert from "node:assert/strict";
import { normalizeQuantity, countCartUnits, createSerialQueue } from "../src/utils/cartState.js";
import { cartReducer } from "../src/reduxStore/reducers/cartReducer.js";
import { detailsForForm, detailsPayload } from "../src/utils/productDetails.js";

test("badge counts units numerically and ignores malformed persisted quantities", () => {
  assert.equal(countCartUnits([{ quantity: "2" }, { quantity: 3 }, { quantity: null }, { quantity: -1 }, { quantity: "no" }, { quantity: 1.5 }]), 5);
  assert.equal(countCartUnits(undefined), 0);
  for (const value of [null, undefined, true, NaN, Infinity, -2, 2.5, {}, []]) assert.equal(normalizeQuantity(value), 0);
  assert.equal(countCartUnits([{ quantity: 100 }, { quantity: 1 }]), 101);
});
test("cart requests run in sequence and recover after a failed mutation", async () => {
  const queue = createSerialQueue();
  const events = [];
  let release;
  const first = queue.run(async () => { events.push("first-start"); await new Promise(r => { release = r; }); events.push("first-end"); });
  const second = queue.run(async () => { events.push("second"); throw Error("offline"); });
  const rejected = assert.rejects(second, /offline/);
  const third = queue.run(() => events.push("third"));
  await Promise.resolve();
  assert.deepEqual(events, ["first-start"]);
  release(); await first; await rejected; await third; await queue.drain();
  assert.deepEqual(events, ["first-start", "first-end", "second", "third"]);
});
test("one completed cart mutation does not reenable a second pending operation", () => {
  let state = cartReducer(undefined, { type: "CART_PENDING", productId: "one" });
  state = cartReducer(state, { type: "CART_PENDING", productId: "two" });
  state = cartReducer(state, { type: "SYNC_CART_SUCCESS", payload: { cartItems: [{productId:"one", quantity:"2"}] } });
  state = cartReducer(state, { type: "CART_IDLE", productId: "one" });
  assert.equal(state.cartLoading, true); assert.equal(state.pendingProducts.two, 1);
  state = cartReducer(state, { type: "CART_IDLE", productId: "two" });
  assert.equal(state.cartLoading, false); assert.equal(state.cartItems[0].quantity, 2);
});
test("invalid cart acknowledgement never empties an existing bag", () => {
  const initial = cartReducer(undefined, { type:"SYNC_CART_SUCCESS", payload:{cartItems:[{productId:"one",quantity:2}]} });
  const state = cartReducer(initial, { type:"SYNC_CART_SUCCESS", payload:"Removed" });
  assert.equal(state.cartItems.length, 1); assert.match(state.cartError, /Invalid bag/);
});
test("product editor round trips ordered details and supports explicit clearing", () => {
  const form=detailsForForm({brand:"Brand",highlights:["One","Two"],specifications:[{label:"Fit",value:"Relaxed"},{label:"Care",value:"Wash: gently"}]});
  assert.deepEqual(detailsPayload(form).specifications,[{label:"Fit",value:"Relaxed"},{label:"Care",value:"Wash: gently"}]);
  assert.deepEqual(detailsPayload(detailsForForm(null)).highlights, []);
  assert.throws(()=>detailsPayload({...form,specificationsText:"No separator"}), /label and value/);
});

test("identity changes mark the cart unresolved until the merged server bag arrives", () => {
  let state = cartReducer(undefined, { type: "SYNC_CART_SUCCESS", payload: { cartItems: [{productId:"guest",quantity:2}] } });
  state = cartReducer(state, {type:"RESET_CART"});
  assert.equal(state.cartLoaded, false);
  state = cartReducer(state, {type:"SYNC_CART_SUCCESS",payload:{cartItems:[{productId:"guest",quantity:2}]}});
  assert.equal(state.cartLoaded,true); assert.equal(countCartUnits(state.cartItems),2);
});
