# Parcel handoff and Delhivery staging

Backend source `5f988b5`; UI source `f3cc425`. Tests use synthetic data and mocked carrier transport. No live Delhivery token or same-account manifested test waybill is configured.

## What works

Seller/admin handoff records a parcel and ships its exact packed quantities atomically. A tracking number alone cannot ship an item. Customer shipment reads check the parent order's ownership once, using the stable account ID where recorded. Seller reads/actions also require the effective one-hour release. Admin can correct tracking before delivery or record manual delivery with an audit reason. Delivery does not deduct stock a second time.

The staging-only tracking adapter batches up to the configured limit (default 50), matches exact waybill strings, keeps existing state on failures, and applies verified forward delivery through existing quantity/funding checks. Correction generations reject an old response already in flight. Reverse/RTO, unknown or invalid-date responses cannot announce customer delivery. Tracking HTTP runs outside database transactions; existing durable jobs provide retries. Carrier access is disabled by default and manual delivery remains available.

## Verification

- The full backend run passed **397 tests**, with zero failures, errors or skips, including real PostgreSQL migration and locking tests.
- A final security regression then reproduced an email-reassignment ownership issue. The parent query was changed to stable account identity with legacy email fallback only when no ID exists. **62 focused workflow/client checks passed** on that final backend change. The final exact commit must also pass full GitHub CI before release.
- Frontend: **101 tests passed**, lint passed without warnings, production build passed. The existing bundle-size advisory remains.
- Actual-CSS Browser: **20/20 passed**, including seller handoff, safe retry identity, conflict drafts, customer read-only tracking, admin audited correction/delivery, existing cancellation/actions, focus/release refresh, account/record fencing, and compact summaries. Viewports included 320, 390 and 1440 pixels, plus reduced motion. Physical mobile hardware was not tested.
- First Browser attempts exposed fixture issues: an unnamed region locator, a mock treating mutation requests as reads, and the old admin shipping flow. Those fixtures were corrected and the full 20-case run passed.
- Five checkpoint-2 CI failures were mature finance/promotion fixtures missing confirmation/release evidence. Their fixtures were updated; the finance calculator and privacy rules were retained.

Key local evidence: `final-fix-delhivery-full.log`, `final-fix-m3-final-owner-green.log`, `delhivery-m3-browser-verified.log` and `delhivery-m3-*-final.log` in ignored `backend/target/`. Carrier tests cover configuration, staging URL/auth, 50+1 batches, response identity, local/qualified timestamps, reverse flags, explicit batch fallback, non-retryable arbitrary 400/auth failures, Retry-After and transaction-free transport.

The official [Delhivery integration MCP](https://one.delhivery.com/developer-portal/document/b2c/detail/delhivery-mcp) was consulted read-only. It provides API guidance; credentials and live sandbox acceptance remain separate. See [staging setup](../../DELHIVERY_SETUP.md).

## Review fix pass

Independent review identified two Important issues: generic delivery could consume tracked units while the UI hid legacy remainder delivery, and new tracking jobs could bypass carrier Retry-After. Source9fb2ee0 adds server-bound legacy delivery maxima and a persisted per-parcel tracking deadline respected by manual requests, scheduled scans and queued jobs. Both PostgreSQL regressions failed first and then passed. The full399-test local run had one unrelated-fixture job-count assertion; the assertion now scopes this parcel, and both affected regressions pass. Exact-source full GitHub CI remains the release gate.

The open-panel refresh observation was also fixed: visible30s/focus revalidation retains content, and a changed order revision refreshes its parent order view. The Browser regression failed before each fix and the final suite passed21/21, with101frontendtests,cleanlint/build. The existing bundle advisory remains. See the original [independent review](final-review.md); these findings were addressed in one inline test-driven pass, not a claim that the original reviewer approved an unexamined fix diff.
