Latest delivery — 19 October 2026: [Marketplace journey, financial dashboards, free listing approval and paid promotions](MARKETPLACE_REVIEW.md). This supersedes earlier notes about button-free PLP cards, the three-step checkout and immediate seller publishing. Review the linked startup, additive schema instructions, screenshots, test evidence and external-service limitations.

# Editorial redesign review — 19 October 2026

The approved white / black / soft-blue direction is implemented across storefront, authentication, account, checkout, seller and admin surfaces. Original Mercato SVG branding and a locally bundled WebP campaign photograph replace the green visual system. Existing marketplace categories and server pricing remain authoritative.

## Latest refinement: compact corners, quieter cards and search discovery

Shared controls now use 8px corners instead of capsules, with 10px panels and 4px badges. The same shape applies to button hover surfaces, native selectors, authentication/account controls, checkout, dashboard actions and dialogs. Smaller existing 4–6px details remain; functional spinner rings and native radio controls retain their expected shape. Press/menu/drawer timing remains 140/180/240ms, with fine-pointer hover guards, immediate keyboard interactions and reduced-motion support. The final pass corrected the modal keyboard-motion selector after removing the obsolete inline search panel.

Product cards no longer contain heart/save controls anywhere: home, catalog, recommendations or saved items. Saving and removing items uses the labeled action on the product detail page. Saved items remain reachable through Account and the footer; the empty state explains the product-page action. The header grid button beside the wordmark is removed. The existing magnifier opens one Search & discover drawer containing search, real marketplace categories, curated selections and Browse everything. Search receives focus on opening, and Escape restores focus to the magnifier without scrolling.

The catalog uses the shorter All products heading and places its filter icon to the left. The visible filter is 30px with a 14px glyph; on phones/coarse pointers it has a 44px touch target around the same 30px surface. Sorting remains inside the Apply-based drawer. Existing results stay visible while requests update.

Validation for this UI-only follow-up on 19 October 2026:

- Frontend lint, all 27 regression tests and production build pass after the final CSS correction. Initial production JavaScript is 460.11 KB before gzip. Backend APIs/schema are unchanged; the preceding 49-test backend run was not repeated for this styling/navigation revision.
- Browser verified guest wishlist sign-in/return, save persistence in Saved items, reopening the product and removing the temporary saved item. Catalog, related and saved-item cards expose zero button actions. Only the product page exposes the wishlist toggle.
- Catalog measured at 320/360/390/414/768/1024/1440/1920px with no horizontal document overflow. First products begin at 173px on 320–414px phones, 187px on the 768px tablet and 195px on desktop. Filter is left of the heading at every width; search remains visible at 320px. Header stays 64px mobile / 72px desktop.
- Search submission returns the matching camera; category selection returns the three Fashion products. Sort Apply closes the drawer, returns focus to the filter and updates the URL/results. Native sort appearance is none with an 8px corner. Keyboard discovery opens with 0ms transitions; pointer discovery retains 240ms transform / 180ms opacity. Escape restores Open search focus. At 844×390, Apply remains visible at bottom 374px with a 44px target.
- After scrolling 728px on desktop, header/toolbar remain at 0px/72px. Account still exposes orders, saved items, both dashboards and sign-out. Checkout retains Help and Account access.
- Bag increase from one to two camera units retains the checkout label, black background, opacity 1 and 8px corners; the accessible badge reports two units. Backend totals show ₹47,498.10 subtotal plus ₹12 fee = ₹47,510.10. Synthetic address create/select and checkout Review succeed. Checkout has no measured overflow at 320/390/768/1024/1440px; controls measure 8px corners and summary panels 10px.
- Admin product search/selects align at 36px desktop / 44px phone with 8px corners. The product editor has 8px actions / a 10px panel and no document overflow at 320px; it was dismissed without saving. The same product editor and control classes serve seller management.
- At 320px, login/register fields and actions measure 8px corners with no document overflow; sign-out exposes the guest Account menu. The homepage also has no document overflow or product-card buttons at that width. Registration was inspected without creating another account.
- Local servers had stopped between sessions. The production preview and isolated demo API were restarted; the initial catalog connection failure recovered through Try again. Restarting demo resets its disposable H2 data. A synthetic two-unit camera bag and delivery address are left ready for review. Physical phones, OS reduced-motion settings, controlled network throttling and live payment/email services remain external checks documented below.

Current screenshots: [catalog, desktop](screenshots/refined-corners-catalog-desktop.jpg) · [phone](screenshots/refined-corners-catalog-mobile.jpg) · [search discovery, desktop](screenshots/search-discovery-desktop.jpg) · [phone](screenshots/search-discovery-mobile.jpg) · [PDP-only wishlist](screenshots/pdp-wishlist-only-desktop.jpg) · [checkout review](screenshots/refined-corners-checkout-desktop.jpg).

