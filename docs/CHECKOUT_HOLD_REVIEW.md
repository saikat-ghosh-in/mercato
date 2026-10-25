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

## Scope

The stock-hold lifetime remains 45 minutes. This change renews reservations when a customer deliberately continues checkout, rather than keeping abandoned bags reserved indefinitely. Payment capture, allocation, refund, pricing, shipping and commission rules are unchanged. The README and local configuration edits that existed before this task remain outside its commits.
