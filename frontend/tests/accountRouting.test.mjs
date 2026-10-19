import test from "node:test";
import assert from "node:assert/strict";
import { accountLanding, loginDestination } from "../src/utils/accountRouting.js";
test("seller accounts land in their dashboard, including pending applicants", () => {
  assert.equal(accountLanding({ roles: ["ROLE_SELLER"] }), "/seller/dashboard");
  assert.equal(accountLanding({ roles: ["ROLE_USER"], sellerStatus: "PENDING" }), "/seller/dashboard");
  assert.equal(accountLanding({ roles: ["ROLE_USER"], sellerStatus: "REJECTED" }), "/seller/dashboard");
  assert.equal(accountLanding({ roles: ["ROLE_USER"] }), "/");
  assert.equal(accountLanding({ roles: ["ROLE_ADMIN", "ROLE_SELLER"] }), "/");
});
test("sign in preserves explicit destinations and rejects external redirects", () => {
  const seller = { roles: ["ROLE_SELLER"] };
  assert.equal(loginDestination(seller, "/checkout"), "/checkout");
  assert.equal(loginDestination(seller, "/admin/dashboard"), "/seller/dashboard");
  assert.equal(loginDestination({ roles: ["ROLE_USER"] }, "/seller/dashboard"), "/profile");
  assert.equal(loginDestination(seller, "//malicious.example"), "/seller/dashboard");
  assert.equal(loginDestination({ roles: ["ROLE_USER"] }, "https://malicious.example"), "/");
});