## Previous refinement: quiet controls and a single Explore entry

The catalog toolbar now has one icon-only Filter and sort action, with an accessible name and a small dot when filters or a non-default sort are active. Sorting lives inside the drawer. Category, seller, price, availability and sort changes remain drafts until Apply commits one URL update, resets pagination and closes the drawer. Cancel/Escape discard pending sort changes. Clearing filters preserves sorting, and applying preserves the current search. Loaded products remain visible during the refresh.

Shop / New & Featured / Collections have been removed from the header. A single Explore control opens a focus-managed panel with real marketplace categories, Just added and More for less selections, marketplace search and Browse everything. It works on desktop and mobile; the narrower header uses the same grid icon. Help, Account, orders/sign-out, saved items and the bag retain their existing supported destinations. The header remains persistent at 72px desktop / 64px mobile.

Shared controls are smaller: standard desktop buttons/selects/searches are 36px, primary actions are 44px, and desktop icon actions are 28–36px with 14–18px glyphs. Phone targets remain 44px while labels, padding and visible icon surfaces are lighter. A coarse-pointer capability query preserves those targets and 16px input text on larger touch screens too. Product save circles are visually 32px inside 44px phone targets. Admin/seller search fields still match their adjacent selectors; mobile text inputs stay at 16px to avoid focus zoom.

Motion follows the existing Emil tokens: 140ms press feedback, 180ms menus/backdrops and 240ms drawers with interruptible transform/opacity transitions. Keyboard-driven menus, drawers and search changes are immediate, without press transforms. Input modality changes update a DOM attribute only when the modality actually changes; they do not trigger application re-renders. Homepage reveals are shorter (240ms copy/sections, 320ms photography), with gentler movement. Reduced-motion variants and fine-pointer hover gates remain in place. Data refreshes do not trigger page-wide fades.

Validation for this frontend-only revision:
- All 27 frontend tests pass; lint and production build pass. Three added regressions cover draft-sort cancellation/reopening, atomic filter/sort application and sort preservation when clearing filters.
- Browser exercised category selection, curated savings (20%-off headphones first), Explore search, Browse everything, sort draft/cancel/reopen/apply, search preservation with category/sort, inverted-price validation, Escape/focus return and keyboard-versus-pointer transition durations.
- Measured widths 320/360/390/414/768/1024/1440/1920 have no document overflow in the catalog/header. Unfiltered products begin at 173px on 360–414px phones, 199px at 320px, 187px on the 768px tablet and 195px on desktop. Header targets measure 44px on phones/tablets and 36px on desktop. The header/toolbar remain at 0px/64px after mobile scrolling; the filter footer stays at the viewport bottom in 844×390 landscape.
- Sign-out/sign-in restored the saved two-unit bag. Sign-in, product purchase and checkout primary controls measure 44px; the desktop wishlist action is 36px. Quantity increase/decrease kept the checkout link label, black background and opacity of 1. Search Escape restores focus to its trigger without scrolling.
- Admin products search/select/Add controls all measure 36px. Product-editor Close/Cancel/Create actions were inspected and cancelled without modifying data. Shared seller product/inventory controls use the same classes. Narrow product-page and sign-in layouts have no measured overflow.
- No backend API/schema changes were made. The preceding checkout revision's 49 backend tests remain the latest backend run; they were not rerun for this UI-only change. Physical touch-device behavior and OS reduced-motion settings remain hardware checks; reduced-motion rules were inspected in code. Existing payment/email sandbox limitations below are unchanged.

Current screenshots: [catalog, desktop](screenshots/icon-filter-catalog-desktop.jpg) · [phone](screenshots/icon-filter-catalog-mobile.jpg) · [Explore, desktop](screenshots/explore-panel-desktop.jpg) · [phone](screenshots/explore-panel-mobile.jpg) · [Filter and sort, desktop](screenshots/filter-sort-desktop.jpg) · [phone](screenshots/filter-sort-mobile.jpg).

## Previous refinement: checkout, marketplace campaign and dashboard controls

Checkout now has a cohesive Delivery → Review → Payment flow: accessible saved-address radio cards, clear add/edit/delete actions, complete product names and seller information, unit quantities, backend line totals, a responsive order summary and a Cashfree preparation panel. Completed steps are revisitable; step changes move keyboard focus to the heading and keep progress below the measured persistent header. Mobile primary actions are 48px, secondary targets 44px. The summary is sticky on desktop and follows the step on phones. Help and Account remain available during checkout.

