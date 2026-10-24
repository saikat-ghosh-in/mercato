# Capped delivery and seller funding — 20 October 2026

Local `stg` follow-up. Remote branches and `main` are unchanged. Existing README and local configuration edits are excluded.

## Result

- Standard and grouped delivery cost at most **₹250 for the whole bag**, including bulky-item charges. The existing ₹50 below ₹999 / free from ₹999 base charge remains, subject to that cap.
- Sellers can select **Cover standard delivery** in the listing editor. The review step repeats this choice; the product page shows free standard delivery for that item. Seller edits continue through the existing approval process. There is no listing fee.
- Covered item shares are charged to the seller's earnings when shipped. Standard/grouped shipping is free to the customer when all chargeable shares are covered. Mixed bags retain only uncovered shares.
- Expedited adds **₹49 once per bag** to the customer's standard charge: ₹49 for a fully covered bag, at most ₹299 otherwise. Platform and handling charges remain separately stated and are not waived by shipping coverage.
- Grouped delivery explains its longer estimate: “Extra time to group deliveries, aiming for fewer trips and lower carbon emissions.” This expresses the purpose of grouped dispatch, without claiming measured savings.

## Pricing, snapshots and accounting

The server calculates the base charge plus package surcharges, then caps the whole standard amount before applying seller funding. Each line receives its bulky-cost share plus a merchandise-proportional base share. The capped total is allocated in exact paise using largest remainders; ties use product IDs. Customer and seller shares add back to the same capped amount, including mixed bags. Changing quantities or delivery method reprices the bag; client-supplied product flags and totals cannot waive charges.

Product requests accept optional `sellerPaysShipping`. Omission preserves the previous value; an explicit `false` turns coverage off. Existing listings default to false. Product responses expose the preference. The immutable order line captures the entire line's `sellerDeliveryCharge` at checkout. Later listing edits do not rewrite that order.

Finance dashboards show seller-paid delivery separately from commission and promotions and deduct it from known net earnings. The deduction is proportional to shipped quantity, rounded to paise; unshipped/cancelled units have no deduction. Successful full refunds reverse it under this draft policy. Promotion funding uses the same shipped deduction. Historical orders with a null contribution remain zero; no historical shipping liability is invented. Customer delivery collections and seller contributions remain distinct pass-through amounts, outside platform profit. Partial refunds without line attribution retain the existing reconciliation hold.

## Deployment and local review

Back up PostgreSQL and stop the API. Apply earlier marketplace migrations and [package pricing](sql/2026-10-package-pricing.sql), then [seller-funded delivery](sql/2026-10-seller-delivery.sql), before deploying the matching API/frontend. The additive script creates `products.seller_pays_shipping` (false for existing listings) and nullable `order_lines_snapshot.seller_delivery_charge`. Repeat execution is safe; historical rows retain their original terms. These DDL statements have not been run against a live PostgreSQL service in this review.

Configuration: `cart.delivery.standard-cap=250` and `cart.delivery.expedited-surcharge=49`. Startup rejects negative delivery charges or a standard cap above ₹250. Existing package pricing defaults remain unchanged; ₹550 is now only an uncapped intermediate package calculation, never the customer's standard bag charge.

From `backend`, run `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`. From `frontend`, run `npm install`, `npm run build`, then `npm run preview -- --host 127.0.0.1 --port 5175 --strictPort`. Open `http://127.0.0.1:5175`. The isolated demo admin remains `reviewer` / `ReviewOnly!2026`. Demo data resets on API restart. Never enable the demo profile in production. The current preview has one synthetic chair with coverage enabled and a synthetic default address, ready on Review & pay; no payment was initiated.

## Verification

- **Backend: 87 tests pass, zero failures/errors.** Coverage includes exact paise allocation, whole-bag cap across quantity changes, mixed coverage, base-charge splitting, explicit disable/legacy omission, persistence, ownership/validation, server rejection of spoofed coverage/price, order snapshots, partial shipment, full refund and promotion-budget consistency. The existing authorization, reservations, payment and commission-notice regressions also pass.
- **Frontend: all 37 tests, lint and production build pass.** Coverage includes form payload/default/disable behavior alongside existing bag and filter regressions. Final entry bundle: 464.59 kB / 153.71 kB gzip.
- Browser: an uncovered bulky chair quoted Standard/Grouped ₹250 and Expedited ₹299. Increasing to two chairs retained the ₹250 cap. [Desktop cap evidence](screenshots/seller-delivery-cap-desktop.jpg).
- Browser: enabled coverage through the catalog editor, checked its review summary, saved it, and opened the public product page. The free-standard offer persisted. Adding that item produced Standard/Grouped free and Expedited ₹49. [Mobile bag](screenshots/seller-delivery-covered-mobile.jpg).
- Browser: selected grouped delivery, continued to checkout, saved a disposable address, and confirmed the default address and selected delivery on Review & pay. Total ₹11,061.15 = merchandise ₹11,049.15 + platform fee ₹12. [Grouped checkout](screenshots/seller-delivery-checkout-desktop.jpg).
- Browser: changed checkout to Expedited; both payment amount and summary became ₹11,110.15, with delivery ₹49. The payment control remained present. [Expedited checkout](screenshots/seller-delivery-expedited-desktop.jpg).
- Browser: bag measured at 320/390px and checkout at 320/768/1440px, without horizontal document overflow. Grouped copy wraps, prices remain visible, and selection persists between bag and checkout. Temporary viewport overrides were reset. These are browser viewport checks, not physical-device certification.

Logs are local ignored artifacts: `backend/target/seller-delivery-backend-verified.log`, `seller-delivery-frontend-tests.log`, `seller-delivery-lint.log`, and `seller-delivery-build.log`.

Live carrier pricing/serviceability, bank settlement, physical delivery, real gateway payments, email delivery and live PostgreSQL migration remain external-service limitations. Financial tests use disposable data and mocked payment outcomes. Existing seller operating policy remains a draft; carrier cost reconciliation and remittance operations must support these disclosed deductions before commercial launch.
