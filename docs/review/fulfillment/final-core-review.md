# Part A — Task 5–6 spec compliance and quality

**Spec compliance: Issues found. Task quality: Needs fixes.** The small admin delivery/current-proof increment and the existing-screen adapters are present and appropriately scoped. Checkout recovery exposes two integration gaps, I1 and I2 below; the task's safe retry/current-state requirements are therefore not fully met.

Target: task `3e14fad536e28d253f62c7836bca16552110c030..e75c3726ae08a9b8ec5ef2cadafe1c6fe1b29cf7`; whole branch `970645b4fbb140a92f5126f8d5c282da5d19bb69..e75c3726ae08a9b8ec5ef2cadafe1c6fe1b29cf7`.

## Strengths

- `backend/src/main/java/com/mercato/Service/FulfillmentWorkflow.java:63` requires current collection proof for both SHIP and DELIVER, alongside the existing funding/allocation guards; its quantity maxima keep delivery bounded by shipped minus delivered. `OrderLineUpdateServiceImpl.java:46` deliberately leaves financial unit identities unchanged for delivery and `:53` changes only delivery counters/history. V14 only extends the transition action constraint. The task PostgreSQL regressions exercise permission rejection, proof loss, partial/full delivery, replay, stale versions and stock settlement only at shipment.
- `frontend/src/components/admin/OrdersTab.jsx:128` wires all supported admin actions through the existing QuantityCommand and authoritative maxima/version. Seller controls remain limited to accept/prepare/pack/cancel in `frontend/src/components/seller/FulfillmentTab.jsx:103`.
- `frontend/src/utils/checkout.js:2` retains the complete immutable submitted request; `Checkout.jsx:174` puts only its opaque operation ID in the URL. `backend/src/main/java/com/mercato/Commerce/PaymentSessionRecovery.java:17` scopes lookup to the authenticated buyer, and `Controller/OrderController.java:91` returns private/no-store recovery. The UI fences late account responses and retains address state on 409.
- `frontend/src/utils/fulfillment.js:35` clamps elapsed display time at the latest receipt. The changed browser regression keeps the release timestamp immutable and confirms the read-only label disappears after receipt beyond that boundary. The payment wrapper correction has a meaningful single-visible-action assertion at both mobile widths.
- `frontend/src/components/shared/MarketplaceFinance.jsx:23` surfaces recognition basis and separates captured buyer money, retained fees, commission and platform accrual; DashboardWidgets labels the shared revenue field as seller earnings. Policy copy accurately defers customer returns and delayed capture.

# Part B — Whole-branch integration and beta readiness

**Ready for the scoped beta release: With fixes.** Resolve I1 and I2 in one bounded fix wave, then run their focused regressions and scoped re-review before rollout. There is no request for another subsystem or a production/compliance readiness claim.

## Additional strengths

- `backend/src/main/java/com/mercato/Commerce/OrderMoneySnapshot.java:18` bounds unit materialization before side effects and snapshots exact money/provenance. V11–V13 preserve unknown legacy facts instead of rebuilding historical prices or physical units.
- `backend/src/main/java/com/mercato/Service/CartServiceImpl.java:235` preserves original versus explicit retry-selection lineage; deterministic cart/product locking and the versioned checkout identity prevent the reviewed duplicate/lost-selection paths. The gap is that not every exposed resume path uses this existing machinery.
- `backend/src/main/java/com/mercato/Commerce/RefundObligations.java:29` recognizes eventual attributed closure, subtracts committed component allocations and bounds obligations by actual captured funds. `Service/FulfillmentFunding.java:18` permits sufficiently funded survivors after attributed partial refunds. The final PostgreSQL survivor regression also confirms delivered units retain SHIPPED financial identities.
- `backend/src/main/java/com/mercato/Service/MarketplaceFinanceService.java:27` excludes unverified legacy collection claims from paid earnings; `:129` uses attributed committed merchandise refunds for bounded commission reversals, and known forward delivery costs survive refunds. Finance, monthly reports, dashboard earnings and promotion budget share the calculator.
- The production package changes no authentication/security configuration. The focused route check at `backend/src/main/java/com/mercato/Security/SpringSecurityConfig.java:15` and `:20` retains CSRF and administrator/seller route protection; command/lookup ownership remains server-derived. Additive V9–V14 introduce no invented delivery date or destructive database reset.

## Critical — Must fix

None found.

## Important — Should fix before beta

