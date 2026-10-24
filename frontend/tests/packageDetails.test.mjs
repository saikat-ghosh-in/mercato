import test from "node:test";
import assert from "node:assert/strict";
import { detailsForForm, detailsPayload, packagePayload } from "../src/utils/productDetails.js";

test("seller-paid delivery policy survives editing and can be explicitly disabled", () => {
  const form=detailsForForm({sellerPaysShipping:true});
  assert.equal(detailsPayload(form).sellerPaysShipping,true);
  assert.equal(detailsPayload({...form,sellerPaysShipping:false}).sellerPaysShipping,false);
  assert.equal(detailsPayload(detailsForForm(null)).sellerPaysShipping,false);
});

test("packed measurements survive editing without rewriting optional legacy data", () => {
  const product = { weightKg: 6.125, lengthCm: 100.25, widthCm: 20, heightCm: 30 };
  assert.deepEqual(packagePayload(detailsForForm(product)), product);
  assert.equal(detailsPayload(detailsForForm(product)).weightKg, 6.125);
  assert.deepEqual(packagePayload(detailsForForm(null)), {});
  assert.throws(() => packagePayload(detailsForForm(null), true), /together/);
});
test("partial, negative, non-finite, oversize and over-precise measurements are rejected", () => {
  const valid = { weightKg: "5", lengthCm: "100", widthCm: "20", heightCm: "15" };
  for (const patch of [{heightCm:""},{weightKg:"0"},{weightKg:"Infinity"},{lengthCm:"601"},{widthCm:"-2"},{weightKg:"1.1234"},{widthCm:"1.123"}]) {
    assert.throws(() => packagePayload({...valid,...patch}));
  }
  assert.deepEqual(packagePayload(valid), {weightKg:5,lengthCm:100,widthCm:20,heightCm:15});
});
