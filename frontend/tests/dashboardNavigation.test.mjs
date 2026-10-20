import test from "node:test";
import assert from "node:assert/strict";
import { activeDashboardTab, dashboardParams } from "../src/utils/dashboardNavigation.js";
const items = [{ id: "overview" }, { id: "products" }, { id: "notices" }];
test("dashboard navigation preserves unrelated URL state without mutating the current location", () => {
  const original = new URLSearchParams("tab=overview&seller=demo&range=month");
  const next = dashboardParams(original, "products");
  assert.equal(next.get("tab"), "products");
  assert.equal(next.get("seller"), "demo"); assert.equal(next.get("range"), "month");
  assert.equal(original.get("tab"), "overview");
});
test("valid deep links select their tab and invalid/missing tabs fall back to overview", () => {
  assert.equal(activeDashboardTab(items, new URLSearchParams("tab=notices")), "notices");
  assert.equal(activeDashboardTab(items, new URLSearchParams("tab=users")), "overview");
  assert.equal(activeDashboardTab(items, new URLSearchParams()), "overview");
});