### I1 — Exact-intent replay and recovered payment sessions bypass retry stock preparation

**Locations:** `backend/src/main/java/com/mercato/Service/OrderServiceImpl.java:58`–`:64`, `:92`; `backend/src/main/java/com/mercato/Commerce/PaymentSessionRecovery.java:29`–`:37`; `frontend/src/components/checkout/Checkout.jsx:177`.

`prepareOrder` returns an existing intent before preparing holds, and `placeOrder` immediately returns its stored session or recreates the gateway order. Neither path calls the V12 retry hold restoration/independent-stock preflight in `OrderRetryHoldService.java:27`. The new PAYMENT_READY UI bypasses capture entirely; its next CashfreePayment screen GETs the by-order recovery at `frontend/src/components/payment/CashfreePayment.jsx:18` and opens the SDK at `:44`. That recovery also performs no hold preparation. Thus the adjacent ready-session path does not close the gap.

**Reproduction:** Start a two-unit checkout with physical stock exactly two; make initial gateway creation time out after the durable local intent commits. Remove the original bag row and re-add two of that product as a new selection. Recover REPLAY_CAPTURE and submit the original operation/body. Gateway creation is reached with the new selection's two-unit hold consuming all stock. Allocation intentionally refuses that unrelated hold (`OrderReservationServiceImpl.java:93`–`:100`), falls back to a new order reservation and fails for insufficient independent stock. The buyer can pay before this deterministic failure and receive an automatic refund. A missing/reduced selection similarly lacks the explicit retry binding; expiry can remove the hold entirely. The same issue exists when a stored session is resumed after bag edits.

**Minimal fix:** Reuse the existing order-first retry preparation/preflight before returning/recreating a payable intent's session and before opening a recovered stored session. Preserve the immutable intent/body, original quote and unrelated new bag selections. Keep the lookup GET private and free of gateway calls; a small existing-style POST preparation step or routing the handoff through the existing retry endpoint is sufficient. Do not add a reservation engine.

**Regression paths:** Extend the existing real-PG checkout tests through `placeOrder` exact replay after an uncertain create, with missing and reduced selections at exact stock; verify allocation succeeds once with the explicit retry binding. With an adequate unrelated new selection holding all stock, verify rejection before gateway/payment exposure and preservation of that selection. Cover PAYMENT_READY → by-order session recovery/handoff using the same guarded preparation. Existing V12 tests call `retryPayment`; the new mocked checkout browser fixture does not exercise backend allocation, so their green evidence does not cover this seam.

### I2 — Cancellation and current payment state do not fence every session resume

**Locations:** `backend/src/main/java/com/mercato/Service/OrderLineUpdateServiceImpl.java:60`–`:65`; `backend/src/main/java/com/mercato/Service/OrderServiceImpl.java:61`–`:72`; `backend/src/main/java/com/mercato/Commerce/PaymentSessionRecovery.java:29` and `:36`.

The rewritten cancellation path invokes only refund handling; it drops the prior whole-order `terminateOrder` call. For an uncaptured order the refund delegate returns without queuing provider termination. An already opened provider checkout therefore remains active after cancellation. Exact capture replay returns an existing session at `OrderServiceImpl.java:62` before checking CANCELLED or other nonpayable states. With no stored session it can perform gateway creation before the cancellation check; the termination enqueued inside the later transaction is followed by an exception, so that enqueue rolls back.

The by-order recovery gate is also weaker than the new intent gate: `PaymentSessionRecovery.java:36` ignores authorization evidence and financial halt. It can expose a session for a reader-halted/authorized order that the intent lookup correctly marks ORDER_UPDATED. For an unpaid partial cancellation both recovery gates still consider the unchanged original-total checkout payable because status remains CREATED.

**Reproduction:** Open an unpaid two-unit checkout, then cancel one unit through the quantity endpoint. Resume the stored session. It still charges the immutable original two-unit amount; `PaidOrderAllocation.java:26`–`:29` then detects the prior cancellation, fully refunds and cancels the remaining unit too. Alternatively cancel the entire order while a capture response is uncertain: replay of the old operation still returns its stored session, and no provider termination was queued. A CREATED order with current authorization/financial halt likewise remains resumable through the by-order GET.

**Minimal fix:** Reuse the existing durable provider termination/reconciliation jobs atomically when cancellation makes an uncaptured immutable checkout unpayable. Apply one consistent current-payability predicate before all gateway creation/session returns and both recovery paths: closed/cancelled quantities, captured funds, authorization or financial halt must route to the current order/review state rather than exposing the old checkout. Retain capture-race reconciliation/refund behavior and perform no gateway I/O under locks. Do not silently rebuild or reprice the remaining order.

