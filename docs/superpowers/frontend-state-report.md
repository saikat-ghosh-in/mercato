# Frontend session and private-state hardening

Date: 23 October 2026. Scope: approved hardening Tasks 4a frontend adapter, 4b authentication screens, and 5. No staging, commits, dependency installation, hosting changes, or browser acceptance were performed by this agent.

## Implemented behavior

- The Axios adapter uses same-origin `/api`, browser cookies, in-memory CSRF, and `X-Account-Context` on every mutation and private/cart read. It removes caller-supplied bearer authorization. `VITE_BACKEND_BASE_URL` no longer bypasses the ingress proxy; Vite already proxies `/api` locally.
- A single session controller provides idle/loading/ready/error states, deduplicated bootstrap, retry, expiry, request cancellation, and generation fences. Session changes clear private Redux state and RTK Query middleware subscriptions/timers. Account routes remount so previous-account local forms and component state cannot remain visible.
- Cross-tab messages contain only an identity-change event and random event ID. BroadcastChannel and storage events invalidate locally and bootstrap from the server. Storage and channel failures do not prevent cookie authentication.
- `401` on private reads, failed CSRF/forbidden mutations, and `409 SESSION_CONTEXT_CHANGED` invalidate the old state. Rejected mutations are never automatically replayed.
- Bootstrap, sign-in, sign-out, completed MFA, password reset, and deactivation use server identity. UI guards and header account state no longer inspect browser JWTs.
- Client expiry uses the strictest role idle bound (customer 24h, seller 1h, administrator 30m), capped by immutable server absolute expiry. Successful HTTP traffic renews the client idle timer; the backend remains authoritative.
- Legacy guest tokens are redeemed only through `POST /auth/legacy-guest-cart`, with CSRF and context. Old bearer and private Redux persistence are erased independently of network success. Retryable migration failures preserve only the guest token; a failed storage write erases the old bearer and retains the guest token in memory for retry. Missing migration endpoints and CSRF/context failures do not discard a valid guest cart. Successful redemption or explicit invalid/expired proof clears the legacy token.
- Redux Persist and PersistGate were removed from application initialization. Auth, address, cart, wishlist, and payment state remain in memory. The installed `redux-persist` dependency was not changed by this agent. A versioned preference helper permits only known theme/sort values and safely handles malformed/unavailable storage; the current UI has no preference persistence to migrate.
- RTK Query now owns profile, address, wishlist-product, order-summary, management-overview, and recoverable-payment-session reads. Other detailed management reads remain incremental consumers of the shared fenced adapter.
- The existing serialized cart queue and server-calculated totals remain intact. Identity changes invalidate queued old-account cart operations.
- Wishlist toggles wait for initial IDs. Read IDs are invalidated by mutations, and stale ID reads cannot overwrite mutation results. Product queries invalidate after a successful toggle.
- Profile editing waits for asynchronous profile data, preserves a dirty draft during same-account refresh, and resets for another identity. Awaited address/profile/order continuations also check the generation before changing Redux state.
- Payment handoffs include `orderId` in the URL. Reload performs only authorized `GET /user/orders/{orderId}/payment-session`; it never creates or retries an order. The returned total, delivery address, resumability, and payment session are used. Legacy/canonical payment-return identifiers are validated before API interpolation.
- `/auth/mfa` has explicit enrollment, authenticator challenge, recovery-code, replacement, customer email step-up, and recovery-code acknowledgment states. Replacement accepts a current code or blank proof when the backend has a recent established authenticator/recovery verification. Setup secrets and recovery codes stay in component memory. Recovery codes wrap at narrow widths. Sign-in restarts are hidden during recovery-code acknowledgment because the backend has already rotated context. Acknowledgment refreshes session/CSRF and broadcasts the identity change.

## Files added

- `frontend/src/backend/sessionState.js`
- `frontend/src/components/auth/SessionGate.jsx`
- `frontend/src/components/auth/Mfa.jsx`
- `frontend/src/reduxStore/reducers/sessionBoundary.js`
- `frontend/src/reduxStore/services/privateApi.js`
- `frontend/src/utils/accountState.js`
- `frontend/src/utils/preferences.js`
- `frontend/tests/session.test.mjs`
- `frontend/tests/sessionIntegration.test.mjs`
- `frontend/tests/accountState.test.mjs`

## Existing files changed by this increment

