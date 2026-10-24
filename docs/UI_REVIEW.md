# UI consistency and responsive review

20 October 2026. Implementation in `frontend/src`, the current application in this monorepo. The separate legacy `mercato-frontend` copy received only the requested save-icon removal.

## Changes

- Shared account headings, wrapping action rows, readable form labels, consistent control shapes, and quieter surfaces.
- Removed floppy disk icons from category, address, profile, and stock-update actions in both frontend copies.
- Removed unused campaign template data, fabricated marketing counts and delivery promises, decorative emoji, purple status variants, and redundant promotional copy in account, seller, payment, and management flows.
- Home headline stays within two lines at phone, tablet, and desktop widths. Removed the duplicate promotional closing section.
- Account headers and address actions wrap independently. Long account values and product descriptions can wrap without stretching their containers.
- Category rows reserve space for edit/delete controls. Dashboard product tables retain readable name columns and scroll within their own container on narrow screens. Accounting figures align to the right.
- Admin bag details resolve product names/images, use readable fee labels, and stack line totals on phones. Admin order details no longer repeat a raw status code in a cramped badge.
- Supply mode labels remain readable at 320px. Product editor steps, gallery thumbnails, delivery options, financial summaries, and narrow dialogs fit their available space.
- Fulfillment cards support native button keyboard activation. Catalogue/inventory and fulfillment tabs expose their selected state.
- Shared route, dashboard-tab, checkout-step, and product-editor transitions use transform/opacity for 180ms. Keyboard navigation stays immediate; reduced motion uses a 100ms fade without translation. Dialogs share backdrop and panel entrance treatment. Removed local press-scale utilities so shared motion preferences control feedback.

## Verification

511 browser measurements across 73 named page/tab/dialog states. Widths: 320, 390, 768, 844, 1024, 1440, and 1920px. The 844px viewport used a 390px height for landscape checks. No measured horizontal document overflow, unintended elements outside the viewport, or ErrorBoundary crashes.

After the final detail-screen fixes, 14 additional measurements across all seven widths verified resolved product names, readable charge labels, no raw order status code, no horizontal overflow or clipped visible text, and zero runtime exceptions (525 responsive measurements in total).

Covered the storefront, catalogue, product detail, empty/populated bag, all authentication and recovery routes, both registration account types, profile/edit/addresses/saved items, checkout address and review steps, payment recovery/confirmation aliases, orders and missing-order recovery, Help, policies, and 404.

Covered all eight administration tabs and all five seller tabs; catalogue/inventory and active/history subtabs; pending seller application; category, address, listing creation/editing, inventory, and application dialogs; all three product editor steps; user drawer, admin order/cart details, timeframe popover, filter drawer, and mobile dashboard navigation. Overlay dismissal and editor navigation were exercised without submitting approvals, access changes, campaigns, or payments.

Motion was sampled after the router committed the new view: pointer tab selection ran the 180ms transition, Enter selected the next tab with zero content animations, and reduced motion produced opacity keyframes only. No runtime exceptions occurred in this motion check.

- ESLint passes.
- All 43 existing frontend tests pass.
- Production Vite build passes.
- Design source lint: 0 failures. Remaining heuristic warnings cover overlay shadows, IDs in monospace, loading skeletons, existing breadcrumbs/sponsored disclosures, and punctuation. The neutral photo scrim is deliberately retained for label contrast.
- Changed save-icon files in the legacy copy parse successfully. That copy has no installed dependencies and was not rebuilt or given the full responsive pass.

Raw measurements: [validation.json](screenshots/ui-review/validation.json). Representative screenshots:

- [Home, desktop](screenshots/ui-review/home-1440.jpg)
- [Profile editor, 320px](screenshots/ui-review/edit-profile-320.jpg)
- [Checkout, 320px](screenshots/ui-review/checkout-review-320.jpg)
- [Administration overview](screenshots/ui-review/admin-overview-1440.jpg)
- [Categories, 320px](screenshots/ui-review/admin-categories-320.jpg)
- [Supply update, 320px](screenshots/ui-review/supply-dialog-320.jpg)
- [Product dialog, 320px](screenshots/ui-review/product-dialog-320.jpg)
- [Product specifications, 320px](screenshots/ui-review/product-edit-specifications-320.jpg)
- [Promotions, 320px](screenshots/ui-review/seller-promotions-320.jpg)
- [Dashboard navigation, 320px](screenshots/ui-review/mobile-dashboard-menu-320.jpg)
- [Admin bag details, 320px](screenshots/ui-review/admin-carts-detail-320.jpg)
- [Admin order details, 320px](screenshots/ui-review/admin-orders-detail-320.jpg)

## Limits

Checks used disposable local H2 data and a headless desktop browser. Physical phones, Safari/Firefox, real email links, and hosted Cashfree payment outcomes were not verified. The seeded sale has no shopper owner, so populated customer order details and successful provider returns were reviewed in source, with admin order details and customer recovery states exercised in the browser. The checks cover the listed views and fixtures, not every possible data permutation.
