# Fulfillment revision validation evidence

## Current beta scope — 27 October 2026

The latest repeated user direction prioritizes a working side project. Acceptance now covers ordinary sandbox checkout, quantity-based seller actions, accurate fees/refunds/commission, existing admin shipping plus a delivery command, and usable desktop/mobile screens. Implementation and integrated validation are still in progress; the historical results below are tied to their recorded commits.

Delayed authorization/capture activation remains **off**. New parcel tracking and in-app customer-return systems, advanced RTO, condition deductions, seller cost disputes/reserves, and enterprise operations are deferred. Existing internal refund hooks are not an exposed returns feature. Older checklist entries for those features are backlog, not prerequisites for this sandbox beta. Policies and screens must describe only the supported flow. Nothing here authorizes production or real-money launch.

## Checkout and money foundation candidate

Commit `1f382affe471f6d2bb508bc5d8fab6065720898d` adds migration V11, immutable per-unit money snapshots, selected-price provenance, explicit cart quote revisions and buyer-scoped checkout retry identities. It preserves newly re-added bag selections when an earlier order allocates, uses deterministic cart/product locking and bounds checkout to 10,000 units without deleting larger saved bags.

Focused verification passed61 tests/10 suites. The backend/PostgreSQL run on that foundation passed328 tests/63 suites with0 failures/errors/skips. These tests reproduce duplicate checkout identities, stale quote conflicts, allocation loss with zero weights, bag-edit races, provenance and fee/commission conservation.

