import test from "node:test";
import assert from "node:assert/strict";
import { createSessionController, SessionChangedError } from "../src/backend/sessionState.js";
import { readPreferences, writePreferences } from "../src/utils/preferences.js";
import { wishlistReducer } from "../src/reduxStore/reducers/wishlistReducer.js";

const guest = { authenticated: false, user: null, contextId: "guest", expiresAt: null, mfa: { state: "NONE" } };
const member = { ...guest, authenticated: true, user: { userId: 7, roles: ["ROLE_USER"] }, contextId: "account-seven" };
const csrf = { token: "csrf-secret", headerName: "X-CSRF-TOKEN" };
const controller = (transport, options = {}) => createSessionController({ transport, ...options });

test("bootstrap is deduplicated and mutations use cookie CSRF and account context without bearer", async () => {
  const calls = [];
  const session = controller(async config => { calls.push(config.url); return config.url.endsWith("csrf") ? csrf : member; });
  await Promise.all([session.bootstrap(), session.bootstrap()]);
  assert.deepEqual(calls, ["/auth/session", "/auth/csrf"]);
  const request = await session.prepare({ url: "/cart/add", method: "post", headers: {} });
  assert.equal(request.headers["X-CSRF-TOKEN"], csrf.token);
  assert.equal(request.headers["X-Account-Context"], member.contextId);
  assert.equal(request.withCredentials, true);
  assert.equal(request.headers.Authorization, undefined);
  assert.equal(session.getSnapshot().status, "ready");
});

test("identity changes abort pending requests and fence even a transport that ignores cancellation", async () => {
  let identity = member;
  const session = controller(async config => config.url.endsWith("csrf") ? csrf : identity);
  await session.bootstrap();
  const old = await session.prepare({ url: "/user/orders", method: "get" });
  identity = guest;
  await session.refresh();
  assert.equal(old.signal.aborted, true);
  assert.throws(() => session.assertCurrent(old), SessionChangedError);
  assert.equal(session.getSnapshot().user, null);
});

test("bootstrap failure is recoverable and does not retain the previous private identity", async () => {
  let offline = false;
  const session = controller(async config => { if (offline) throw Error("offline"); return config.url.endsWith("csrf") ? csrf : member; });
  await session.bootstrap();
  offline = true;
  await assert.rejects(session.refresh(), /offline/);
  assert.equal(session.getSnapshot().status, "error");
  assert.equal(session.getSnapshot().user, null);
  offline = false;
  await session.bootstrap();
  assert.equal(session.getSnapshot().status, "ready");
});

test("valid legacy guest cart migrates with CSRF before private persistence is erased", async () => {
  const entries = new Map([["user", JSON.stringify({ token: "must-never-send", guestToken: "legacy-guest" })], ["persist:auth", "private"], ["persist:cart", "cart"]]);
  const operations = [];
  const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value), removeItem: key => { operations.push(`remove:${key}`); entries.delete(key); } };
  const session = controller(async config => {
    operations.push(config.url);
    assert.equal(config.headers?.Authorization, undefined);
    if (config.url.endsWith("csrf")) return csrf;
    if (config.url.endsWith("legacy-guest-cart")) {
      assert.deepEqual(config.data, { guestToken: "legacy-guest" });
      assert.equal(config.headers[csrf.headerName], csrf.token);
      assert.equal(entries.has("user"), true);
      assert.deepEqual(JSON.parse(entries.get("user")), { guestToken: "legacy-guest" });
    }
    return guest;
  }, { storage });
  await session.bootstrap();
  assert.ok(operations.indexOf("/auth/legacy-guest-cart") < operations.indexOf("remove:user"));
  assert.equal(entries.size, 0);
});

test("transient migration failure retains only legacy guest token for retry, never user or cart data", async () => {
  const entries = new Map([["user", JSON.stringify({ token: "old", guestToken: "legacy-guest" })], ["persist:auth", "private"]]);
  const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value), removeItem: key => entries.delete(key) };
  let offline = true;
  const session = controller(async config => {
    if (config.url.endsWith("legacy-guest-cart") && offline) throw Error("offline");
    return config.url.endsWith("csrf") ? csrf : guest;
  }, { storage });
  await assert.rejects(session.bootstrap(), /offline/);
  assert.equal(entries.has("persist:auth"), false);
  assert.deepEqual(JSON.parse(entries.get("user")), { guestToken: "legacy-guest" });
  offline = false;
  await session.bootstrap();
  assert.equal(entries.has("user"), false);
});

test("storage failure cannot prevent cookie session bootstrap", async () => {
  const storage = { getItem() { throw Error("denied"); }, removeItem() { throw Error("denied"); } };
  const session = controller(async config => config.url.endsWith("csrf") ? csrf : guest, { storage });
  await session.bootstrap();
  assert.equal(session.getSnapshot().status, "ready");
});

test("offline startup erases old bearer and private persistence before any response", async () => {
  const entries = new Map([["user", JSON.stringify({ token: "old-bearer", guestToken: "legacy" })], ["persist:auth", "private"]]);
  const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value), removeItem: key => entries.delete(key) };
  const session = controller(async () => { throw Error("offline"); }, { storage });
  await assert.rejects(session.bootstrap(), /offline/);
  assert.deepEqual(JSON.parse(entries.get("user")), { guestToken: "legacy" });
  assert.equal(entries.has("persist:auth"), false);
});

