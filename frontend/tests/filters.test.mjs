import test from "node:test";
import assert from "node:assert/strict";
import { applyFilterDraft, emptyFilterDraft, filterDraftReducer, validateFilterDraft } from "../src/utils/filterDraft.js";
import { parseParams } from "../src/utils/productUtils.js";

test("filter edits are isolated until Apply and reopening discards cancelled drafts", () => {
  const url = new URLSearchParams("category=Fashion&sellers=Studio&keyword=linen");
  const applied = parseParams(url);
  let drawer = filterDraftReducer(undefined, { type: "OPEN", filters: applied });
  drawer = filterDraftReducer(drawer, { type: "CHANGE", updates: { category: "Electronics", sellers: ["Other"] } });
  assert.equal(url.get("category"), "Fashion");
  assert.deepEqual(applied.sellers, ["Studio"]);
  drawer = filterDraftReducer(drawer, { type: "CLOSE" });
  assert.equal(drawer.open, false);
  drawer = filterDraftReducer(drawer, { type: "OPEN", filters: parseParams(url) });
  assert.equal(drawer.draft.category, "Fashion");
  assert.deepEqual(drawer.draft.sellers, ["Studio"]);
});

test("Apply commits all filters together and preserves search/sort", () => {
  const before = new URLSearchParams("keyword=tea+%26+coffee&sortBy=sellingPrice&sortingOrder=asc&pageNumber=8&category=Fashion");
  const after = applyFilterDraft(before, { category: "Home & Living", sellers: ["100% Good", "Studio"], minPrice: "0", maxPrice: "2500", inStockOnly: true });
  assert.equal(before.get("category"), "Fashion");
  assert.equal(after.get("category"), "Home & Living");
  assert.equal(after.get("keyword"), "tea & coffee");
  assert.equal(after.get("sortBy"), "sellingPrice");
  assert.equal(after.get("sortingOrder"), "asc");
  assert.equal(after.get("pageNumber"), "0");
  assert.equal(after.get("sellers"), "100% Good,Studio");
  assert.equal(after.get("minPrice"), "0");
  assert.equal(after.get("maxPrice"), "2500");
  assert.equal(after.get("inStock"), "true");
});

test("clearing a draft is reversible and only removes filters when applied", () => {
  const url = new URLSearchParams("category=Fashion&minPrice=200&maxPrice=900&sellers=Studio&inStock=true&keyword=shirt&sortBy=productName");
  let drawer = filterDraftReducer(undefined, { type: "OPEN", filters: parseParams(url) });
  drawer = filterDraftReducer(drawer, { type: "CLEAR" });
  assert.deepEqual(drawer.draft, emptyFilterDraft());
  assert.equal(url.get("category"), "Fashion");
  const result = applyFilterDraft(url, drawer.draft);
  for (const key of ["category", "minPrice", "maxPrice", "sellers", "inStock"]) assert.equal(result.has(key), false);
  assert.equal(result.get("keyword"), "shirt");
  assert.equal(result.get("sortBy"), "productName");
});

test("invalid prices never commit and validation keeps the drawer open", () => {
  for (const prices of [{minPrice:"-1"}, {maxPrice:"-2"}, {minPrice:"NaN"}, {maxPrice:"Infinity"}, {minPrice:"100",maxPrice:"50"}]) {
    const draft = { ...emptyFilterDraft(), ...prices };
    const message = validateFilterDraft(draft);
    assert.ok(message);
    const url = new URLSearchParams("category=Fashion");
    assert.throws(() => applyFilterDraft(url, draft));
    assert.equal(url.toString(), "category=Fashion");
    const state = filterDraftReducer({open:true,draft,error:""}, {type:"ERROR",message});
    assert.equal(state.open,true);
    assert.equal(state.draft,draft);
  }
  assert.equal(validateFilterDraft({...emptyFilterDraft(),minPrice:"0",maxPrice:"0"}), "");
  assert.equal(validateFilterDraft({...emptyFilterDraft(),minPrice:"10.50",maxPrice:"20.99"}), "");
});

test("opening filters after URL navigation uses the latest applied values", () => {
  let drawer = filterDraftReducer(undefined, { type:"OPEN",filters:parseParams(new URLSearchParams("category=Fashion")) });
  drawer = filterDraftReducer(drawer, {type:"CLOSE"});
  drawer = filterDraftReducer(drawer, {type:"OPEN",filters:parseParams(new URLSearchParams("category=Electronics&inStock=true"))});
  assert.equal(drawer.draft.category,"Electronics");
  assert.equal(drawer.draft.inStockOnly,true);
});
