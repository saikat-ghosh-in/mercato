# Scoped final-fix re-review

**I1: ADDRESSED. I2: ADDRESSED. New Critical/Important findings: none.**

Reviewed the supplied `final-fix-review-package.txt` for `e75c3726ae08a9b8ec5ef2cadafe1c6fe1b29cf7..b8d2f30820181f9b934ae033ad3d947551d17b83`, against `final-fix-brief.md`, I1/I2 in `final-core-branch-review.md`, and `final-fix-report.md`. This is one bounded re-review of those fixes and new significant breakage in their diff, not a fresh branch audit.

## I1 — ADDRESSED

- `backend/src/main/java/com/mercato/Service/OrderServiceImpl.java:58` commits intent lookup before taking the existing order-first locks. At `:61`–`:65`, both an exact-intent stored-session return and gateway creation require current payability plus `ensureCartHoldsForRetry`. The existing helper (`Service/OrderRetryHoldService.java:27`) restores missing/reduced selections with explicit retry lineage, preserves adequate unrelated selections, and rejects insufficient independent stock before exposure. Its immediate cart dependency (`Service/CartServiceImpl.java:246`) locks the cart and sorted union of cart/order products. The immutable stored request/body, merchant identity and original prices are retained.
- Both GETs are metadata-only: `Commerce/PaymentSessionRecovery.java:31` returns no intent session, and `:35`/`:45` prohibit by-order session exposure without a prepared session. `Controller/OrderController.java:89` and `:98` retain private/no-store responses. These read-only paths perform no hold mutations or gateway I/O. `OrderMapper`/`PaymentResponseDTO` do not leak a nested payment session.
- The actual handoff runs the protected POST at `Controller/OrderController.java:91`–`:94`, through existing `retryPayment` stock preparation (`Service/OrderServiceImpl.java:155`). `frontend/src/components/checkout/Checkout.jsx:174` navigates sessionless PAYMENT_READY recovery by order ID; `frontend/src/reduxStore/services/privateApi.js:20` prepares via POST, including reload, before `CashfreePayment.jsx:52` opens the SDK. Explicit reopen also refetches preparation at `CashfreePayment.jsx:33`. Existing CSRF/session interception, authentication and server-derived ownership apply; no security configuration was relaxed.
- The changed real-PG tests at `inventory/PostgresCheckoutReservationTest.java:218`, `:219`, `:241`, `:247` and `:260` cover uncertain exact replay with missing/reduced selections at exact stock, independent-hold rejection, mutation-free GETs, actual POST handoff, and allocation exactly once. The browser diff uses the actual Checkout/CashfreePayment components and RTK method contract for PAYMENT_READY/reload/reopen/account-change paths.

## I2 — ADDRESSED

- `Commerce/CheckoutPayability.java:9` is shared by exact replay, explicit retry, both recovery reads and post-network finish. It rejects closed/changed quantities, capture evidence, authorization timestamp/ID/amount, financial halt and allocation/confirmation/full-refund closure. Thus an unpaid partially cancelled original-total decision cannot expose its old payable session while its recorded surviving quantities remain intact.
- `Service/OrderLineUpdateServiceImpl.java:65`–`:70` atomically queues existing provider termination/reconciliation for uncaptured cancellation and retains existing refund handling for funded cancellation. Whole cancellation delegates through that same quantity path (`Service/OrderServiceImpl.java:194`). `CashfreeServiceImpl.java:45` uses stable cancellation keys; the existing transactional `DurableJobs`/`JobStore` join the cancellation transaction and deduplicate them.
- Create success/failure at `Service/OrderServiceImpl.java:67`–`:79` and resume/recreation success/failure at `Service/CashfreeServiceImpl.java:60`–`:87` recheck after unlocked network I/O. Cancelled outcomes queue distinct stable `terminate-after-payment-io:`/`reconcile-after-payment-io:` jobs (`:51`–`:56`). Rejection occurs after the transaction commits, preserving queued work. Existing handlers support termination verification and paid-order reconciliation. No provider network call was moved under database locks.
- Changed tests at `inventory/PostgresCheckoutReservationTest.java:270`–`:306` cover partial/whole/uncertain cancellation, deduplication, replay/retry/both recovery fences, authorization/capture/halt, and five latch-controlled create/resume/404/uncertain races. At `:320`, initial jobs are marked SUCCEEDED before releasing the provider latch; the assertions verify distinct post-I/O jobs survived the rejected result. Saved existing regressions include `lateCaptureKeepsCancellationAndRefundsActualCapture`, `partiallyCancelledCheckoutCannotResurrectCancelledItems`, and `refundTransportReconcilesStableIdentityOutsideTransactionsAndSurvivorsStayActionable`.

## Saved verification checked

Read the saved results, matched the new regression names/assertions to the supplied diff, and parsed the twelve named Surefire XML files. No suites were rerun.

- Backend: **152 tests / 12 suites / 0 failures / 0 errors / 0 skips**. Counts: checkout PG 34, quantity PG 42, practical finance PG 8, capture/concurrency 30, place order 4, retry hold 5, reservation 9, cart provenance 5, gateway snapshot 1, payment boundary 5, intent response 1, session security 8. XML modification times span `2026-10-28T11:03:45.7373170Z`–`11:04:37.8579695Z`, consistent with the fix report and `backend/target/final-fix-final-focused.log`.
- `backend/target/final-fix-client-green.log`: **95 passed, 0 failed/skipped**.
- `backend/target/final-fix-browser-final.log`: **5 passed, 0 failed/skipped**, covering the changed handoff contract with mocked transport/SDK and compiled CSS. The test source retains 320/390px and reduced-motion checks.
- `backend/target/final-fix-lint.log`: eslint output with no diagnostics. `backend/target/final-fix-build.log`: successful Vite build; existing >500 kB chunk advisory remains nonblocking.

These are saved local evidence, not independently rerun exact-HEAD CI or live-provider proof. Existing framework/error-path log noise is not characterized as clean output.

## Scope and remaining gates

No additional Critical/Important breakage found in the fix diff or the immediate mapper, lock, security and job dependencies checked for concrete integration questions. No new logistics, return, authorization or payment engine is required by this re-review.

The controller reports exact `b8d2f30` CI run `40677562282` SUCCESS with the full suite and a matching Netlify build ready and held; those are controller-supplied facts, not live checks performed by this reviewer. The compatible beta/live-browser/provider gates remain controller work. Provider/SMTP transports in the reviewed tests are mocked; hardware-phone behavior is unverified. Keep authorization activation OFF and main/production unchanged pending those gates.

No git rediscovery, suite reruns, source/index edits, subagents, SSH, provider/SMTP calls, push or deploy were performed. Only this report was written; the preserved README/local configuration and controller documents were not changed.
