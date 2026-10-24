# Navigation, notices and field refinement — 19 October 2026

Follow-up to [the marketplace journey review](MARKETPLACE_REVIEW.md). Frontend-only changes on local `stg`; no API/schema changes or remote operations. The existing README and local configuration edits are preserved.

## Delivered

- Account has one seller destination: **Seller dashboard**. Removed the separate Seller notices menu item and dashboard banner. An unread badge appears on the dashboard's Seller notices tab, with the full accessible count. Reading a notice clears the badge immediately. One dashboard-owned inbox supplies the tab and badge; older refresh responses cannot restore a read badge. Polling/focus refreshes run only for eligible dashboard users.
- Both financial overviews show a compact editable date-range button beneath the heading. The From/Through form opens only on request. Native date input events now retain edits correctly. Draft edits do not request a report; Apply validates and commits together, while Cancel/Escape discard drafts. Invalid/missing/impossible/reversed/future/oversized ranges stay editable with an inline error. Loaded report content stays visible during refreshes, and focus returns to the range button. Reports retain their existing UTC date contract.
- Shortened long placeholders across fulfillment/admin searches, product/image controls, address, recovery and profile forms. Shared dashboard searches have enough input width and wrap onto their own row on phones. Rich descriptions stay in accessible labels/help rather than oversized placeholders.
- Removed redundant country/currency labels from the footer, financial overview and address forms/cards/checkout. Prices retain ₹. The existing fixed country value and currency identifiers remain internal API data; address saving and pricing contracts are preserved.
- Added tap/keyboard-accessible information buttons to seller business fields: store/legal names, support email, categories, pickup address/state/pincode, business structure, PAN and GST/enrolment. Help popovers stay bounded to 300px and the viewport, close with Escape/outside click, and do not submit the form. Reduced motion and keyboard activation use immediate transitions.
- Added a persistent **All products** header link on storefront, account, authentication, dashboard and checkout routes. On small phones the header uses the M mark and compact labels, retaining Help, Account, Bag and search access without overflow. The footer keeps the wordmark.

## Verification

- `npm run lint`: pass, no warnings/errors.
- `npm test`: **34 passing tests**, including new coverage for UTC default periods, valid leap dates and malformed/reversed/future/oversized timeframes.
- `npm run build`: pass. Entry bundle approximately 464.59 kB / 153.71 kB gzip. Logs: ignored `backend/target/refinement-{lint,tests,build}.log`.
- Backend source unchanged in this follow-up. The preceding delivery's 71 passing backend regressions remain the backend baseline; they were not redundantly rerun for these interface edits.
- Browser: date range changed from 22 July–19 October to 1–19 October; input persisted, the report updated, and keyboard focus returned to the edited range button. An inverted range remained open with an inline error. At 320px the editor measured 288px wide and both inputs 248px, with no clipped fields.
- Browser: a temporary local 5%→6% proposal produced a Seller notices badge of 1. Mark as read immediately removed it. Only that test proposal was cancelled afterward; the current rate remains 5%. The Account menu has no Seller notices destination. The test used the disposable review account and unconfigured demo mail service; no real seller email was sent.
- Browser: fulfillment search/header measured at **320/360/390/414/768/1024/1440/1920px**, with no document overflow. The complete `ID or customer` placeholder has 235px of input width at 320px. All products remains available at every width; mobile touch controls remain reachable.
- Browser: registration at 320/390/768/1024/1440px has no document overflow. Keyboard/tap PAN help opens without submitting. At 390px the panel is 300px wide, entirely within the viewport. Help for all 11 business fields is available.
- Browser: All products navigates directly from registration to the catalog and is present during checkout. A synthetic address saved successfully without a visible Country field, became the default and appeared in combined Review & pay. Catalog and checkout visible text contain no `India`/`INR` labels, while ₹ prices and backend totals remain visible.

Screenshots: [overview](screenshots/refinement-overview-desktop.jpg), [desktop date editor](screenshots/refinement-timeframe-desktop.jpg), [320px date editor](screenshots/refinement-timeframe-mobile.jpg), [fulfillment search/header](screenshots/refinement-fulfillment-mobile.jpg), [registration help](screenshots/refinement-registration-help-mobile.jpg), [notice badge](screenshots/refinement-seller-notice-badge.jpg), [checkout](screenshots/refinement-checkout-mobile.jpg).

No physical-device, screen-reader or operating-system reduced-motion certification is implied by viewport emulation. Existing live-service and production deployment limitations remain documented in the marketplace review.
