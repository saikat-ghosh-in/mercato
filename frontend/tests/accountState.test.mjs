import test from "node:test";
import assert from "node:assert/strict";

let account = {};
try { account = await import("../src/utils/accountState.js"); } catch (error) { if (error.code !== "ERR_MODULE_NOT_FOUND") throw error; }

test("profile initialization waits for asynchronous identity and preserves same-account dirty fields", () => {
  assert.equal(typeof account.profileDraft, "function");
  assert.equal(account.profileDraft(null, null), null);
  const loaded = account.profileDraft(null, { userId: 1, firstName: "Alice" });
  assert.equal(loaded.form.firstName, "Alice");
  const dirty = { ...loaded, dirty: true, form: { ...loaded.form, firstName: "Edited" } };
  assert.equal(account.profileDraft(dirty, { userId: 1, firstName: "Refreshed" }), dirty);
  assert.equal(account.profileDraft(dirty, { userId: 2, firstName: "Bob" }).form.firstName, "Bob");
});

test("MFA flow separates enrollment, authenticator, recovery, replacement and email step-up", () => {
  assert.equal(typeof account.mfaAction, "function");
  assert.deepEqual(account.mfaAction("ENROLL_REQUIRED", "123456"), { url: "/auth/mfa/verify", data: { code: "123456" } });
  assert.equal(account.mfaAction("CHALLENGE_REQUIRED", "recovery", true).url, "/auth/mfa/recovery");
  assert.equal(account.mfaAction("STEP_UP_REQUIRED", "123456").url, "/auth/step-up/verify");
  assert.equal(account.mfaAction("VERIFIED", "123456", false, true).url, "/auth/mfa/replace");
  assert.deepEqual(account.mfaAction("VERIFIED", "", false, true), { url: "/auth/mfa/replace", data: { code: "" } });
  assert.throws(() => account.mfaAction("CHALLENGE_REQUIRED", "bad"), /six-digit/);
});

test("payment recovery accepts only the requested order and authoritative resumable session", () => {
  assert.equal(typeof account.recoveredPayment, "function");
  const response = { orderId: 23, totalAmount: 120, paymentSessionId: "server-session", canResume: true, deliveryAddress: { city: "Delhi" } };
  assert.equal(account.recoveredPayment(response, "23").totalAmount, 120);
  assert.throws(() => account.recoveredPayment({ ...response, orderId: 24 }, "23"), /couldn’t be resumed/);
  for (const invalid of [{ canResume: false }, { paymentSessionId: null }, { totalAmount: NaN }, { totalAmount: -1 }]) {
    assert.throws(() => account.recoveredPayment({ ...response, ...invalid }, "23"), /couldn’t be resumed/);
  }
});

test("payment return order identifiers cannot change an API path or query", () => {
  assert.equal(typeof account.validatedOrderId, "function");
  assert.equal(account.validatedOrderId("order-123_abc"), "order-123_abc");
  for (const value of [null, "", "../admin/users", "one?query=two", "one/two", "one#two", "a".repeat(101)]) assert.equal(account.validatedOrderId(value), null);
});
