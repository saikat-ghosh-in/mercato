# Editorial redesign review — 18 October 2026

The approved white / black / soft-blue direction is implemented across storefront, authentication, account, checkout, seller and admin surfaces. Original Mercato SVG branding and a locally bundled WebP campaign photograph replace the green visual system. Existing marketplace categories and server pricing remain authoritative.

## Run locally

Backend: from `backend`, run `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo`.
Frontend: from `frontend`, run `npm install`, then `npm run dev -- --host 127.0.0.1 --port 5174`.
Production build review: `npm run build`, then `npm run preview -- --host 127.0.0.1 --port 5175`.

Local demo administrator / seller: **reviewer / ReviewOnly!2026**. Demo uses disposable H2 data, loopback binding and disabled Cashfree credentials. Restarting the backend resets fixtures. These credentials are rejected outside the demo profile and when prod is active. Never enable the demo profile against a production database.

## Product data and schema rollout

Apply `backend/sql/20261018-rich-product-details.sql` to the staging PostgreSQL database before deploying the new backend. Take a database backup first. The script adds nullable columns plus ordered highlight/specification tables and can run repeatedly. It does not rewrite existing product rows. Keep these additive fields when rolling back application code; older clients ignore them.

Product requests/responses now support brand, productCode, materials, care, warranty, packageContents, deliveryReturns, highlights and ordered specifications (`label`, `value`). Omitted new fields preserve their existing values on updates; empty strings/arrays explicitly clear them. Limits: brand/code 100 characters, long text 2,000, 10 highlights of 300 characters, and 30 specifications with 100-character labels / 500-character values. Product creation/update uses bean validation including nested values. Rich fields are editable from both seller and admin product management. Missing details stay hidden on the PDP. Demo policy/material text is clearly synthetic.

The migration was exercised twice against H2 in PostgreSQL compatibility mode with existing-row preservation and collection cleanup assertions. A deployment against a real PostgreSQL staging instance is still required; no external database was modified.

## Verification recorded

- Frontend: lint clean, production build successful, 15 regression tests pass.
- Backend: 49 tests pass, zero failures/errors/skips. Includes database/security flows, rich product persistence and legacy updates, ownership, invalid nested input, migration idempotency, demo credential isolation, duplicate phone conflicts, role-input validation, upload validation, inventory reservations, payment callbacks and finalization concurrency.
- Browser desktop: home/navigation/search, catalog filters/sorting, PDP/gallery, bag quantity and badge, guest sign-in/cart merge, failed sign-in, rich product create/edit, inventory adjustment, category create/edit, admin overview/users/roles-dialog/orders/carts, profile edit, address validation/save, checkout delivery/review/payment-provider failure, wishlist add/remove, and seller overview.
- Local product creation retained ordered highlights/specifications in the editor; a stock delta changed 12 to 14 without removing the product table during refresh. Role editing was inspected and cancelled; no extra privileges were granted.
- Checkout retained its selected address and bag when the disabled demo payment provider failed. Actual totals included the server platform fee.
- Production preview at port 5175: mobile registration/validation, missing reset and verification tokens, account-status screen, filtered price sorting, navigation/Escape, gallery, quantity changes, sign-in/guest merge, delivery address save and checkout review. A two-unit bag showed ₹2,698.20 plus the backend ₹12 fee, total ₹2,710.20.
- Desktop 1440×1000: three product columns (about 336px each). Mobile 390×844: two columns, with document width 375px; PDP, bag, checkout and admin directory did not overflow horizontally. Wide management tables scroll inside their own containers.
- Seller catalogue/inventory, active/history fulfillment tabs and search; admin user drawer via Enter and Escape; signed-in password recovery; incomplete payment return and order recovery; help disclosure and missing-page recovery were exercised in Browser.

## Screenshots and control coverage

### Follow-up: consistent controls and stable updates

All action buttons now use the shared pill radius, including secondary, destructive and portal-dialog actions. Icon actions remain circular; image thumbnails and underlined tabs retain their respective image/navigation shapes. Browser computed styles confirmed the same 999px action radius on profile navigation, save/cancel and category-dialog create/cancel controls.

