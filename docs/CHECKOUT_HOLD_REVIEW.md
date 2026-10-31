# Checkout stock-hold repair — 25 October 2026

## Cause and correction

`POST /api/orders/capture` prepares an order before the gateway handoff. It previously required an existing, unexpired cart reservation. Bags remain saved after their 45-minute reservations expire; cleanup removes the reservations and releases stock. This left an ordinary saved bag unable to check out, even when its products had enough available stock. Read-only beta inspection confirmed the reported lamp had 12 physical units, zero reserved units, a one-unit cart item, and no remaining reservation.

Checkout now reacquires each item's reservation in the existing order-preparation transaction, in consistent product order. Existing reservations are renewed by quantity delta, so their units are not counted twice. If any item is genuinely unavailable, the transaction rolls back every earlier reservation change and creates neither an order nor a gateway request. No inventory check was bypassed and no stock was increased manually.

The regression investigation also reproduced a cleanup race: cleanup loaded an expired reservation, waited for a product lock, then deleted that reservation even if checkout had renewed it while cleanup was waiting. Cleanup now reads only candidate identifiers, acquires the product lock, loads the current reservation and rechecks its expiry before releasing stock.

## Regression evidence

Four tests against disposable PostgreSQL databases failed against the old code and passed after the repair:

- A bag whose expired reservations were already cleaned up checks out at its unchanged quantities.
- Existing expired reservations renew without double-counting stock.
- A genuine shortage on a later item rolls back earlier reacquisition, preserves the bag, and creates no order or gateway call.
- A deterministic PostgreSQL lock-contention test proves cleanup retains a reservation renewed while it was waiting for that product lock.

The full backend suite passed 212 tests with zero failures, errors or skips. A separate code review found no concrete blocker in the reacquisition and cleanup changes. Frontend code is unchanged; its 82 regression tests, lint and production build passed. The pre-existing large-bundle advisory remains a build warning.

## Beta deployment and original-case validation

Staging commit `799d9ceecfe9785ad63a52d4b9b908d56bb8f9a5` passed GitHub Application checks. Prerelease [`v0.1.0-beta.4`](https://github.com/saikat-ghosh-in/mercato/releases/tag/v0.1.0-beta.4) passed its backend/PostgreSQL, frontend, operations and dependency checks before publishing the JAR. The beta redeploy wrapper verified its checksum and completed the joined Netlify session readiness check. Installed JAR SHA-256: `dc8d3502b41639f6a018968de7d99370ec4081e0df2e726e6b4248a4bc01101b`.

Before and immediately after deployment, beta retained 2 users, 7 products, 4 categories, 1 order, Flyway rank 8 and zero cart reservations. The beta service restarted from PID 3676177 to 3682039. Production stayed active at PID 1098123. No migration or direct data repair was required.

In the signed-in Browser, the original one-lamp/four-bowl bag was reloaded without changing its quantities. Clicking **Continue to payment** successfully created a new unpaid sandbox order at ₹1,763 and opened Cashfree's sandbox checkout. The database then showed:

- Lamp: physical quantity 12, reserved quantity 1, bag quantity 1, live hold quantity 1.
- Bowls: physical quantity 40, reserved quantity 4, bag quantity 4, live hold quantity 4.
- Both the older pending order and the new test order remained `CREATED` / `INITIATED`, with no captured payment.

The extra order is an intentional unpaid test of the previously failing capture endpoint. No payment was submitted and no funds were captured. [Sanitized sandbox screenshot](screenshots/payment/checkout-renewed-holds-sandbox.png).

## Scope

The stock-hold lifetime remains 45 minutes. This change renews reservations when a customer deliberately continues checkout, rather than keeping abandoned bags reserved indefinitely. Payment capture, allocation, refund, pricing, shipping and commission rules are unchanged. The README and local configuration edits that existed before this task remain outside its commits.