Checkout rejects a deleted/stale delivery selection, waits for verified bag data and blocks repeated payment submissions. It uses the latest saved address rather than an old selected snapshot. Incomplete payment-session responses cannot open a broken hosted-payment screen; the captured backend total remains authoritative. Address-refresh failures offer inline retry. Payment failures retain the address, items and current step, with a stable payment button. The payment-opening screen can retry the existing session or open order status; it no longer falls into an endless loading label. Confirmation, pending, failed and stock-conflict states share the new visual treatment while retaining existing payment polling and inventory behavior.

The homepage now presents Mercato as a marketplace across tech, fashion, home and beauty. Original generated still-life photography replaces the fashion-model campaign; the bundled WebP is 94,578 bytes (about 95 KB). Copy, category navigation, campaign CTA and page metadata reflect multiple sellers and product variety. Text and photography enter once; below-fold sections reveal once as they enter view. Motion uses transform/opacity, does not restart on featured-product refreshes, and is removed for reduced motion. Category hover effects require a fine pointer. No new animation dependency was added.

All six dashboard search locations (products, inventory, users, orders, carts and fulfillment) use the same explicit height: 40px desktop / 44px below 1024px. Inputs no longer add their own minimum height to wrapper padding. Search takes a full row on phones so it cannot collapse into a tiny field. Existing filters, clearing and search behavior are retained; search fields have accessible names and 16px mobile input text.

Validation on 18 October 2026:
- Frontend lint and production build pass; all 24 frontend tests pass. Four new regression cases cover canonical/deleted addresses, unverified/failed/empty bags, backend-authoritative payment handoff and malformed handoff responses.
- All 49 backend tests pass (zero failures/errors/skips), including mocked payment/reservation/concurrency coverage. Log: `backend/target/checkout-marketplace-tests.log` (ignored local output). No backend API or schema changes were needed.
- Browser exercised checkout delivery/review/payment, address add/select/delete, edit prefill, Escape dismissal/focus restoration, keyboard progression, step revisiting, real backend totals, gateway failure and retry, missing-session redirect, and incomplete payment-return recovery. Synthetic address data was used; the temporary extra address was removed.
- Homepage and checkout layouts were checked at 320, 360, 390, 414, 768, 1024, 1440 and 1920px, with an additional 844×390 landscape checkout check. No horizontal document overflow was measured; prominent actions remained 48px. Detailed desktop/phone visual checks and screenshots accompany the flows.
- Browser measured 40px desktop and 44px phone/tablet dashboard searches against adjacent selectors, tested product/user search and clearing, and inspected inventory/order/cart/fulfillment search fields. A 320px product search now spans its available 241px row.
- Homepage category-anchor navigation and featured savings sorting work; the savings collection correctly starts with the 20%-off headphones. Browser reported no errors/warnings during the final check.
- Bag quantity increase/decrease maintained the existing checkout link label, black background and opacity of 1; the badge and checkout returned the correct two-unit total of ₹47,510.10.

Current screenshots:
- [Marketplace campaign, desktop](screenshots/marketplace-home-desktop.jpg) · [Phone](screenshots/marketplace-home-mobile.jpg)
- [Delivery, desktop](screenshots/checkout-delivery-desktop.jpg) · [Phone](screenshots/checkout-delivery-mobile.jpg)
- [Review, desktop](screenshots/checkout-review-desktop.jpg) · [Phone](screenshots/checkout-review-mobile.jpg)
- [Payment, desktop](screenshots/checkout-payment-desktop.jpg) · [Phone](screenshots/checkout-payment-mobile.jpg)
- [Recoverable gateway failure](screenshots/checkout-payment-error-desktop.jpg) · [Incomplete return, phone](screenshots/checkout-payment-recovery-mobile.jpg)
- [Dashboard search, desktop](screenshots/dashboard-search-desktop.jpg) · [Phone](screenshots/dashboard-search-mobile.jpg)

The local demo deliberately points Cashfree at a closed localhost port. A successful hosted checkout and provider-side method selection/retry still require a configured sandbox. Success/pending/stock-conflict visual branches were reviewed in code and their payment transitions have backend mock coverage; they were not fabricated as paid Browser orders. Reduced-motion CSS and touch safeguards were inspected; OS settings, physical phones and controlled network throttling remain external checks. Startup and production boundaries below are unchanged.

## Previous refinement: compact navigation and Apply-based filters

The utility strip is removed. One persistent header now contains Help and an Account menu with orders, sign-out and role-appropriate dashboards (sign-in/register for guests). The entire header stays at the top: 72px on desktop and 64px below 1024px, including checkout. Narrow phones expose search inside the navigation menu. Expanded search is included in measured sticky offsets.

