import test from "node:test";
import assert from "node:assert/strict";
import { defaultTimeframe, validateTimeframe, timeframeLabel } from "../src/utils/reportTimeframe.js";
const today = new Date("2026-10-19T18:30:00Z");
test("default financial timeframe covers 90 UTC dates", () => {
  assert.deepEqual(defaultTimeframe(today), { from: "2026-07-22", through: "2026-10-19" });
  assert.equal(validateTimeframe(defaultTimeframe(today), today), "");
  assert.equal(timeframeLabel(defaultTimeframe(today)), "22 Jul 2026 – 19 Oct 2026");
});
test("timeframe validation rejects incomplete, impossible, reversed, future or oversized dates", () => {
  for (const range of [
    { from: "", through: "2026-10-19" },
    { from: "2026-02-30", through: "2026-10-19" },
    { from: "2026-10-19", through: "2026-10-18" },
    { from: "2026-10-19", through: "2026-10-20" },
    { from: "2025-01-01", through: "2026-10-19" },
  ]) assert.notEqual(validateTimeframe(range, today), "");
  assert.equal(validateTimeframe({ from: "2025-10-19", through: "2026-10-19" }, today), "");
  assert.equal(validateTimeframe({ from: "2024-02-29", through: "2024-03-01" }, today), "");
});
