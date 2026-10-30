# Delhivery tracking and seller-remorse revision

## Scope and delivery

Approved 29 October 2026: compact quantity summaries, seller visibility only after the one-hour remorse period, seller parcel handoff and Delhivery staging tracking. Milestones were committed and reviewed separately, then deployed to beta after the compatible code passed its final gates. Main and production remain unchanged.

Delhivery token/test waybills are not yet configured. The carrier client remains disabled by default and manual admin delivery remains available. Tests with mocked provider transport are not live carrier acceptance.

## Milestone checkpoints

1. Compact summaries — complete, source`5342153d67fb1955850b4e2d006667ff3d0f492b`.101 frontend tests,15 CSS Browser cases, lint/build passed; scoped spec/quality review approved. Existing chunk advisory remains Minor. Screenshots are synthetic, not live beta.
2. Seller privacy and release — complete, source `afee9bcd6f0936f0d8f05233524609f2c21ff10f`. 84 focused backend tests, final 48-test workflow verification, 101 frontend tests, four affected CSS Browser cases, clean lint and successful build; scoped spec/quality review approved.
3. Shipments and Delhivery — complete and deployed to beta, source `5f988b5`, `f3cc425`, with review fixes in `9fb2ee0`. Release `v0.1.0-beta.6` uses tested source `c01308b3996282725885c0517a45cd892274e9f8`. Carrier access stays disabled until staging credentials are verified.

## Decisions and boundaries

- Use existing requested stg checkout, preserving README/local-properties edits. Cost: staging commits rather than separate isolation.
- Reuse Windows artifact helpers because bash helpers were previously blocked; no application behavior change.
- Three sequential implementers with scoped reviews; the final task review also evaluates whole-branch integration separately. Cost: broader last review instead of duplicate review hydration.
- Reuse the existing server release timestamp rather than creating another publication engine. Cancelled history may become visible only after release; fully cancelled held fulfillments create no new incoming task/mail.
- Seller incoming views revalidate on focus and every60s while visible, without exposing hidden-order counts/deadlines. Cost: a small scoped periodic read.
- No label/pickup/rate/return automation, new payment engine, live carrier mode or paid services. Existing0.5%/500-per-unit commission and60-day policy notice remain.

## Acceptance evidence

Baseline frontend: 95/95 passed. Baseline source `19fa53e` passed Application checks `40696571411`. Final milestone evidence and provider/Browser limitations are recorded below.

Milestone1: [evidence](review/delhivery/milestone-1-evidence.md), [review](review/delhivery/milestone-1-review.md), [cancelled390px](review/delhivery/compact-cancelled-390.png), [mixed320px](review/delhivery/compact-mixed-320.png).

Milestone2: [evidence](review/delhivery/milestone-2-evidence.md), [review](review/delhivery/milestone-2-review.md). Beta stayed on its previous release at that checkpoint; beta.6 now includes the reviewed visibility changes.

Milestone3: [evidence and review fixes](review/delhivery/milestone-3-evidence.md), [independent review](review/delhivery/final-review.md), [seller 320px](review/delhivery/parcel-seller-320.png), [customer desktop](review/delhivery/parcel-customer-desktop.png). Screenshots are synthetic fixtures. Final Browser suite: 21/21; frontend 101/101; clean lint and successful build. [Exact-source CI](https://github.com/saikat-ghosh-in/mercato/actions/runs/40915076985) passed all jobs, including 400 backend checks with zero failures/errors/skips and normalized PostgreSQL schema evidence. [Release checks](https://github.com/saikat-ghosh-in/mercato/actions/runs/40915545426) also passed.

Delhivery One account access was inspected on 29 October. Its explicitly labeled staging playground returned serviceability data for synthetic pincode 122003. The account's exportable API token control is labeled live, so it was not used or generated. Playground access does not prove Mercato's backend has a usable staging token or manifested test waybill. No wallet recharge, live booking, label or pickup request was made. See [staging setup](DELHIVERY_SETUP.md).

## Beta rollout, 29 October 2026

- Installed the checksum-verified beta.6 JAR using `~/redeploy-beta.sh`. Flyway 15 (`parcels`) succeeded; existing users/products remain 2/7. No historical parcel rows were invented.
- A private beta recovery dump was created and its table of contents/checksum verified before migration. This is an on-instance recovery aid, not the deferred off-instance backup gate.
- At rollout verification, the beta service was active with PID 3761086. Production's existing PID 1098123 stayed unchanged; no production service, database or `main` change was made.
- Netlify beta deploy [6ae37bd377a5a6ec555c2734](https://app.netlify.com/projects/mercato-in-beta/deploys/6ae37bd377a5a6ec555c2734) is ready/published from the same `c01308b` source, with signed rewrites, one Edge Function, successful plugin checks and no secret-scan matches. Auto publishing remains on; no publish lock was changed.
- Joined HTTPS checks passed: session/CSRF JSON, private no-store, Secure/HttpOnly/host-only/SameSite=Lax cookie, direct-origin 403, sandbox Cashfree form allowance and nosniff.
- Normal launchadmin MFA was completed by the user. Browser session Retry recovered after restart; authenticated admin order details show compact quantities and the Packages panel. Customer shipment reads allow the account's own test order and reject another customer's order with a not-found response despite the admin role.
- No live parcel was shipped or delivered as part of this read-only beta check. Handoff, delivery, races and notifications were tested with disposable PostgreSQL fixtures and mocked carrier/SMTP transport. Actual carrier acceptance still needs a verified staging token and manifested test waybill. Physical mobile hardware was not tested.

## Final implementation decisions

- Continue inline after the delegated runner reached its usage limit, preserving its draft; cost: final fixes were authored and verified inline after independent review.
- Use the dedicated scoped shipment endpoint rather than duplicate tracking into every order DTO; cost: one additional scoped read per open detail, avoiding order-list N+1 loads.
- Review all changes in this task from `19fa53e`, retaining prior hardening reviews; cost: unrelated earlier hardening was not reviewed again.
- Revalidate open tracking panels on focus and every visible 30 seconds; cost: bounded Mercato database reads, not a Delhivery API call per UI refresh. Existing carrier polling/backoff governs external calls.
- Preserve legacy delivery through explicit server-computed untracked maxima and persist carrier retry deadlines; cost: a small DTO extension and one metadata column, with regression coverage.

The original independent review's two Important findings and asynchronous-refresh observation were resolved in one test-driven fix pass. CI on the corrected source is green. Existing bundle-size advice, real-device verification, live carrier credentials and previously deferred production/payment/returns readiness remain disclosed limitations.