test("the strictest role controls idle expiry and an elapsed session clears private identity", async () => {
  let scheduled;
  let delay;
  let time = Date.parse("2026-10-22T00:00:00Z");
  let identity = { ...member, user: { ...member.user, roles: ["ROLE_USER", "ROLE_ADMIN"] }, expiresAt: "2026-10-23T00:00:00Z" };
  const session = controller(async config => config.url.endsWith("csrf") ? csrf : identity, {
    now: () => time, schedule: (callback, milliseconds) => { scheduled = callback; delay = milliseconds; return 1; }, cancel: () => {},
  });
  await session.bootstrap();
  assert.equal(delay, 30 * 60 * 1000);
  const old = await session.prepare({ url: "/user/orders" });
  time += 30 * 60 * 1000;
  identity = guest;
  scheduled();
  assert.equal(session.getSnapshot().user, null);
  assert.equal(old.signal.aborted, true);
  await session.bootstrap();
  assert.equal(session.getSnapshot().authenticated, false);
});

test("wishlist mutations cannot be overwritten by an older read", () => {
  let state = wishlistReducer(undefined, { type: "WISHLIST_READ_STARTED", requestId: "old" });
  state = wishlistReducer(state, { type: "WISHLIST_PENDING", productId: 1, value: true });
  state = wishlistReducer(state, { type: "ADD_WISHLIST_ID", payload: 1 });
  state = wishlistReducer(state, { type: "SET_WISHLIST_IDS", payload: [], requestId: "old" });
  assert.deepEqual(state.productIds, [1]);
});

test("preferences accept only a versioned safe schema and tolerate unavailable storage", () => {
  const memory = new Map();
  const storage = { getItem: key => memory.get(key), setItem: (key, value) => memory.set(key, value) };
  writePreferences({ theme: "dark", user: { email: "private" }, sort: "price-asc" }, storage);
  assert.deepEqual(readPreferences(storage), { theme: "dark", sort: "price-asc" });
  memory.set("mercato:preferences", JSON.stringify({ version: 99, theme: "dark" }));
  assert.deepEqual(readPreferences(storage), {});
  assert.deepEqual(readPreferences({ getItem() { throw Error("denied"); } }), {});
  assert.doesNotThrow(() => writePreferences({ theme: "dark" }, { setItem() { throw Error("denied"); } }));
});

test("blocked browser storage getters cannot crash preference reads or writes", () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  Object.defineProperty(globalThis, "localStorage", { configurable: true, get() { throw Error("storage disabled"); } });
  try {
    assert.deepEqual(readPreferences(), {});
    assert.doesNotThrow(() => writePreferences({ theme: "dark" }));
  } finally {
    if (descriptor) Object.defineProperty(globalThis, "localStorage", descriptor);
    else delete globalThis.localStorage;
  }
});

test("failed storage writes erase the old bearer while retaining the guest cart in memory for retry", async () => {
  const entries = new Map([["user", JSON.stringify({ token: "old-bearer", guestToken: "legacy" })]]);
  const storage = { getItem: key => entries.get(key), setItem() { throw Error("quota"); }, removeItem: key => entries.delete(key) };
  let offline = true;
  let migrated;
  const session = controller(async config => {
    if (offline) throw Error("offline");
    if (config.url.endsWith("legacy-guest-cart")) migrated = config.data.guestToken;
    return config.url.endsWith("csrf") ? csrf : guest;
  }, { storage });
  await assert.rejects(session.bootstrap(), /offline/);
  assert.equal(entries.has("user"), false);
  offline = false;
  await session.bootstrap();
  assert.equal(migrated, "legacy");
});

test("an unavailable cross-tab channel cannot leave identity refresh stuck at idle", async () => {
  const session = controller(async config => config.url.endsWith("csrf") ? csrf : guest, { publish() { throw Error("channel unavailable"); } });
  await session.bootstrap();
  await session.refresh({ broadcast: true });
  assert.equal(session.getSnapshot().status, "ready");
});

test("a delayed old bootstrap cannot replace a newer account's CSRF token", async () => {
  let release;
  let started;
  let first = true;
  const waiting = new Promise(resolve => { started = resolve; });
  const session = controller(async config => {
    if (config.url.endsWith("csrf") && first) { first = false; started(); return new Promise(resolve => { release = resolve; }); }
    return config.url.endsWith("csrf") ? { ...csrf, token: "new-csrf" } : member;
  });
  const old = session.bootstrap();
  const rejected = assert.rejects(old, SessionChangedError);
  await waiting;
  await session.refresh();
  release({ ...csrf, token: "old-csrf" });
  await rejected;
  const request = await session.prepare({ url: "/cart/add", method: "post" });
  assert.equal(request.headers[csrf.headerName], "new-csrf");
});

test("an unavailable migration endpoint cannot discard a valid legacy guest cart", async () => {
  const entries = new Map([["user", JSON.stringify({ guestToken: "legacy" })]]);
  const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value), removeItem: key => entries.delete(key) };
  const session = controller(async config => {
    if (config.url.endsWith("legacy-guest-cart")) throw { response: { status: 404 } };
    return config.url.endsWith("csrf") ? csrf : guest;
  }, { storage });
  await assert.rejects(session.bootstrap());
  assert.deepEqual(JSON.parse(entries.get("user")), { guestToken: "legacy" });
});