Review then found a retry-restored hold could fail transfer and reserve stock twice after payment. Fix `86393016b2c8a8fc53688d0c04185f96d08b0ac2` adds V12 with explicit retry selection bindings, preserves immutable original lineage and rejects unavailable independent stock before gateway retry. Three new regressions reproduced the defect; the affected PostgreSQL/unit/migration gate passed43 tests/6 suites with0 failures/errors/skips. Scoped re-review cleared the finding with no new blocking issues. The reviewed foundation and fix are pushed to `stg`; [Application checks40468355747](https://github.com/saikat-ghosh-in/mercato/actions/runs/40468355747) passed for that exact head, including the complete PostgreSQL/backend, frontend and Linux operations gates. The complete suite will run again after integration. External gateway calls were mocked; this is not live Cashfree proof. Refund/report integration, server-side reload recovery and the new frontend contract are required before deployment.

The pre-upgrade read-only OCI check found beta active on V8 with2 users and7 products. Production remained active under its existing process. No database reset or configuration mutation was performed.

## Implementation decisions recorded during execution

These decisions are recorded in execution order so they remain reviewable after the temporary implementation workspace is removed.

1. Reuse the requested existing `stg` checkout and preserve the README/local configuration edits. Cost if wrong: reversible staging commits rather than an isolated checkout.
2. Generate the temporary task/review packages with PowerShell because Git Bash was blocked by the local sandbox. This changes no application behavior.
3. Apply new commercial rules prospectively; keep ambiguous legacy funding, quantities and delivery history reviewable. Cost: some old test orders need review instead of automatic adoption.
4. Allow audited administrator adoption only with funding, allocation, reservation and quantity evidence. Preserve original confirmation/release dates and start its prospective acceptance deadline from the later release/adoption time. Cost: conservative delay for old records.
5. Split the financial implementation into independently reviewed reader, attribution and settlement units, with no intermediate backend deployment. Cost: additional review coordination. The later side-project scope decision defers settlement activation.
6. Preserve verified partial capture evidence but halt whole-order dispatch without sufficient attributed funds. Cost: operator review rather than underfunded shipment.
7. Keep legacy `capturedAt` as an observation anchor; record first verification and nullable provider capture time separately. Cost: additional provenance fields; no invented bank dates.
8. Retain attempt-local proof when authorization ownership is ambiguous. Matching authorization IDs alone cannot promote one attempt into another. Cost: conservative review; delayed authorization remains disabled.
9. Split monetary foundations from refund/report integration. Require checkout operation identity and cart quote revision, with the compatible frontend delivered before deployment. Cost: a coordinated client/backend contract update.
10. Limit checkout to10,000 total units, validate with checked arithmetic before side effects and preserve larger editable bags. Use an explicit cart revision and finish replay lookup before locking an existing order. Cost: a clear checkout limit and extra transaction boundary.
11. Follow the user's side-project direction: finish ordinary sandbox commerce, basic manual shipping/returns and accurate money; defer delayed settlement, advanced RTO, deductions, disputes and enterprise operations. Cost: those optional features remain backlog.
12. Recover uncertain checkout server-side using a buyer-owned operation-ID lookup. Persist only the opaque ID in the browser. Cost: one small read endpoint instead of private browser persistence.
13. Bind explicitly restored retry selections separately from immutable original quote lineage. Preserve unrelated bag selections and check independent stock before payment retry. Cost: two nullable binding fields and migration V12.
14. On the user's repeated instruction to stop overengineering, defer even the previously proposed parcel/customer-return systems. Finish the existing admin shipping/delivery actions and current-screen integration instead. Cost: in-app returns and parcel tracking stay backlog; current payment/refund fixes remain.
15. Combine the small admin-action and frontend-wiring changes into one implementation/review unit with separate backend/frontend commits. Cost: a slightly broader integration diff, with fewer handoffs and no additional subsystem.

## Refund and shared-finance core

Commit `aae29866eb5208b83d9b37d8bbf98e69cc512406` adds V13 unit-state/refund attribution, cause-specific buyer-charge refunds, bounded commission reversals and a shared calculation for finance, monthly reports, promotion budgets and dashboard earnings. Checkout-intent recovery is buyer-owned, private/no-store and performs no gateway call on GET. Only an opaque retry ID needs browser persistence.

The exact pre-fix source passed 354 tests across 67 suites, including 71 focused evidence/finance tests. Independent review found two bounded defects: final cancellation after an earlier return could retain residual buyer fees; full refunds could erase known legacy seller delivery costs. Fix `3e14fad536e28d253f62c7836bca16552110c030` changes those two branches. The affected 25 tests across three suites passed after reproducing both defects, and scoped re-review cleared both with no new blocking issues. [Application checks40537296487](https://github.com/saikat-ghosh-in/mercato/actions/runs/40537296487) passed for that exact pushed staging head, including the complete PostgreSQL/backend, frontend and Linux checks.

Actual collections require current verification evidence. Proven partial or halted primary money remains visible as collections but cannot earn or fund dispatch. Legacy observation timestamps alone are not proof. Internal received-unit refund hooks are tested but remain unexposed; no customer-return system is claimed. Existing generic shipping still needs the explicitly assigned current-proof guard in the final wiring increment, and the frontend must consume the new checkout contract and correct earnings labels before deployment. Gateway/SMTP boundaries were mocked in these local checks.

## Final core-wiring candidate

Backend `331308b` adds V14 and an admin-only delivery action, using the existing operation/version command path. Shipping and delivery now require current verified capture evidence as well as funding/allocation. Shipment settles stock once; delivery only records physical progress and its audit event. No parcel or returns model is added.

Frontend `1992c61` wires checkout intent/reload recovery, guarded admin quantity actions, accurate finance labels and the seller-release countdown correction. A retry preserves the original checkout decision; only its opaque ID goes in the URL. The customer can see inline recovery errors without a duplicate order or a flashing payment action. Unsupported return/automation promises are removed.

Final backend evidence: 360 tests across 67 suites, no failures/errors/skips. Final frontend: 95 tests, lint and production build passed. The isolated CSS Browser suite passed 18 cases; a mobile screenshot check then caught duplicate visible payment controls, reproduced the defect, and the final affected checkout rerun passed all four cases after the layout fix. The unchanged remaining cases retain their previous pass evidence. Existing bundle-size and test-framework warnings remain documented.

These are synthetic component/route fixtures with compiled CSS, not live beta or hardware-phone proof. Both320/390px widths, retry/recovery states, privacy fences, command maxima, keyboard and reduced motion are covered. Inspected screenshots: [Checkout320](./review/fulfillment/core-checkout-320.png), [Admin390](./review/fulfillment/core-admin-390.png), [Finance320](./review/fulfillment/core-finance-320.png). Independent final review, CI and beta integration are pending.

## Accordion increment — 26 October 2026

Code commit: `f34a170c84aedc4d51c17f2e1f7bc846596cf8d2`.

- Full frontend suite: 84/84 passed; lint passed; production build passed. Existing >500kB chunk advisory remains.
- Isolated Chrome tests: 2/2 passed, covering controlled/single/multiple behavior, keyboard navigation, focus restoration, inert closure, delayed hiding/interruption, reduced motion and FilterPanel draft retention/Apply.
- In-app Browser with production CSS on local Vite: Help opens multiple sections, Home moves focus to first trigger, Enter closes a section, closed content disappears from accessibility tree. Trigger measured44px.
- Desktop document width1265px and390px viewport/mobile document375px both showed equal scroll/client widths (no horizontal overflow). Mobile long labels wrap inside the trigger with right chevrons.
- Mobile filter drawer: entered minprice100, collapsed/reopened Price range, retained100; Apply closed drawer, updated URL atomically and focused filter button. Actual selectors remain custom selectors.
- Screenshots: [Desktop Help](./review/fulfillment/accordion-desktop.png), [Mobile Help](./review/fulfillment/accordion-mobile.png), [Mobile filters](./review/fulfillment/accordion-filter-mobile.png).

Environment limit: these initial visual checks used the pre-existing disposable H2 demo backend with payment services/jobs disabled. They verify the accordion UI, not new fulfillment or payment behavior. Integrated beta and finance/return journeys remain pending.

## Deployed accordion verification

The reviewed UI increment was pushed to `stg` and Netlify beta published exact commit `f34a170c84aedc4d51c17f2e1f7bc846596cf8d2`, deploy `6adef64f49b054d4d52baabc` (`ready`). Backend remains `v0.1.0-beta.4`; unfinished fulfillment changes were not included.

In-app Browser on beta Help: default first section open, second opens independently, Home focuses the first trigger and Space closes it while retaining focus. At360px viewport, document scrollWidth/clientWidth both345px. The saved screenshots were visually inspected; no horizontal overflow. Temporary viewport was reset.

[Beta desktop](./review/fulfillment/accordion-beta-desktop.png) · [Beta mobile](./review/fulfillment/accordion-beta-mobile.png).

GitHub [Application checks40281392799](https://github.com/saikat-ghosh-in/mercato/actions/runs/40281392799) completed successfully for this exact accordion commit, including the PostgreSQL and Linux operations gates.

Deployed PDP check: JBL Clip5 real catalog product retained its default-open specifications while Care instructions opened independently. At360px viewport, long specification labels/values wrapped without overflow (scroll/client width345px). Header remained visible while the disclosure scrolled into view. [Mobile product disclosure](./review/fulfillment/accordion-beta-product-mobile.png). No cart or payment mutation was performed.

## Quantity workflow backend candidate

Reviewed commits `8e8d06b` and `a1dd04a` are on `stg`; the backend has **not** been installed on beta. The frontend adapter and financial/logistics increments must be completed before that rollout.

Final local PostgreSQL/backend evidence:260 tests,0 failures/errors/skips;54 targeted workflow tests. Coverage includes partial-cancel acceptance, cumulative quantity bounds, ownership and dual-role customer mode, exact hold/SLA boundaries, command replay/version conflicts, cancel-versus-preparation concurrency, adoption/backfill, seller metrics, future jobs and stale-notice delivery. Review fixes also cover absent/oversized/undersized reservations with atomic rollback and truthful skipped mail activity. No Cashfree authorization proof is implied.

GitHub [Application checks40296537407](https://github.com/saikat-ghosh-in/mercato/actions/runs/40296537407) passed for exact commit `a1dd04a0f3ce3f9ba020feb0d8929e0de9301538`, including PostgreSQL, frontend and Linux operations checks. This is code verification; the beta service still runs the prior release.

Two nonblocking review notes remain for the whole-branch pass: expected local test warnings, and readability of dense new service formatting.

## Payment contract evidence

Read-only checks against beta's configured Cashfree sandbox API `2025-01-01` found one successful card attempt with explicit `is_captured: true` and no authorization object. Separately, the stored `PAYMENT_SUCCESS_WEBHOOK` passed its raw-body HMAC check but omitted both `is_captured` and authorization metadata. Its `auth_id` alone cannot establish a hold or capture. The financial reader must reconcile insufficient webhook evidence against the authenticated provider API.

No account setting, payment, authorization, capture or void was changed by these checks. No raw payload, signature, key, customer detail or payment value was recorded. These observations establish ordinary immediate-payment field shapes; delayed capture remains an external acceptance gate.

## Workflow UI candidate

Commit `4592d9a1afba7c4d9331df71b25985affd2a41b6` adds backend-permission-based seller acceptance, preparation, packing and cancellation; customer unprepared-unit cancellation; versioned command retries; retained refreshes and policy disclosures. Review found an initial admin-detail failure could leave a blank view. Fix `bae8b4b24e9aad16120e72a43929acf6525d6df5` adds visible error, Retry and Back; its two focused admin browser cases passed after reproducing the failure. Scoped re-review passed with no new issues. These commits are pushed to `stg` for CI, with beta publishing still held at the compatible earlier frontend.

Final local checks:91 frontend tests and9 isolated Chrome scenarios passed; lint and production build passed. Browser coverage includes quantity bounds, exact retry identity, stale-version drafts, customer mode for dual-role accounts, whole-cancellation eligibility, session/record response fencing, hold/deadline refreshes, list back-navigation, admin compatibility and checkout/Help disclosures. Existing build chunk-size advisory remains.

A minor display-clock issue remains scheduled for the final UI increment: the hold label can retain its last tick after the release-boundary refresh. It does not change backend permissions; the immutable-timestamp regression and label correction remain required before final acceptance.

GitHub [Application checks40331269685](https://github.com/saikat-ghosh-in/mercato/actions/runs/40331269685) passed for exact reviewed head `bae8b4b24e9aad16120e72a43929acf6525d6df5`, including the full frontend/backend PostgreSQL and Linux operations jobs. The beta frontend remains at its explicitly held earlier revision; CI success is not a deployment claim.

These are isolated components using compiled production CSS and an offline sans-serif fallback, with synthetic scoped API responses and a tan fixture product image. They do not prove the integrated dashboard shell, real account sessions or payment/backend transitions. Screenshots: [Seller desktop](./review/fulfillment/workflow-seller-desktop.png), [Seller mobile](./review/fulfillment/workflow-seller-mobile.png), [Customer retained invalid draft](./review/fulfillment/workflow-customer-invalid-draft-mobile.png). The customer capture intentionally retains quantity2 after the server maximum falls to1, with submission disabled; it is validation evidence, not a successful cancellation capture.

## Integrated acceptance checklist — pending

These are release checks, not claims of completed validation. Update each with the exact environment, commit and evidence when exercised.

For the coordinated beta rollout, the user approved temporarily locking auto publishing on **mercato-in-beta**. The dashboard confirms the currently published `a1dd04a` is locked; new builds and CI continue. Restore auto publishing only after the compatible backend is installed, then verify the exact new frontend revision. [Publishing hold evidence](./review/fulfillment/beta-publishing-held.png). Production publishing settings were not changed.

- [ ] Seller acceptance after customer partial cancellation; repeated quantity acceptance and cancellation; full cancellation; valid remaining actions after mixed progress.
- [ ] Seller grace countdown, exact release/acceptance boundaries, durable reminders and timeout recovery after downtime. Exercise time boundaries locally with the injected Clock.
- [ ] Customer cancellation restricted to unprepared quantities; dual-role customers use customer permissions; changed versions refresh facts without losing entered reasons or quantities.
- [ ] Retained seller/customer content during refresh, identical uncertain-operation retry, account/selection response fencing and refreshed lists after returning from detail.
- [ ] Ordinary immediate sandbox capture; insufficient webhook reconciliation; durable captured funds despite allocation rollback; uncertain evidence visibly held for review. Authorization activation remains disabled.
- [ ] Checkout intent retry reuses the same order; stale quote refresh retains entered address; newly re-added cart items survive previous-order allocation.
- [ ] Integer-paise allocations, cause-specific refunds, residual buyer charges, bounded commission reversals and matching finance/monthly/promotion figures. Holds excluded from collections.
- [ ] Existing admin shipping and delivery commands; current capture proof before physical handoff; once-only stock settlement; partial quantities and delivery audit.
- [ ] No unsupported parcel/return buttons or policy promises. Internal financial return-hook tests are not a live customer-return feature.
- [ ] Customer/seller notification scope and truthful partial-event wording; usable order and finance visibility; ambiguous legacy records stay reviewable.
- [ ] Desktop and 320–390px mobile journeys, keyboard access, retained disclosure forms, reduced motion and horizontal overflow.
- [ ] Final PostgreSQL suite, frontend tests/lint/build, Linux CI, focused independent reviews and whole-branch review.
- [ ] New immutable beta prerelease assets, additive migrations, existing MFA/data preserved, exact Netlify commit, beta service checks and unchanged production/main.

Enabled-account sandbox proof is required before exposing delayed capture: verified authorization notification/reconciliation, partial capture, full void, expiry, unsupported-card routing and emergency behavior. Synthetic tests alone do not satisfy that gate. Tax collection, invoices/credit notes, carrier contracts and payout readiness remain gates before real-money launch.

## Reviewed payment reader

Commit `eee9f4209ebe00484c289d90b3d080781772fac1` adds the additive `V10` evidence migration, authenticated reconciliation, typed capture/authorization facts, safe new-order policy defaults and durable financial halts. The full local PostgreSQL/backend suite passed291 tests across59 suites with0 failures/errors/skips. The reader and boundary regressions cover missing webhook fields, source spoofing, strict flags/identity/amount checks, aliases, timestamp provenance, legacy unknowns, null-safe funding and retained capture facts.

Independent review found two blocking merge cases not covered by that initial run: an omitted authorization ID could falsely halt a proven capture, and authorization metadata could cross provider-attempt boundaries. Fix `f7ebe571c1aebff7ce9dd20c005dd928b7758870` preserves omitted facts, separates attempt proof from primary-payment promotion, and retains ambiguous ownership for review. Six new real-PostgreSQL sequences reproduced those defects before the fix. Focused52 and full297 tests/59 suites passed with0 failures/errors/skips on the final source. Scoped re-review cleared both findings with no new blocking issues. Both reader commits are pushed to `stg` for CI; no beta backend installation has occurred.

`capturedAt` remains a compatibility observation anchor. `captureVerifiedAt` records current verification, and nullable `providerCapturedAt` records only a qualified provider date with explicit provenance. Unknown provider dates are not replaced by payment initiation time. Legacy policies stay unknown; new records default to `CAPTURE_ON_AUTH`. Authorization allocation and settlement activation remain disabled. Partial capture requires the next unit-attribution increment before fulfillment; release evidence still requires the later financial state integration. No beta installation or enabled-account proof is claimed for this candidate.

GitHub [Application checks40377258704](https://github.com/saikat-ghosh-in/mercato/actions/runs/40377258704) passed for exact reviewed reader head `f7ebe571c1aebff7ce9dd20c005dd928b7758870`, including PostgreSQL, frontend, Linux operations and advisory checks. The approved beta publishing hold remains active; the OCI beta backend still awaits the coordinated complete revision.
