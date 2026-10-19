Latest follow-up: [Delivery, package measurements and capped commission](PACKAGE_PRICING_REVIEW.md) — simplified delivery choices, validated packed weight/dimensions, configurable bulky delivery, 0.5% / ₹500-per-item commission, dropdown/mobile-dialog fixes and pointer cursors. Frontend: 36 tests, lint/build pass. Backend: 81 tests pass.

Latest follow-up — 19 October 2026: [Compact timeframe editor, seller notice badge, field help and persistent catalog navigation](CONTROL_REFINEMENT_REVIEW.md). Frontend lint/build and 34 tests pass; Browser evidence and screenshots are linked there.

Current revision: checkout/delivery/default addresses, seller onboarding/commission notices, financial analytics, listing approvals and paid promotions are recorded in [Marketplace review](MARKETPLACE_REVIEW.md). Earlier route checks below remain historical evidence. Current automated result: 32 frontend tests, lint/build, and 71 backend tests pass.

# Route and action validation — 19 October 2026

This records what was actually checked. Browser checks used disposable demo H2 data, desktop 1440×1000 and mobile 390×844, including the production Vite preview. Shared controls were checked in their owning flow; this is not a claim that every possible data permutation or live service was exercised.

## Storefront and navigation — Browser

- [x] Home campaign, categories, featured collection, header and footer destinations render in the new visual system.
- [x] Search submission, catalog category filter, price controls, sorting, selected-filter removal and mobile filter/navigation dialogs.
- [x] Three desktop product columns and two mobile columns; no document overflow in measured screens.
- [x] Product details, gallery arrows/thumbnails, price, availability, rich details and conditional disclosures.
- [x] Add to bag, increase/decrease/remove, accurate accessible badge and server-calculated charges. Removing a line retains unrelated lines (baseline Browser check and regression coverage).
- [x] Saved items add/remove and empty state; anonymous access requests sign-in.
- [x] Help disclosures, missing-page recovery, failed requests and retry controls (baseline Browser and current regression coverage).

## Authentication and account — Browser

- [x] Login, invalid credentials, retained entered values and guest-bag merge on successful login.
- [x] Registration validation/submission and verification-pending page using synthetic account data.
- [x] Forgot-password layout, missing reset token, missing verification token and account-deactivated screen.
- [x] Signed-in Change Password action opens recovery instead of redirecting back to the home page.
- [x] Profile read/edit/save; address required validation and create/save/select in checkout.
- [x] Address edit/delete dialogs and focus/Escape behavior inspected; current save and selected-address behavior verified.
- [x] User drawer keyboard activation, focus trap and Escape dismissal.
- [x] Sign-out and clearing session-associated bag/wishlist state inspected in implementation; invalid/expired authentication has automated API boundary coverage.
- [ ] Real verification/reset emails and completed reset link require SMTP sandbox. No existing user's credentials were changed.
- [ ] Account deactivation and role grants were not performed interactively. The dialogs were inspected without changing access; permission/validation boundaries have backend tests.

## Current follow-up — compact corners and product-page wishlist

- [x] Shared 8px control/hover corners, 10px panels, smaller badges; native selectors retain appearance-none chevrons and keyboard behavior. Functional spinner/native radio shapes retained.
- [x] Header grid button and header heart removed; magnifier opens Search & discover with focused search, categories, curated links and Browse everything. Browser search/category navigation and Escape/focus return verified.
- [x] Product cards in catalog/recommendations/saved items expose zero buttons. Guest wishlist requests sign-in and returns to PDP; save persists in Saved items, then removal through PDP restores the empty list.
- [x] Left filter is visibly 30px, with 44px phone/coarse-pointer targets. Sort Apply closes the drawer, restores focus and updates URL/results. Landscape Apply remains reachable.
- [x] Catalog/header measured at 320/360/390/414/768/1024/1440/1920 without document overflow. First row 173px phones / 187px tablet / 195px desktop. Search remains available at 320px; sticky destinations remain accessible after scrolling.
- [x] Bag quantity change retains checkout appearance; two-unit accessible badge and ₹47,510.10 backend total verified. Synthetic address save/select and checkout Review work. Checkout widths 320/390/768/1024/1440 have no document overflow.
- [x] Admin product search/selects measure 36px desktop / 44px phone. Shared seller/admin product dialog inspected at 320px, 8px actions / 10px panel, dismissed without saving.
- [x] Final frontend lint, 27 tests and production build pass. Keyboard discovery transition is 0ms; pointer transform/opacity is 240/180ms. Modal keyboard-motion selector corrected.
- [ ] Physical phones, OS reduced-motion settings, controlled network throttling and payment/email sandbox still require external validation. Backend unchanged; preceding 49 passing backend tests were not rerun for this UI-only follow-up.

## Previous follow-up — Explore, icon-only filters and compact controls

- [x] Header replaces its three shopping links with one Explore control; category, curated selections, search and Browse everything work in Browser.
- [x] Icon-only catalog action; sorting appears only inside the filter drawer. Draft edits/cancellation, atomic Apply, search preservation and retained sort after Clear have regression coverage and Browser checks.
- [x] Shared controls measure 36px desktop / 44px prominent and mobile; glyphs are smaller while phone hit targets stay 44px. Admin search/select/Add controls align at 36px.
- [x] Catalog/header measured at 320/360/390/414/768/1024/1440/1920 without document overflow. First row: 173px on typical phones, 199px at 320px, 195px desktop. Header/toolbar remain accessible after scrolling.
- [x] Pointer drawers use 240ms transform/opacity motion; keyboard opening measures 0ms. Escape and search-close restore focus. Reduced motion and hover guards inspected in source.
- [x] Sign-out/sign-in restores the bag; quantity changes keep checkout appearance stable. Primary controls measure 44px on sign-in, PDP and checkout. Admin product dialog dismissed without saving.
- [ ] Physical phones/OS motion settings remain external checks. Backend unchanged in this follow-up; previous 49-test backend run remains documented.

