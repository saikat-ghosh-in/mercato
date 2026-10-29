# Final independent review — compact orders, seller privacy and parcels

Reviewed supplied current-task range `19fa53eee7cab3066a58667a5344a32c045ae720..f3cc4254b46ebd5dade4406274aa6893aefda3d0` on local stg. Earlier hardening is outside this review. Read the complete supplied diff package in bounded sections; explicitly recovered portions truncated by tool output. Read the plan, Task 3 binding brief and report, ledger rulings, and previous Task 2 review. Source/index/HEAD remain unchanged; this report is the only write. No tests, external calls, browser sessions, or subagents were run.

## Strengths

- Shipment reads derive customer identity server-side and perform the authoritative parent query before reading parcels. The stable customer ID takes precedence over email; legacy email fallback applies only without that ID (`OrderRepository.java:53`, `ShipmentService.java:50`). Explicit seller routes retain seller context for dual-role accounts and check ownership plus canonical release before projection or refresh (`ShipmentService.java:57`). Customer routes do not inherit admin privileges.
- Handoff uses the existing order lock, current workflow/funding guards, line transitions and reservation settlement. Parcel rows, quantities, inventory, commands and notification enqueue commit together. Products are locked in stable order, and changed payload/stale revision checks run before mutation. The cancellation race therefore rechecks current quantities under the same aggregate lock.
- Tracking HTTP is explicitly outside transactions. The adapter has one fixed staging URL, header-only credentials, bounded/deduplicated batches and exact string AWB matching. Ambiguous duplicate identities are excluded. Apply rechecks AWB, generation and terminal status under the order lock; correction cannot rewrite delivered history. Forward `Delivered` + `DL`, valid post-handoff timestamps and current delivery eligibility are required before quantity delivery. Reverse/unknown/future records cannot turn into delivery or money changes.
- Per-parcel shipment/delivery mail uses existing branded AUTH_EMAIL jobs and exact quantities; the new path suppresses old whole-order shipping mail. Delivery does not settle stock again. No booking, payment/return engine, dependency, production endpoint or new queue has been introduced.
- The shared panel retains uncertainty drafts/operation identity, refreshes facts on conflicts and fences account/record changes. Compact quantity summaries remain intact and seller visibility is applied before backend aggregates. Dedicated scoped tracking reads are a sound minimal alternative to duplicating shipment DTOs into every existing detail/list response: the three detail UIs display the data, scope remains explicit and lazy/N+1 enrichment is avoided. This is an accepted ledger deviation, not a blocker.

## Issues

### Critical (Must Fix)

None found.

### Important (Should Fix)

1. **[P2] Preserve delivery for legacy units when the same line gains a parcel, and protect parcel quantities from generic delivery.**
   - Primary reference: `frontend/src/components/admin/OrdersTab.jsx:131`. Related integration seam: `backend/src/main/java/com/mercato/Service/OrderLineUpdateServiceImpl.java:52` and `:64`; parcel delivery at `backend/src/main/java/com/mercato/Service/ShipmentService.java:136`.
   - The new filter removes the generic Mark delivered action for an entire line as soon as *any* parcel references that line, including an already delivered parcel. Valid upgrade case: a packed line already has two legacy shipped, undelivered units; after this release another three units ship in a parcel. The admin can deliver the parcel's three units, but the legacy two never regain a UI delivery action. They have no parcel action because the additive migration correctly creates no fabricated parcels. The order can remain permanently short of delivered through supported UI actions.
   - Conversely, the still-supported generic admin command has no parcel reservation check: its DELIVER maximum remains all `shippedQty - deliveredQty`. A direct/stale legacy action can consume quantities belonging to parcels and leave those parcels unable to apply their full quantities later. Hiding a button cannot make that existing endpoint safe.
   - Fix with a small shared server calculation for the undelivered legacy remainder (exclude quantities reserved by non-delivered parcels), enforce that bound for non-parcel DELIVER, and expose/use it for the existing legacy admin control. Keep parcel delivery responsible for its own exact items. Add a regression with legacy and parcel shipments on the same line, including the parcel-first delivery order and rejection of generic delivery of reserved quantities. No backfill or new workflow engine is needed.

2. **[P2] Prevent fresh refresh jobs from bypassing Retry-After/backoff for the same parcel.**
   - Primary reference: `backend/src/main/java/com/mercato/Service/ShipmentService.java:151`–`:158`; failure handling at `:168`. The client correctly raises WaitingJobException with Retry-After, but the existing processor only delays that particular job.
   - `enqueue` checks only the time of the previous enqueue, then creates a new random business key. After a provider response such as `429 Retry-After: 3600`, manual refresh becomes eligible after 60 seconds and the scheduled scan after 900 seconds. Either creates a fresh job that calls the provider before the required hour expires, while the original job is still waiting. Generic transient backoff can be bypassed in the same way. The existing client unit assertion proves parsing, but does not exercise enqueue plus worker integration.
   - Retain a per-parcel next permitted attempt or reuse/coalesce pending durable tracking work so manual and scheduled requests honor the outstanding provider delay. Keep this on the existing queue. Add a mocked 429 service/job regression that advances past 60/900 seconds but remains before Retry-After, proving no second outbound request, then proves retry is possible after the deadline.

### Minor (Nice to Have)

