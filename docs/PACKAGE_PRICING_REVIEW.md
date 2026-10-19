# Delivery, product measurements and commission — 19 October 2026

Frontend and backend follow-up on local `stg`. No remote branch or `main` changes. The pre-existing README and local configuration edits remain excluded.

## Customer-facing changes

- Delivery choices show method, estimated time and the server price. Removed repeated thresholds, shipping operations and environmental disclaimers from the selector. Grouped delivery makes no environmental savings claim.
- Only bags with a bulky-item surcharge show the short disclosure: “Includes ₹… for bulky items.” The same authoritative delivery price feeds cart, checkout and the order snapshot. Checkout removes repetitive preparation/charge explanations and hides zero-value optional fee rows while retaining all actual charges.
- Product details show packed weight and package dimensions when supplied. No fabricated measurements are assigned to existing production listings.
- Dropdowns share native keyboard behavior, centered decorative chevrons, reserved arrow space and consistent borders. Listing and promotion selectors fill their field width. Enabled actions have pointer cursors; disabled/pending controls retain their appropriate cursor.

## Product measurements and delivery rules

New listing requests require packed measurements for one sellable unit: `weightKg` (0.001–1,000, up to 3 decimals), and `lengthCm`, `widthCm`, `heightCm` (0.01–600, up to 2 decimals). Partial groups, zero/negative/out-of-range and over-precise values are rejected. Legacy updates omitting all four preserve stored values. Existing unmeasured listings retain their previous delivery pricing until verified measurements are backfilled.

Seller/admin editors, listing review, request/response DTOs and persistence include these fields. Catalogue updates do not alter inventory. Seller edits retain approval requirements. The review step repeats the submitted measurements. Invalid inputs retain entered values.

Configurable staging business defaults:

- Chargeable kg = max(actual packed kg, length × width × height / 5,000).
- First 5 kg included. Each started additional 5 kg costs ₹50 per item.
- A longest side strictly above 100 cm has a minimum ₹100 surcharge per item. The greater weight/size charge applies, without adding both.
- Multiply per-item surcharge by quantity. Add to every delivery method, even above the free-base-delivery threshold. Expedited adds ₹99 once per bag. Existing ₹50 below ₹999 / free from ₹999 base policy remains.
- Examples: 5 kg / 100 × 5 × 5 cm = no surcharge; 6 kg / 20 × 20 × 20 cm = ₹50; 1 kg / 101 × 5 × 5 cm = ₹100; demo chair 12 kg / 110 × 60 × 45 cm = ₹550.

Properties (all optional, shown with defaults): `cart.delivery.included-weight-kg=5`, `weight-block-kg=5`, `weight-block-charge=50`, `max-standard-side-cm=100`, `oversize-minimum=100`, `volumetric-divisor=5000` (each uses the `cart.delivery.` prefix). Invalid positive-threshold/non-negative-charge configuration fails at startup. These are Mercato business defaults, not verified carrier quotes; carrier serviceability remains a launch integration.

## Commission

New baseline: **0.5%, capped at ₹500 per item sold**. Calculate and round the percentage fee per unit to two decimals, cap it, then multiply by fulfilled quantity. ₹1,000 item → ₹5; ₹200,000 item → ₹500. Two such items → ₹1,000. Unshipped/cancelled units do not earn commission; successful full refunds reverse it.

New orders capture both rate and cap. Historical order rates and aggregate rounding remain unchanged; a null historical cap remains uncapped. Finance totals, per-line explanations and promotion funding respect the same captured terms. SQL funding aggregation avoids loading the full seller sales history into application memory.

The one-time revision-2 baseline migration lowers an old rate to at most 0.5%; it never raises a lower existing rate or resets later administrator decisions on restart. Existing pending proposals retain their notices and status. Subsequent admin changes still require the existing explanation, exact confirmation, single-pending-change guard, email/app notices and full 60-day period. The ₹500 cap remains fixed across those rate changes.

## Deployment

Back up PostgreSQL and stop the API. Apply prior marketplace migrations, then [2026-10-package-pricing.sql](sql/2026-10-package-pricing.sql), before deploying the matching backend/frontend. The new migration is repeatable and preserves historical snapshots. It adds a database check for complete, bounded package measurements. Do not backfill guessed sizes or alter historical order rows.

Local review: from `backend`, run `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`; build frontend and run `npm run preview -- --host 127.0.0.1 --port 5175 --strictPort`. Demo credentials remain `reviewer` / `ReviewOnly!2026`. Demo data is disposable and resets at API restart. Production never enables `demo`.

## Verification

- Frontend: lint passes; **36 tests pass**; production build passes.
- Backend: **81 tests pass**, including weight/size boundaries, volumetric pricing, quantity/method repricing, client price spoofing, persisted measurements, legacy update preservation, validation/ownership, capped order snapshots, finance/promotion funding consistency, full refunds and baseline migration idempotence. Existing authorization, inventory, payment and 60-day notification regressions remain green.
- PostgreSQL-specific DDL is supplied for deployment; local integration tests use H2 in PostgreSQL mode. A live PostgreSQL migration and carrier/payment/email integrations are not certified by this local run.
- Browser: the demo chair produced ₹550 standard / ₹649 expedited delivery for one unit, and ₹1,100 / ₹1,199 for two. Switching methods and decreasing quantity restored the correct server total, without removing the checkout control during updates. Only the conditional bulky-fee disclosure is shown.
- Browser: a new synthetic listing saved 6.125 kg / 30 × 20 × 15 cm, and reopening the editor and admin review preserved all four measurements. New listing remained pending approval. The customer chair PDP displayed 12 kg / 110 × 60 × 45 cm.
- Browser: listing dialogs at 320/390/768/1440px fit without horizontal overflow. Header/actions remain on-screen; invalid negative weight shows an inline message inside the visible dialog, retaining entered values. Phone form inputs use 16px text. Fixed nested scrolling that previously allowed a focused field to move the whole dialog off-screen.
- Browser: native category and catalog-sort arrows have 12px right inset, zero vertical-center error and 38px reserved input padding. Sorting from the mobile drawer applies the URL atomically and closes it. Enabled action cursor checks pass on bag, listing editor and catalog.
- Browser: inventory product selection now has a full-width mobile search, searchable/wrapping options and a bounded 300px list. At 320px the list remained inside the screen (top ~470, bottom ~770); keyboard search/selection and Escape dismissal work. No stock update was submitted.
- Browser: admin commission settings show 0.5%, the ₹500 per-item cap and all existing 60-day notice safeguards. Synthetic baseline sales retain their original captured 5% rate.
- API restart restored a clean disposable demo after verification; synthetic test listings/addresses/carts do not survive restart.

Test logs are ignored local artifacts in `backend/target/package-*.log`.

Final checkout was rechecked at 320/390/768/1440px after restarting the final API: no document or delivery-card overflow; default address, ₹550 delivery and ₹11,611.15 total remain consistent. The local preview retains a synthetic default address and one chair for review; no payment was initiated.

Screenshots: [final checkout](screenshots/package-checkout-desktop.jpg), [delivery desktop](screenshots/package-delivery-desktop.jpg), [delivery mobile](screenshots/package-delivery-mobile.jpg), [package editor](screenshots/package-editor-mobile.jpg), [visible validation](screenshots/package-validation-mobile.jpg), [inventory dropdown](screenshots/package-inventory-mobile.jpg), [commission](screenshots/package-commission-desktop.jpg). Mobile checks used viewport emulation, not physical-device certification.