## Previous follow-up — checkout, homepage and dashboard search

- [x] Original multi-category campaign image and marketplace copy, working catalog/category links, once-only entry/scroll reveals, reduced-motion and fine-pointer hover safeguards.
- [x] Delivery/review/payment redesign; saved address add/select/delete, edit prefill, Escape/focus restoration and keyboard progression in Browser.
- [x] Current-address validation, unavailable/empty bag guards, malformed payment handoff and backend total regression coverage (four new tests).
- [x] Recoverable local gateway failure and retry preserve the address, bag and step. Missing payment sessions redirect to checkout; incomplete returns expose order/bag recovery.
- [x] Checkout measured at 320/360/390/414/768/1024/1440/1920px and 844×390 landscape without document overflow; primary actions measure 48px.
- [x] Products/inventory/users/orders/carts/fulfillment searches match 40px desktop and 44px mobile/tablet controls. Search and clearing work; a narrow-phone search occupies a full row.
- [x] Stable cart checkout appearance during quantity increase/decrease; correct two-unit badge and server total verified again.
- [ ] Successful Cashfree-hosted checkout, real-device touch behavior and OS reduced-motion settings still require sandbox/hardware. No paid outcomes were fabricated in Browser.

## Checkout and orders — Browser plus regression tests

- [x] Guest checkout redirects to sign-in and returns after server bag merging.
- [x] Delivery selection, review, payment step and real backend fee totals.
- [x] Provider failure keeps address and bag; incomplete return offers orders/bag recovery.
- [x] Orders page and empty state, navigation back to shopping.
- [x] Mocked backend coverage for reservations, expiry, stock conflicts, order transitions, payment signatures/callbacks, retries, partial cart consumption and repeated/concurrent finalization.
- [ ] Live paid-order details, hosted payment, fulfillment/shipment and refunds require configured Cashfree/SMTP sandbox. No real funds were transacted.

## Seller and admin — Browser

- [x] Seller overview, product catalogue, perpetual inventory and supply-update dialog.
- [x] Product create/edit with brand, highlights, ordered specifications and care; values persisted when reopened.
- [x] Supply selection by keyboard and stock delta save (12 → 14); existing table stays mounted while refreshing.
- [x] Active fulfillment/history tabs, search and empty states; failure details now expose retry/back controls.
- [x] Admin overview, users, orders, categories, products and carts tabs.
- [x] Admin category create/edit persisted; user role dialog and drawer inspected and dismissed.
- [x] Shared product editor available in both dashboards; category/search controls have accessible names.
- [x] Mobile admin directory scroll containment; desktop seller inventory reflects shopper reservations.
- [ ] Paid fulfillment action buttons and cancellation/refund completion were tested through backend mocks, not a live paid browser order.

## Automated checks and implementation audit

- [x] `npm run lint` and `npm run build` pass.
- [x] `npm test`: 27 tests pass, including cart normalization/serial queue/pending state/identity reset, stale catalog results, failed refresh retention, rich editor conversion, metadata reuse/retry, aborted metadata isolation, draft/cancel/reopen, atomic Apply, price validation and URL preservation.
- [x] Current responsive checks: widths 320/360/390/414/768/1024/1440/1920 and 844×390 landscape; tested pages stay within the viewport, header remains accessible, and management tables scroll internally.
- [x] On-demand filters: initially closed, draft changes leave results/URL untouched, Cancel/reopen restores applied values, invalid prices retain the drawer, Apply commits/closes/restores focus, and clearing a draft remains cancellable.
- [x] Product rows start at 195px desktop, 205px on 360/390px phones and 231px at 320px. Native selector arrows align with their vertical centers. Forms/checkout use 48px primary actions and mobile dialog actions use 44px targets.
- [x] Mobile product title taps work across wrapped lines; gallery, guest bag/login merge, quantity updates, address save/review and account edit were exercised. The filter footer remains reachable in landscape.
- [x] Follow-up Browser checks: consistent pill action shapes on account/forms/admin dialogs, unchanged checkout link appearance after quantity mutations, desktop/mobile sticky filter/sort toolbar, scroll containment, search-height offsets, and filter drawer dismissal.
- [x] `mvnw.cmd test`: 49 tests pass, no failures/errors/skips. Covers authorization/ownership, nested validation, rich persistence/legacy updates/clearing, role inputs, duplicate phone, uploads, cart/reservations, payment callbacks and order transitions.
- [x] Additive SQL applied twice in H2 PostgreSQL mode, retaining the old product row and checking collection cleanup.
- [x] Static motion audit: transform/opacity feedback, reduced-motion override, retained content during refresh, image dimensions reserved, no page-wide update fade.
- [x] Long rich fields are bounded by backend validation and editor limits; invalid specifications preserve form values.
- [ ] Real PostgreSQL staging deployment, physical touch devices, OS reduced-motion behavior and controlled network-throttling scenarios remain release checks. Stale/failed request behavior is covered by regression tests; no claim of exhaustive slow-network Browser coverage is made.

Local demo credentials: **reviewer / ReviewOnly!2026**. Demo-only fixture creation is disabled with `prod`; the known demo password is rejected outside demo initialization. Restarting demo resets all synthetic data. Review [deployment and startup instructions](EDITORIAL_REVIEW.md) before staging rollout.