- `frontend/.env.example`
- `frontend/src/App.jsx`
- `frontend/src/backend/api.js`
- `frontend/src/main.jsx`
- `frontend/src/components/Header.jsx`
- `frontend/src/components/PrivateRoute.jsx`
- `frontend/src/components/admin/AdminOverviewTab.jsx`
- `frontend/src/components/auth/ResetPassword.jsx`
- `frontend/src/components/checkout/Checkout.jsx`
- `frontend/src/components/orders/Orders.jsx`
- `frontend/src/components/payment/CashfreePayment.jsx`
- `frontend/src/components/payment/PaymentConfirmation.jsx`
- `frontend/src/components/seller/SellerOverviewTab.jsx`
- `frontend/src/components/shared/WishlistToggle.jsx`
- `frontend/src/components/user/Addresses.jsx`
- `frontend/src/components/user/Profile.jsx`
- `frontend/src/components/user/ProfileEdit.jsx`
- `frontend/src/components/wishlist/Wishlist.jsx`
- `frontend/src/reduxStore/actions/addressActions.js`
- `frontend/src/reduxStore/actions/authActions.js`
- `frontend/src/reduxStore/actions/cartActions.js`
- `frontend/src/reduxStore/actions/orderActions.js`
- `frontend/src/reduxStore/actions/wishlistActions.js`
- `frontend/src/reduxStore/reducers/authReducer.js`
- `frontend/src/reduxStore/reducers/store.js`
- `frontend/src/reduxStore/reducers/wishlistReducer.js`
- `frontend/src/utils/paymentUtils.js`
- `frontend/src/utils/tokenManager.js`

## Regression evidence

The new controller test file first failed because the controller module did not yet exist. Subsequent assertion-level red/green cycles reproduced and fixed: old bearer retained on offline startup; strict-role idle expiry missing; blocked localStorage property getters throwing; failed persistence writes retaining a bearer; unavailable channel leaving bootstrap idle; delayed old bootstrap overwriting a new CSRF token; and a missing migration endpoint incorrectly discarding a guest token.

Account-state tests had assertion-level reds before profile draft hydration, MFA endpoint selection, safe payment-session recovery, optional recent-proof replacement, and payment-return ID validation were implemented.

The real Axios/Redux Toolkit integration tests exercise cookie/CSRF/context headers, bearer stripping, canceled delayed account-A responses after account B logs in, and a server context conflict without mutation replay. A further red/green integration test changes the account between RTK fulfillment and its legacy thunk continuation; it caught `USER_ADDRESS` restoring invalid old-request state after the reset, now prevented by the generation check.

Final commands run from `frontend/`:

- `npm test`: 66 tests passed, 0 failed (43 existing, 23 added).
- `npm run lint`: passed, exit 0, no diagnostics.
- `npm run build`: passed, exit 0, Vite 7.3.1, 455 modules transformed. Main bundle 522.23 kB (173.83 kB gzip); Vite emits its >500 kB advisory.

## Original dirty work preserved

The starting snapshot is `backend/target/hardening-baseline/manifest.txt` (54 original tracked dirty/deleted paths, including README and backend local properties). No original UI file was reverted. In that manifest, the frontend files also edited by this increment are: `App.jsx`, `AdminOverviewTab.jsx`, `ResetPassword.jsx`, `Checkout.jsx`, `Orders.jsx`, `CashfreePayment.jsx`, `PaymentConfirmation.jsx`, `SellerOverviewTab.jsx`, `Addresses.jsx`, `Profile.jsx`, `ProfileEdit.jsx`, and `Wishlist.jsx`. Their existing markup and visual treatment were retained while adding the state behavior above.

All other original frontend manifest entries were checked against their recorded SHA-256 and remained byte-identical; the original `HomeData.js` deletion was preserved. The original untracked `PageHeading.jsx`, `workspace.css`, and `useContentTransition.js` were left in place and were not edited by this agent. `frontend/src/index.css` is byte-identical to the initial snapshot. Dependency changes belong to the root task, not this increment.

## Acceptance limits and integration requirements

- No live browser, desktop/mobile layout, actual multi-tab browser, authenticator, Cashfree, or email-delivery acceptance is claimed. The root task owns that verification.
- The backend session/MFA contracts were coordinated with the session agent. Private reads also need server context mismatch rejection to cover the interval before cross-tab messages arrive; that agent confirmed the filter change.
- The authorized payment-session recovery endpoint is owned by the root task and must be available before the handoff screen can complete its recovery read.
- Query migration is incremental: profile/address/wishlist/order/overview reads use RTK Query; existing detailed management screens still use the shared cancelable/fenced Axios adapter.
- If both browser cross-tab mechanisms are unavailable, other tabs learn of a change through a context-rejected API request or expiry; server context checks remain the data-isolation boundary.
- Recovery-code acknowledgment deliberately precedes the frontend identity refresh. Navigating away or reloading before saving the one-time display loses those displayed codes; the user can replace the authenticator using established proof. The screen explicitly asks the user to save the codes.
- Production bundling previously emitted the existing >500 kB main-chunk advisory; the final build outcome is recorded above. No performance redesign or new UI dependencies were introduced.

## Final root verification, 24 October 2026

See docs/HARDENING_REVIEW.md for the final 185-test backend run, 72-test frontend run, scoped dependency assessments, Browser evidence and remaining external gates. Earlier pending/partial runs above are historical. Local PostgreSQL 18.1 passed; PostgreSQL 14.24 Testcontainers remains required in CI. No live deployment or paid resource was created.