**Regression paths:** Exercise partial and whole unpaid cancellation through the current quantity/whole APIs; assert durable termination/reconciliation is committed once, subsequent exact replay and both recovery routes expose no payable session, and no new gateway create occurs for a closed decision. Cover cancellation racing an uncertain create, plus CREATED+authorized and CREATED+financial-halt by-order recovery. Keep the existing capture-after-cancellation refund regression.

## Minor — Recorded/deferred

- Existing framework/error-path log noise, dense service formatting and the Vite >500 kB chunk advisory remain the previously recorded nonblocking items. Saved counts are green; no infrastructure upgrade or formatting sweep is required for this sandbox.
- No new polish request is added. The formerly deferred display-clock M1 is addressed by the clamp and immutable-boundary regression.

## Verification and limits

- Read the full production/migration package once in bounded passes, recovering output-truncated sections from that package; read task 5–6 test changes and the relevant full-branch checkout reservation/intent regressions. Requirements came from the combined brief and latest scope/rulings, not the older parcel/settlement backlog.
- Named focused dependency checks: checkout retry/selection ownership and stock allocation (`OrderServiceImpl.retryPayment`, `OrderRetryHoldService`, `OrderReservationServiceImpl`); resumed-session handoff (`CashfreePayment`, `paymentUtils`, both recovery methods); cancellation termination/refund delegation (`CashfreeServiceImpl`, `RefundServiceImpl`); cleanup compatibility (`AbandonedOrderCleanupJob`, reservation cleanup and its repository query); role/CSRF routing (`SpringSecurityConfig`, `AuthUtil`). Checkout initial declarations and retry/cancellation bodies were read because package hunks ended mid-function or omitted that concrete call path. No additional blocker is asserted for the cleanup check.
- Inspected saved backend counts: **360 tests / 67 suites / 0 failures / 0 errors / 0 skips**. Saved frontend result: **95/95**, lint clean, build successful with the known chunk advisory. Saved CSS browser result: **18/18**, then final affected checkout **4/4** after the mobile wrapper change. The saved backend log contains known framework/error-path stack output; it is not described as pristine.
- Controller separately reports **CI 40550964948 succeeded for exact e75c372**, a ready locked Netlify candidate and validated private beta database backup. These are controller-supplied release facts, not live actions independently performed by this reviewer.
- No tests or git commands were rerun. I1/I2 are static call-path findings with explicit reproducible state sequences; no new runtime reproduction is claimed. No source/index/HEAD changes, subagents, deployment, gateway or SMTP calls were made. Only this report was written.

## Cannot verify / remaining external gates

- I1/I2 fixes and their focused regression/re-review evidence remain required. The supplied complete-suite results belong to e75c372, before those fixes.
- Controller must finish integrated beta/browser verification against the compatible backend/frontend, additive migrations with retained MFA/data, ordinary sandbox provider capture/cancel/refund, seller/admin quantity journeys and matching finance. Synthetic route fixtures do not establish live provider behavior.
- Actual hardware-phone behavior remains unverified. The saved browser evidence covers compiled CSS at 320/390px, keyboard and reduced motion.
- Keep authorization activation OFF and production/main unchanged; release/publishing restoration belongs to the controller after the fixes and its live gates.

## Declined to judge

- New parcel/tracking/RMA/customer-return models and UI: explicitly deferred by the latest repeated side-project scope ruling; internal finance hooks are not an exposed returns feature.
- Delayed-capture settlement/expiry activation, emergency/drain infrastructure and enabled-account authorization proof: deferred and OFF; not required to complete ordinary sandbox collection.
- Condition deductions, reverse logistics, cost disputes/reserves/recovery and extensive analytics: explicit backlog, not core beta blockers.
- Real-money legal/tax/invoice/carrier/payout compliance or commercial readiness: outside this sandbox release and not claimed.
- Broad stylistic refactors, new test infrastructure, speculative scaling and bundle splitting: no demonstrated core failure; existing minors stay deferred.
- Changing current 0.5%/₹500-per-unit commission, 60-day notices or seller-paid shipping policy: prohibited scope changes; the review checks preservation instead.

**Final assessment: Part A needs fixes; Part B ready for beta with I1/I2 fixed and the controller's external release gates completed.**
