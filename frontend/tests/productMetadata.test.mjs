import test from "node:test";
import assert from "node:assert/strict";
import { createProductMetadataCache } from "../src/utils/productMetadataCache.js";

test("quantity refreshes reuse product metadata and retry only failed products", async () => {
  const calls = [];
  let fail = true;
  const cache = createProductMetadataCache(async id => {
    calls.push(id);
    if (id === "two" && fail) throw Error("offline");
    return { productId: id, primaryImageUrl: `${id}.jpg` };
  });
  const signal = new AbortController().signal;
  const first = await cache.load(["one", "two"], signal);
  assert.equal(first.one.primaryImageUrl, "one.jpg");
  assert.equal(first.two, undefined);
  fail = false;
  const second = await cache.load(["one", "two"], signal);
  assert.equal(second.one, first.one);
  assert.equal(second.two.primaryImageUrl, "two.jpg");
  await cache.load(["one", "two"], signal);
  assert.deepEqual(calls, ["one", "two", "two"]);
});

test("an aborted metadata lookup cannot poison the cache for the next bag", async () => {
  let finish;
  const cache = createProductMetadataCache(() => new Promise(resolve => { finish = resolve; }));
  const controller = new AbortController();
  const pending = cache.load(["old"], controller.signal);
  controller.abort();
  finish({ productId: "old" });
  assert.equal(await pending, null);
  assert.deepEqual(cache.missing(["old", "new"]), ["old", "new"]);
});
