# Mercato compact orders and Delhivery tracking implementation plan

> For agentic workers: use superpowers:subagent-driven-development or executing-plans; maintain milestone checkpoints.

**Goal:** Deliver the user-approved 29 October plan: compact quantity summaries, private one-hour seller remorse period, seller handoff and Delhivery staging tracking with manual delivery fallback.
**Architecture:** Reuse current quantity/funding guards, operation/version idempotency, PostgreSQL/Flyway, durable jobs, cookie/CSRF/session fencing and shared UI. Add only parcel records/items and one Delhivery client/job handler. No new workflow/payment/job engine.
**Stack:** Java17/Spring/PostgreSQL; React19/Vite/Redux and existing CSS/component/browser harnesses.
**Spec:** The approved complete plan in this chat, including customer ownership, configurable verified50-waybill batches, and sellers unable to read held orders.

## Global constraints
- Work in existing local stg; preserve README.md and backend/src/main/resources/application-local.properties unstaged. Never push main or touch production. Focused commits/checkpoints, reviewed stg push and beta deployment authorized.
- Side project: small direct implementation, no broad redesign/refactor/dependencies. Cashfree sandbox only; delayed capture OFF. Preserve commerce pricing, seller-paid delivery,0.5% capped500/unit commission and60-day change safeguard.
- Delhivery staging ONLY https://staging-express.delhivery.com; token backend-only Authorization header. User has no staging access yet: disabled by default, explicit setup status/admin manual delivery; do not fabricate live integration proof or fallback to production/mocked product data.
- Carrier/network/SMTP calls outside database transactions/locks. Reuse leases/fencing/dedup/retry, server-derived ownership, immutable checkout/pricing, protected mutations.
- No label/pickup booking, courier rate calculation, returns/RTO financial automation, bank payouts or external-service purchases.

## Milestone1 — compact summaries
- [x] Replace shared seven-counter grid with Qty N and state. Customers map Confirmed/Accepted/Preparing/Packed to Processing, Awaiting funding to Awaiting payment. Seller/admin retain meaningful operational stage labels/actions.
- [x] Single-state line: Qty1 · Cancelled/Delivered/etc, without duplicate counts. Mixed states show only positive disjoint buckets: processing=ordered-cancelled-shipped(after confirmation), shipped=shipped-delivered, delivered, cancelled. Unconfirmed active units are Awaiting payment, never Processing. Header totals and line rows avoid duplicate raw grids. Unknown legacy counts do not invent progression.
- [x] Pure behavior regressions, actual shared component CSS Browser at320/390 and desktop, frontend tests/lint/build; focused UI-only commit/checkpoint.

## Milestone2 — seller privacy during remorse
- [x] One authoritative visibility rule using existing server releaseAt/Clock, confirmedAt+1h (legacy effective release preserved); no new publication table or second waiting period.
- [x] Filter seller list/details/search, dashboard counts, finance/order-report/export and order notifications before projection/aggregation/pagination. Held/unconfirmed own IDs404; foreign IDs404. Customer/admin scopes unchanged. At exact release seller can see current survivors and act immediately; later capture/refresh does not restart hold.
- [x] Explicit seller command route/context (including dual admin/seller) and existing mutation guards. Reuse RELEASE mail job; fully cancelled held fulfillment creates no incoming-work task/notification, but cancelled historical views may show it only after release. Keep48h acceptance from release and current soft preparation-review policy.
- [x] Real PostgreSQL read/aggregate/detail/dual-role/time-boundary and cancellation regressions, targeted finance/mail/permission tests, frontend endpoint adaptation if needed; focused commit/checkpoint.

## Milestone3 — shipments and Delhivery
- [ ] Add minimal shipment + shipment-item persistence (next additive Flyway), scope one seller fulfillment/parcel to exact line quantities; permit partial/multiple parcels. Track waybill/status/provider timestamps, last check/error and sanitized history; no invented legacy shipments/dates.
- [ ] Seller Mark shipped form selects packed quantities, enters waybill and confirms actual handoff. New command creates parcel and performs quantity/financial/stock SHIP atomically, operationId+expectedVersion; no bare seller SHIP bypass. Admin same guarded path/manual legacy controls retained. No extra stock effect on DELIVER.
- [ ] GET /api/user/orders/{orderId}/shipments uses ONE authoritative customer order-ownership check (404 foreign; dual-role no admin bypass on customer route), then returns only that order's parcels. Customer refresh uses same check. Seller reads/refresh require own fulfillment AND released visibility. Admin is separately scoped.
- [ ] Shipments appear in customer/seller/admin details; Track package panel has waybill, normalized status, ETA if known, last update and sanitized events. Private no-store responses, session/record fences and retained content on refresh/failure.
- [ ] Backend-only staging Delhivery pull client; DELHIVERY_ENABLED=false without token. DELHIVERY_TRACKING_BATCH_SIZE=50 validated1..50, configurable; poll active parcels15min, deduplicate AWBs and chunk; manual60s cooldown. Explicit provider batch-size rejection retries singly and warns, not auth/other arbitrary400; missing results retained/retried. Honor429/backoff. Do not claim production quotas apply to staging.
- [ ] Verified forward-delivery+matchingAWB updates only parcel-linked shipped quantities through existing delivery service/commands exactly once. Duplicate/out-of-order responses don't regress/duplicate; RTO/reverse/unknown/delayed statuses never become delivered or automatic refunds. Stop normal polling after terminal delivery. Network I/O outside locks, reject stale/corrected tracking generations when applying responses.
- [ ] Admin correct tracking and manual Mark delivered requires reason/op/version audit. Use only current remaining shipped quantities; no duplicate mail/stock/delivery. Per-parcel shipment/delivery notices through existing mail jobs; no premature whole-order completion announcement.
- [ ] Provider-mocked client/status/response/locking/ownership tests + realPG shipment/cancel/version/race/partial/delivery invariants; frontend real component/browser tests and final full PostgreSQL suite/frontend/lint/build. Independent final task+whole-branch review before rollout.

## Review focus
Cross-account shipment access; held-order aggregate/deep-link leaks; partial cancellation versus ship quantity race; duplicate/corrected parcel tracking versus delivery; provider stage/identity mismatch. No external API or destructive tests from workers.

## Delivery
Three checkpoint milestones, no permission pauses for already approved work. Full CI on exact final code, new immutable beta prerelease; beta-only backup and additive upgrade, coordinated matching Netlify publish (temporarily hold only if needed/authorized then restore). Current source19fa53e, installedbeta5/V14; main441b35a unchanged. No Delhivery token: report actual-carrier acceptance blocked on credential setup, keep working manual path, no invented success. Update DEPLOY and review notes/screenshots with actual evidence and scope limits.
