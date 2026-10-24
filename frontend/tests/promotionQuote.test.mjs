import test from "node:test";
import assert from "node:assert/strict";
import { promotionQuote } from "../src/utils/promotionQuote.js";
test("promotion quote uses paise and the complete fixed duration", () => {
  assert.equal(promotionQuote(99, 3), 297);
  assert.equal(promotionQuote("49.99", 7), 349.93);
  assert.equal(promotionQuote("199.10", 14), 2787.4);
});
test("invalid promotion terms cannot produce a bookable quote", () => {
  for (const rate of [undefined, -10, 0, Infinity, 1001]) assert.equal(promotionQuote(rate, 3), null);
  for (const days of [0, 1, 8, 1000]) assert.equal(promotionQuote(49, days), null);
});