The catalog title/count and filter/sort controls share a compact toolbar. The introductory sentence and persistent sidebar are removed. Filters open only on request; draft edits do not affect results until Apply. Apply validates prices, commits one URL update, resets pagination, closes the drawer and restores focus. Cancel/Escape/backdrop discard edits; Clear inside the drawer remains a draft change. Search and sorting are preserved. Applied chips remain removable. Existing results survive refreshes and show retryable inline errors.

Explicit control sizes replace color-dependent sizing: 40px desktop actions, 44px mobile targets and 48px prominent forms/checkout. Small icon actions keep 44px mobile hit targets. Shared native selectors use a centered decorative chevron with reserved right padding; keyboard/platform option behavior is retained. Authentication fields are 48px instead of adding excessive inner padding. Product-title links fill their wrapped title area, fixing missed taps between lines. Seller product form actions use concise Create/Save labels with full accessible names.

Browser checks covered 320, 360, 390, 414, 768, 1024, 1440 and 1920px widths and 844×390 landscape. No horizontal document overflow was found in the tested catalog, header, product, bag, checkout, account and management screens. Wide tables remain internally scrollable. The first product row begins at 195px on 1440px desktop, 205px on 360/390px phones, and 231px on a 320px phone. Native sort controls measure 40px desktop/44px mobile with aligned arrow centers; account/checkout form actions measure 48px. Landscape filter options scroll while Apply stays visible (44px high, bottom 374px in a 390px viewport).

Interactive checks include draft isolation, Cancel/reopen, negative/inverted prices, Apply/close/focus restoration, sort preservation, clear-draft cancellation, menu dismissal, Help/account reachability after scrolling and at checkout, guest login/cart merge, mobile gallery/add-to-bag, quantity changes without checkout flashing, address save/checkout review, profile edit, seller catalogue/history and management dialogs/selectors. Demo data resets on API restart. Local servers were restarted as hidden background processes after the earlier sessions stopped; reload old Browser tabs to replace stale development assets.

Five new filter regressions bring the frontend suite to **20 passing tests**; lint and production build also pass. The full backend suite was rerun at the end: **49 tests pass, zero failures/errors/skips**. Backend/API/schema are unchanged in this refinement. Physical touch hardware, OS reduced-motion behavior and external-service checks remain the release gates documented below.

- [Compact desktop catalog](screenshots/compact-catalog-desktop.jpg)
- [Compact mobile catalog](screenshots/compact-catalog-mobile.jpg)
- [Apply-based mobile filters](screenshots/apply-filter-mobile.jpg)
- [Persistent account menu while scrolled](screenshots/persistent-account-desktop.jpg)
- [Compact mobile login](screenshots/compact-login-mobile.jpg)

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

- Frontend: lint clean, production build successful, 20 regression tests pass.
- Backend: 49 tests pass, zero failures/errors/skips. Includes database/security flows, rich product persistence and legacy updates, ownership, invalid nested input, migration idempotency, demo credential isolation, duplicate phone conflicts, role-input validation, upload validation, inventory reservations, payment callbacks and finalization concurrency.
- Browser desktop: home/navigation/search, catalog filters/sorting, PDP/gallery, bag quantity and badge, guest sign-in/cart merge, failed sign-in, rich product create/edit, inventory adjustment, category create/edit, admin overview/users/roles-dialog/orders/carts, profile edit, address validation/save, checkout delivery/review/payment-provider failure, wishlist add/remove, and seller overview.
- Local product creation retained ordered highlights/specifications in the editor; a stock delta changed 12 to 14 without removing the product table during refresh. Role editing was inspected and cancelled; no extra privileges were granted.
- Checkout retained its selected address and bag when the disabled demo payment provider failed. Actual totals included the server platform fee.
- Production preview at port 5175: mobile registration/validation, missing reset and verification tokens, account-status screen, filtered price sorting, navigation/Escape, gallery, quantity changes, sign-in/guest merge, delivery address save and checkout review. A two-unit bag showed ₹2,698.20 plus the backend ₹12 fee, total ₹2,710.20.
- Desktop 1440×1000: three product columns (about 336px each). Mobile 390×844: two columns, with document width 375px; PDP, bag, checkout and admin directory did not overflow horizontally. Wide management tables scroll inside their own containers.
- Seller catalogue/inventory, active/history fulfillment tabs and search; admin user drawer via Enter and Escape; signed-in password recovery; incomplete payment return and order recovery; help disclosure and missing-page recovery were exercised in Browser.

## Screenshots and control coverage

### Historical follow-up: consistent controls and stable updates

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