The bag checkout link no longer swaps into a dimmed button while quantities update. Its element, label, background and opacity remain stable; `aria-disabled` and a click guard prevent checkout during pending writes or errors. Only the affected item's quantity controls become pending. Cart presentation metadata is cached separately from live server prices and stock flags, avoiding redundant product/image fetches for quantity-only updates. Failed metadata lookups can retry, and aborted responses cannot populate the cache.

The catalog now pins its count, filters, selected chips and sorting below the sticky header on desktop and mobile. The desktop filter column scrolls independently. ResizeObserver measures header/search and toolbar height, with no React state updates on scroll. Query-only changes retain browsing position; pagination returns to the toolbar. Search focus uses preventScroll. Filter/mobile drawers use interruptible 240ms transform transitions, backdrops/popovers use 180ms opacity, and press feedback uses 140ms. Reduced motion removes movement and retains a gentle opacity transition.

Validated in Browser at 1440×1000 and 390×844: scrolled toolbar top aligns with header bottom (89px desktop, 73px mobile); sorting and opening/dismissing filters remain reachable; expanded search adjusts the sticky offset. No horizontal document overflow. Quantity changes retained the black checkout link at opacity 1. Lint, build and all 15 frontend tests pass; two new regressions check metadata reuse/retry and aborted lookup isolation. Backend source is unchanged in this follow-up; its previously recorded 49-test result still applies.

- [Scrolled desktop catalog](screenshots/plp-sticky-desktop.jpg)
- [Scrolled mobile catalog](screenshots/plp-sticky-mobile.jpg)
- [Stable cart checkout](screenshots/cart-stable-desktop.jpg)

See [the route/action checklist](VALIDATION_CHECKLIST.md) for the distinction between interactive checks, regression coverage and remaining service-dependent checks.

- [Desktop home](screenshots/home-desktop.jpg)
- [Desktop catalog](screenshots/catalog-desktop.jpg)
- [Mobile product details](screenshots/pdp-mobile.jpg)
- [Mobile bag](screenshots/bag-mobile.jpg)
- [Mobile sign-in](screenshots/login-mobile.jpg)
- [Mobile checkout](screenshots/checkout-mobile.jpg)
- [Seller products](screenshots/seller-products-desktop.jpg)
- [Mobile admin directory](screenshots/admin-mobile.jpg)

## Fixes beyond the visual refresh

- Search no longer remounts on query updates. Loaded catalog/product content survives refreshes, with abort/stale-response handling and bounded route skeletons.
- Cart writes and refreshes use one serial queue. Login waits for guest writes; identity changes invalidate stale results. Unit counts normalize numeric data, hide zero and show 99+ visually while preserving the full accessible count.
- Quantity changes preserve product rows/images. Per-product pending state blocks duplicate mutations; invalid responses cannot empty a valid bag.
- Rich product details have real persistence and validation, with compatible legacy updates.
- Wishlist mutations block repeated requests and removal immediately updates the wishlist page.
- Shared dialogs trap focus, support Escape and restore focus. Stock selection uses the existing Headless UI combobox; category actions remain visible on touch screens. Icon actions have accessible names.
- Recovery routes work for signed-in users. Profile fields support clearing optional names and editing seller details; duplicate phone numbers return a conflict instead of silently being ignored.
- Gateway failures return usable messages without internal transport details. Empty/null role updates return bad requests.
- Motion uses restrained press/menu/drawer transitions, respects reduced motion, and avoids whole-page update fades.

## External limits and release gates

Cashfree hosted payment, real email delivery, successful payment-to-shipment browser flows, refund settlement and physical touch-device behavior require configured sandbox services / hardware. Local gateway calls intentionally fail and no real payment was attempted. Existing automated backend tests cover payment and inventory behavior with mocks; this does not prove a live gateway integration. Synthetic accounts and test data were used throughout.

Seller permissions remain administrator-managed; registration now explains this instead of advertising a disabled future feature. Reviews and selectable variants remain outside this revision. Missing merchant product/policy information must be supplied by sellers rather than invented by the UI.

The commit is for local `stg` review. Remote branches and main must remain unchanged. Pre-existing edits to README.md and application-local.properties are excluded.