- `frontend/src/components/orders/ShipmentPanel.jsx:40`: after queuing an asynchronous refresh, the immediate GET can finish before the worker. There is no subsequent in-panel revalidation when that worker completes or the 60-second cooldown elapses, so an open customer view can continue showing old tracking until navigation/reload. A lightweight visible/focus or bounded follow-up read would make the existing refresh action clearer; this does not require realtime infrastructure and is not a rollout blocker.
- Previously disclosed frontend bundle advisory and PostgreSQL/test configuration warnings remain nonblocking. They are not reasons to expand this side-project change.

## Required focus assessment

- **Cross-account shipment access:** No blocker found. Stable-ID parent guard, foreign 404, role-specific routes and scoped seller projection are present. Customer refresh uses the same parent guard, then the order lock for mutation.
- **Held-order aggregate/deep-link leaks:** No new leak found. The new seller tracking read/create/refresh calls canonical requireVisible before shipment output/metadata. Existing Milestone 2 aggregate/notification behavior agrees with its prior approved review; it was not broadly re-reviewed.
- **Partial cancellation versus ship quantity race:** No correctness blocker found. Order-first serialization and workflow/version validation prevent cancelled or already shipped quantities from entering a new parcel. The test supplies an actual held order lock for shipment versus cancellation, although only the shipment-wins outcome is new here.
- **Duplicate/corrected parcel tracking versus delivery:** AWB/generation/terminal checks and stable per-parcel command identity handle replay/correction. Important issue 1 concerns the remaining interaction with the legacy admin delivery path.
- **Provider stage/identity mismatch:** Fixed staging endpoint, exact AWB matching, explicit forward Delivered/DL, reverse checks and conservative timestamp validation are present. Important issue 2 concerns coordinating the provider's retry policy with new work enqueueing.

## Checks and evidence

- Inspected saved final focused Surefire XML: PostgresQuantityWorkflowTest 56 and DelhiveryClientTest 6, total 62; zero failures/errors/skips. Saved final frontend log reports 101 passing, zero failing/skipped; build succeeds with disclosed advisory; lint log has no diagnostics. Saved final actual-CSS Browser log reports 20 passing, zero failing/cancelled/skipped; includes 320/390/1440 and reduced-motion coverage.
- Full 397-test backend success is supplied in the implementer report and predates the final stable-ID ownership fix. This review does not relabel it an exact-final-source full run. Root's full CI on the final corrected source remains a required gate.
- Named focused dependency check: unchanged FulfillmentWorkflow `:62`–`:81` confirms generic admin DELIVER uses the entire remaining line quantity, while SHIP/DELIVER preserve current capture/funding/allocation/halt guards and generic seller SHIP remains blocked.
- Named focused dependency check: unchanged SpringSecurityConfig `:14`–`:20` protects admin/seller route prefixes and leaves new mutations under existing CSRF enforcement. AuthUtil `:24` confirms generic dual-role actor resolution, while the new explicit seller route avoids it.
- Named focused dependency check: unchanged useWorkflowResource `:15`–`:29` preserves same-scope data and fences superseded/account/record reads. New panel mutation fences were read in the diff.
- Named focused dependency check: existing DurableJobs/JobWorker/JobProcessor reuse the durable enqueue, leases and job-specific wait/backoff machinery; JobProcessor's WaitingJobException branch honors the supplied duration, confirming issue 2 lies in new enqueue coordination rather than duration parsing.
- Named focused dependency check: existing ApiHeadersFilter `:7` supplies API and CDN no-store headers, so the new controller does not create a cache leak.
- No live carrier acceptance, SMTP delivery, real-device mobile coverage, beta upgrade, push, deployment, main change or production action is claimed by this review. Credentials are unavailable; disabled carrier with manual delivery is the intended supported fallback.

## Declined to judge

- Live Delhivery account ownership/manifested-AWB acceptance and provider staging quota behavior: no staging credentials or valid account AWBs, and external calls are explicitly excluded; mocked transport is not live acceptance.
- Booking, label/pickup/manifestation, rate shopping, return/RTO financial automation, bank payouts and new worker infrastructure: explicitly excluded from the approved scope.
- Prior hardening outside the supplied base, unrelated legacy identity/email behavior and a broader auth/payment redesign: outside this current-task review; the shipment ownership guard itself was reviewed.
- Exact final CI, beta backup/additive migration deployment and matching Netlify publication: root-owned release gates not yet performed in this review; approval cannot substitute for those results.
- Broad performance/bundle refactoring and physical mobile hardware acceptance: no concrete blocker shown and outside the minimal integration; the existing warning and hardware limit remain disclosed.

## Assessment

**Task 3 spec verdict: With fixes.** The minimal architecture and the dedicated endpoint deviation are acceptable, but preservation of legacy manual delivery and shared provider retry behavior are incomplete.

**Task 3 quality verdict: With fixes.** The core ownership, locking, shipment generation and funding integration are strong. Fix the two bounded correctness issues and add their focused regressions before release.

**Whole current-branch integration verdict: With fixes; not ready for rollout.** Compact summaries and seller remorse privacy integrate coherently with the parcel path. Resolve the legacy/parcel delivery boundary and retry scheduling gaps, then run the root-owned exact-source full CI and the affected frontend gate. No broader redesign is requested.
