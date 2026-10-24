import test from "node:test";
import assert from "node:assert/strict";
import { accountStatus, accessActions, filterUsers } from "../src/utils/userAccess.js";
const shopper = { userId: "1", username: "alex", firstName: "Alex", email: "alex@mercato.invalid", enabled: true, emailVerified: true, roles: ["ROLE_USER"], createdAt: "2026-10-01" };
const seller = { ...shopper, userId: "2", username: "taylor", firstName: "Taylor", sellerDisplayName: "Taylor Studio", sellerStatus: "APPROVED", roles: ["ROLE_USER", "ROLE_SELLER"], createdAt: "2026-10-02" };
test("admin is read-only; a shopper cannot receive unapproved selling access", () => {
  assert.deepEqual(accessActions({ ...seller, roles: ["ROLE_ADMIN", "ROLE_SELLER"] }), []);
  assert.deepEqual(accessActions(shopper), ["DEACTIVATE"]);
});
test("account and selling states are independent", () => {
  const suspended = { ...seller, sellerSuspended: true };
  assert.equal(accountStatus(suspended), "Active");
  assert.deepEqual(accessActions(suspended), ["DEACTIVATE", "RESTORE_SELLING"]);
  assert.deepEqual(accessActions({ ...suspended, enabled: false }), ["REACTIVATE"]);
  assert.deepEqual(accessActions({ ...suspended, enabled: false, accountLocked: true }), []);
  assert.deepEqual(accessActions({ ...shopper, enabled: false, selfDeactivated: true }), ["REACTIVATE", "DEACTIVATE"]);
  assert.equal(accountStatus({ ...shopper, enabled: false, selfDeactivated: true }), "Paused");
});
test("filters combine name/store, role and status without altering source order", () => {
  const users = [shopper, { ...seller, sellerSuspended: true }];
  assert.deepEqual(filterUsers(users, { search: "  studio ", role: "ROLE_SELLER", status: "selling-suspended" }).map(u => u.userId), ["2"]);
  assert.equal(filterUsers(users, { search: "nobody" }).length, 0);
  assert.deepEqual(users.map(u => u.userId), ["1", "2"]);
});
test("sorting and account status do not conflate email verification or seller approval", () => {
  assert.equal(accountStatus({ ...shopper, emailVerified: false }), "Awaiting verification");
  assert.equal(accountStatus({ ...shopper, enabled: false, emailVerified: false }), "Deactivated");
  assert.deepEqual(filterUsers([shopper, seller], {}).map(u => u.userId), ["2", "1"]);
  assert.deepEqual(filterUsers([shopper, seller], { sort: "oldest" }).map(u => u.userId), ["1", "2"]);
});
