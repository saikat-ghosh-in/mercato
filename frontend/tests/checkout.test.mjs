import test from "node:test";
import assert from "node:assert/strict";
import { checkoutIssue, currentDeliveryAddress, paymentHandoff } from "../src/utils/checkout.js";
import * as checkout from "../src/utils/checkout.js";
import { cartReducer } from "../src/reduxStore/reducers/cartReducer.js";

test("checkout keeps the full uncertain intent even after quote and address change; reload restores server body", () => {
  assert.equal(typeof checkout.createCheckoutIntent, "function");
  let id=0;const intent=checkout.createCheckoutIntent(()=>`operation-${++id}`);
  const original={addressId:"one",expectedTotal:612,deliveryMethod:"STANDARD",expectedCartVersion:7};
  const submitted=intent.payload(original);
  assert.equal(intent.payload({...original,addressId:"two",expectedCartVersion:9}),submitted);
  assert.equal(submitted.operationId,"operation-1");
  const reloaded=checkout.createCheckoutIntent();reloaded.restore({...submitted});
  assert.deepEqual(reloaded.payload({}),submitted);
  intent.clear();assert.equal(intent.payload({...original,expectedCartVersion:9}).operationId,"operation-2");
});
test("cart preserves the authoritative quote version without predicting it",()=>{
  const first=cartReducer(undefined,{type:"SYNC_CART_SUCCESS",payload:{cartItems:[],version:8}});
  assert.equal(first.version,8);
  assert.equal(cartReducer(first,{type:"CART_PENDING",productId:"one"}).version,8);
});
test("recovery only offers a payable session or exact safe replay and otherwise opens the existing order",()=>{
  assert.equal(typeof checkout.recoveryAction,"function");
  const order={orderId:"original",totalAmount:612};
  assert.equal(checkout.recoveryAction({state:"PAYMENT_READY",canReplayCapture:true,paymentSessionId:"safe",order}),"payment");
  assert.equal(checkout.recoveryAction({state:"REPLAY_CAPTURE",canReplayCapture:true,order}),"replay");
  assert.equal(checkout.recoveryAction({state:"ORDER_UPDATED",canReplayCapture:false,order}),"order");
  assert.equal(checkout.recoveryAction({state:"PAYMENT_READY",canReplayCapture:false,paymentSessionId:"old",order}),"order");
});

test("checkout uses the latest saved address and rejects deleted or stale selections", () => {
  const selected = { addressId: "one", addressLine1: "Old address" };
  const updated = { addressId: "one", addressLine1: "New address" };
  assert.equal(currentDeliveryAddress([updated], selected), updated);
  assert.equal(currentDeliveryAddress([], selected), null);
  assert.equal(currentDeliveryAddress([updated], null), updated);
});
test("checkout selects the default address, preserves an explicit choice and recovers after deletion", () => {
  const first = { addressId: "one", defaultAddress: true };
  const second = { addressId: "two", defaultAddress: false };
  assert.equal(currentDeliveryAddress([first, second], null), first);
  assert.equal(currentDeliveryAddress([first, second], second), second);
  assert.equal(currentDeliveryAddress([first], second), first);
  assert.equal(currentDeliveryAddress([second, { addressId: "three" }], null), null);
});
test("checkout cannot pay with an unverified, failed or empty bag", () => {
  const ready = { cartLoaded: true, cartQty: 2 };
  assert.equal(checkoutIssue(ready), "");
  for (const pending of [{ cartLoaded: false }, { cartLoading: true }, { loadingProducts: true }]) assert.match(checkoutIssue({ ...ready, ...pending }), /wait/);
  for (const failed of [{ cartError: "Network failure" }, { productsUnavailable: true }]) assert.match(checkoutIssue({ ...ready, ...failed }), /couldn’t be checked/);
  for (const cartQty of [0, -1, NaN, 1.5]) assert.match(checkoutIssue({ ...ready, cartQty }), /empty/);
});
test("payment handoff uses the captured backend total and session", () => {
  const result = paymentHandoff({ order: { orderId: "order-1", totalAmount: 112.75, paymentSummary: { paymentId: "pay-1" } }, paymentSessionId: "session" });
  assert.equal(result.totalAmount, 112.75);
  assert.equal(result.orderId, "order-1");
  assert.equal(result.paymentId, "pay-1");
  assert.equal(result.paymentSessionId, "session");
});
test("incomplete payment responses cannot open a broken payment screen", () => {
  for (const response of [null, {}, { order: { orderId: "one", totalAmount: 12 } }, { order: { totalAmount: 12 }, paymentSessionId: "session" }, { order: { orderId: "one", totalAmount: -1 }, paymentSessionId: "session" }, { order: { orderId: "one", totalAmount: NaN }, paymentSessionId: "session" }, { order: { orderId: "one", totalAmount: 12 }, paymentSessionId: " " }]) assert.throws(() => paymentHandoff(response), /incomplete/);
});
