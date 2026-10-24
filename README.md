# Mercato

Mercato is a multi-seller e-commerce marketplace aimed at the Indian market — INR pricing, domestic payment methods, seller fulfillment dashboards alongside the customer storefront. The live demo is at [mercato-in.netlify.app](https://mercato-in.netlify.app).

Listed product prices are tax-inclusive — GST and other levies are already baked into selling price. Checkout only adds shipping, platform, and processing fees; no separate tax line.

This repo is a monorepo: the Spring Boot API lives in `backend/`, and the React storefront lives in `frontend/`. I kept them together so order/payment/inventory changes stay in one pull request instead of chasing two repos.

---

## How the repo is organized

**Backend (`backend/`)** — standard Spring Boot layout under `backend/src/main/java/com/mercato/`:

- `Entity/` — JPA models. Cart stuff (`Entity/cart/`), order snapshots and fulfillment (`Entity/fulfillment/`), payments and refunds (`Entity/fulfillment/payment/`).
- `Service/` — where the real logic lives. `OrderServiceImpl` owns checkout capture; `CashfreeServiceImpl` owns the payment gateway boundary; `CartReservationServiceImpl` and `OrderReservationServiceImpl` own inventory holds; `FulfillmentServiceImpl` and `OrderLineUpdateServiceImpl` handle post-payment seller workflow.
- `Controller/` — thin REST adapters. Orders, cart, products, auth, public Cashfree webhook.
- `Repository/` — Spring Data JPA. Most interesting queries are around abandoned orders, cart expiry, and pessimistic product locks.
- `Security/` — JWT for registered users and a separate guest token for anonymous carts. Role gates for customer / seller / admin routes.
- `Schedular/` — background jobs: guest-cart TTL cleanup, cart-reservation expiry, abandoned unpaid-order cancellation.
- `Mapper/`, `Payloads/` — DTO mapping and API contracts. Kept separate from entities on purpose so the DB model does not leak into the frontend.

**Frontend (`frontend/src/`)** — React 19 + Vite, Redux Toolkit for client state:

- `reduxStore/` — cart, auth, orders, products. Cart is persisted locally; it is deliberately *not* cleared until payment is confirmed.
- `backend/api.js` — single axios instance, attaches Bearer or guest JWT.
- `components/checkout/` — multi-step checkout (address → review → payment).
- `components/payment/` — Cashfree SDK wrapper and the post-return confirmation page that polls the backend.
- `components/seller/` and `components/admin/` — fulfillment and ops dashboards on the same app shell.

The backend is the source of truth for inventory, money, and order state. The frontend orchestrates UX and retries; it does not decide whether an order is paid.

---

## Inventory: why two reservation types

Every `Product` tracks `physicalQty` and `reservedQty`. Available stock is the difference. Writes that touch inventory go through `findByProductIdForUpdate` (pessimistic row lock) and the entity has `@Version` for optimistic safety on concurrent updates.

I split reservations into two tables instead of one generic “hold” because the lifecycle is genuinely different:

**Cart reservations** (`CartReservation`, one per `CartItem`) are soft holds while someone is browsing or sitting in checkout. They expire after 45 minutes (`cart.item.reservation.minutes`). A scheduled job releases expired holds and puts quantity back into circulation. Guests get a cart backed by a JWT; logged-in users get a persisted cart that merges on login.

**Order reservations** (`OrderReservation`, one per `OrderLine`, unique on `order_line_fk`) are the committed hold after money clears. They stay until fulfillment settles the line — shipping decrements both physical and reserved stock; cancellation releases reserved only.

That split is why checkout does *not* move inventory at order capture. When the customer clicks pay, I snapshot the cart into an `Order` (line prices, address, totals) and open a Cashfree session, but the cart and its reservations stay put. Inventory only transfers to the order on payment success. If payment fails or the customer abandons checkout, the order stays `CREATED`, cart holds remain valid (until TTL), and an abandoned-order job cancels stale unpaid orders after 48 hours and releases matching cart holds.

`CartItem.unitPrice` is snapshotted when the item is added so the order lines freeze pricing at capture time even if the seller edits the catalog mid-checkout.

---

## Payments: why Cashfree

Mercato is built for India — INR, local pin codes, UPI/cards/wallets through a domestic gateway. That constraint drove the choice.

**Stripe** is excellent for global card-first commerce, but it is not where I wanted to spend integration effort for an India-first storefront: domestic payment rails and merchant onboarding are not the path of least resistance for this use case.

**Razorpay** is the obvious local alternative and would have worked. I went with **Cashfree** because its API is organized around an order object with a payment session, status polling via `GET /orders/{id}`, and signed webhooks — that maps cleanly onto how I already model `Order` + `Payment` in JPA. Refund status sync uses the same client. The integration surface lives almost entirely in `CashfreeServiceImpl`; the rest of the app talks to `CashfreeService` and does not care which gateway is underneath.

---

## Checkout, end to end

Here is the path a paid order actually takes through the system.

**1. Cart** — Adding or updating quantity calls `CartReservationService.reserve()`, which locks the product row and increments `reservedQty`. Removing an item or clearing the cart calls `release()`. The frontend shows availability based on what the API returns; it does not compute stock locally.

**2. Order capture** — `POST /orders/capture` (`OrderServiceImpl.placeOrder`) validates the cart is non-empty, resolves the shipping address with an ownership check, builds an `Order` in `CREATED` status with snapshotted lines, creates a Cashfree order, and stores a `Payment` in `INITIATED` with the session ID. Before saving, it runs `validateHeld()` on each cart line so checkout fails fast if a hold expired. The cart is left intact.

**3. Payment** — The frontend loads Cashfree’s JS SDK (`CashfreePayment.jsx`) with the session ID and sends the customer through their hosted flow. On return, `PaymentConfirmation.jsx` lands on `/checkout/payment-confirmation?order_id=…` (`/payment-confirmation` is kept as an alias).

**4. Confirmation via webhook and poll** — Cashfree notifies the backend asynchronously (`handleWebhookEvent` → `PAYMENT_SUCCESS`), and the confirmation page polls `POST /orders/{id}/sync-payment-refund`, which calls `syncOrderStatus` when the Cashfree order is `PAID`. Both paths converge on the same method: `finalizeOrderAfterPaymentSuccess()` in `CashfreeServiceImpl`.

I need both because webhooks are not reliable as the only signal — dropped delivery, network blips, or a user closing the tab before the webhook fires should not leave an order stuck in `CREATED` after Cashfree has the money. Polling is the backstop; the webhook is the fast path.

**5. Finalize inventory** — `OrderReservationServiceImpl.finalizeInventoryForPaidOrder()` is idempotent per line:

- If an `OrderReservation` already exists, release any orphaned cart hold still hanging around (this handles the poll-before-webhook ordering without double-counting `reservedQty`).
- Else if a cart hold exists, *transfer* it: delete the `CartReservation`, insert the `OrderReservation`, no net change to `reservedQty`.
- Else *reserve* fresh for the order line (poll-only path where the cart hold expired but payment still succeeded — see *What is not built yet*).

The method returns `true` only when this invocation actually created the reservations. That boolean is the “primary finalizer” signal.

**6. Concurrency** — Webhook and poll can hit finalize at the same time for the same order. There is a unique constraint on `order_line_fk` in `order_reservations`. Rather than taking a pessimistic lock on the whole order, the loser catches `DataIntegrityViolationException` on insert, treats it as “already finalized,” reconciles any stray cart hold, and returns `false`. The winning transaction’s inventory changes stand; the loser’s uncommitted work rolls back.

The confirmation email uses the same gate: `handlePaymentSuccess` only calls `EmailService.sendOrderConfirmationEmail` when `finalizeOrderAfterPaymentSuccess` returns `true`. A losing concurrent caller must not send a duplicate email just because it confirmed the order object in memory before losing the insert race.

**7. Cart clear and customer signal** — If the buyer is logged in and still has cart rows, `clearCart` runs after successful finalization. The confirmation page clears Redux cart state once it sees `paymentSummary.status === SUCCESS`. The customer gets one confirmation email from the primary finalizer.

**8. Fulfillment** — After confirmation, sellers work order lines through accept → ship / cancel via `OrderLineUpdateServiceImpl`. `OrderReservationServiceImpl.settleQty()` adjusts physical and reserved stock on ship or cancel. Refunds go back through Cashfree and sync via webhook plus the same sync endpoint the confirmation page uses.

---

## Background maintenance

Three schedulers keep inventory honest without manual intervention:

- **Cart reservation expiry** — releases holds past TTL so stale carts do not lock stock forever.
- **Guest cart cleanup** — deletes old guest carts and releases their reservations before removal.
- **Abandoned order cleanup** — cancels unpaid `CREATED` orders older than 48 hours, terminates the Cashfree order, and releases cart holds for matching product IDs so a retry does not inherit dead state.
