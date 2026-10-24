// Cookie credentials and CSRF stay in memory. This controller is also used by
// the transport tests so response fencing is independent of React rendering.
export class SessionChangedError extends Error {
  constructor() { super("Your account changed. Please try again."); this.name = "SessionChangedError"; this.code = "SESSION_CONTEXT_CHANGED"; }
}

export const isPrivateRequest = (url = "") => /^\/(user|account|admin|seller|orders|order-line|cart|wishlist)(\/|\?|$)/.test(url) || url === "/auth/user";
const isMutation = method => !["get", "head", "options"].includes((method || "get").toLowerCase());
const safe = operation => { try { return operation(); } catch { return undefined; } };
const privateKeys = ["persist:auth", "persist:cart", "persist:sellers", "persist:categories", "persist:root", "paymentData", "clientSecret", "orderResponse"];

export function createSessionController({ transport, storage, publish = () => {}, now = () => Date.now(), schedule = setTimeout, cancel = clearTimeout }) {
  let snapshot = { status: "idle", authenticated: false, user: null, contextId: null, expiresAt: null, mfa: { state: "NONE" }, epoch: 0, error: null };
  let csrf = null;
  let pending = null;
  let legacyGuestToken;
  let abort = new AbortController();
  let expiry;
  const listeners = new Set();
  const emit = value => { snapshot = { ...snapshot, ...value }; listeners.forEach(listener => listener(snapshot)); };
  const assertEpoch = epoch => { if (epoch !== snapshot.epoch) throw new SessionChangedError(); };
  const retainLegacyGuest = token => {
    try { storage?.setItem("user", JSON.stringify({ guestToken: token })); }
    catch { safe(() => storage?.removeItem("user")); }
  };
  const invalidate = () => {
    cancel(expiry);
    abort.abort(); abort = new AbortController(); csrf = null; pending = null;
    emit({ status: "idle", authenticated: false, user: null, contextId: null, expiresAt: null, mfa: { state: "NONE" }, error: null, epoch: snapshot.epoch + 1 });
  };
  const scheduleExpiry = () => {
    cancel(expiry);
    const roles = snapshot.user?.roles || [];
    const idle = roles.includes("ROLE_ADMIN") ? 30 * 60 * 1000 : roles.includes("ROLE_SELLER") ? 60 * 60 * 1000 : snapshot.authenticated ? 24 * 60 * 60 * 1000 : snapshot.mfa?.state !== "NONE" ? 10 * 60 * 1000 : Infinity;
    const absolute = Date.parse(snapshot.expiresAt);
    const delay = Math.min(idle, Number.isFinite(absolute) ? absolute - now() : Infinity);
    if (Number.isFinite(delay)) {
      expiry = schedule(() => { invalidate(); bootstrap().catch(() => {}); }, Math.max(0, delay));
      expiry?.unref?.();
    }
  };
  const bootstrap = () => {
    if (pending) return pending;
    if (snapshot.status === "ready") return Promise.resolve(snapshot);
    const epoch = snapshot.epoch;
    const signal = abort.signal;
    emit({ status: "loading", error: null });
    pending = (async () => {
      let legacy = legacyGuestToken;
      if (legacy === undefined) safe(() => { legacy = JSON.parse(storage?.getItem("user") || "null")?.guestToken; });
      if (typeof legacy === "string" && legacy.length > 0 && legacy.length < 16384) { legacyGuestToken = legacy; retainLegacyGuest(legacy); }
      else safe(() => storage?.removeItem("user"));
      privateKeys.forEach(key => safe(() => storage?.removeItem(key)));
      try {
        let session = await transport({ url: "/auth/session", method: "get", withCredentials: true, signal });
        assertEpoch(epoch);
        let csrfResponse = await transport({ url: "/auth/csrf", method: "get", withCredentials: true, signal });
        assertEpoch(epoch);
        if (!csrfResponse?.token || !csrfResponse?.headerName || !session?.contextId) throw Error("Couldn’t initialize your session. Please retry.");
        if (typeof legacy === "string" && legacy.length > 0 && legacy.length < 16384) {
          // Legacy JWTs are never sent as authentication. Only a guest cart can
          // be redeemed once through the dedicated, CSRF protected endpoint.
          try {
            await transport({ url: "/auth/legacy-guest-cart", method: "post", data: { guestToken: legacy }, headers: { [csrfResponse.headerName]: csrfResponse.token, "X-Account-Context": session.contextId }, withCredentials: true, signal });
          } catch (error) {
            assertEpoch(epoch);
            if (![400, 401, 410].includes(error.response?.status)) {
              retainLegacyGuest(legacy);
              throw error;
            }
          }
          assertEpoch(epoch);
          legacyGuestToken = null;
          safe(() => storage?.removeItem("user"));
          session = await transport({ url: "/auth/session", method: "get", withCredentials: true, signal });
          assertEpoch(epoch);
          csrfResponse = await transport({ url: "/auth/csrf", method: "get", withCredentials: true, signal });
          assertEpoch(epoch);
        } else safe(() => storage?.removeItem("user"));
        if (!csrfResponse?.token || !csrfResponse?.headerName || !session?.contextId) throw Error("Couldn’t initialize your session. Please retry.");
        csrf = csrfResponse;
        emit({ ...session, user: session.authenticated ? session.user : null, status: "ready", error: null, epoch });
        scheduleExpiry();
        return snapshot;
      } catch (error) {
        if (epoch === snapshot.epoch) emit({ status: "error", authenticated: false, user: null, contextId: null, error: "Your session couldn’t be checked. Please try again." });
        throw error;
      } finally { if (epoch === snapshot.epoch) pending = null; }
    })();
    return pending;
  };
  return {
    getSnapshot: () => snapshot,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
    bootstrap,
    invalidate,
    refresh({ broadcast = false } = {}) { invalidate(); if (broadcast) safe(publish); return bootstrap(); },
    async prepare(config) {
      const epoch = snapshot.epoch;
      await bootstrap();
      assertEpoch(epoch);
      const headers = { ...config.headers };
      delete headers.Authorization;
      delete headers.authorization;
      if (isMutation(config.method)) headers[csrf.headerName] = csrf.token;
      if (isPrivateRequest(config.url) || isMutation(config.method)) headers["X-Account-Context"] = snapshot.contextId;
      const signal = config.signal ? AbortSignal.any([config.signal, abort.signal]) : abort.signal;
      return { ...config, headers, signal, withCredentials: true, sessionEpoch: epoch };
    },
    assertCurrent(config) { assertEpoch(config.sessionEpoch); if (config.signal?.aborted) throw new SessionChangedError(); },
    recordActivity() { if (snapshot.status === "ready") scheduleExpiry(); },
  };
}
