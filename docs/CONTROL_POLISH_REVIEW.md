# User details, custom dropdowns and navigation — 20 October 2026

Local `stg` UI follow-up. Existing README/local configuration edits remain excluded; no remote push or `main` change.

## Changes

Clicking a user row is intended to show that user's details. The drawer's missing layout styles left it in document flow while its fixed backdrop dimmed the page. It now has a fixed, opaque, right-hand panel, bounded width, independently scrolling content and safe-area padding. The user button explicitly says View details and supports native keyboard activation. Escape/close restores focus. Admin accounts remain read-only; role editing is available only for eligible non-admin accounts.

The shared `SelectControl` now renders Headless UI Listbox buttons, options and an anchored portal, replacing native OS selects across catalog sorting, dashboard filters, listing categories, address states, registration and promotion controls. Menus support arrow keys, Home/End, type-ahead, Enter, Escape, outside dismissal, disabled options, selected checks and focus restoration. Long options wrap inside a bounded scrolling menu. Portals sit above drawers/modals and use nonmodal popup behavior, so choosing an option does not dismiss its owning dialog. Registration uses React Hook Form Controllers so selection, conditional tax fields, validation and reset stay connected. No native `<select>` remains in application components.

Menus use 180ms transform/opacity transitions and drawers 240ms; keyboard operation and reduced motion skip those transitions. Mobile options retain 44px targets and 16px text. The seller-registration grid now uses a shrinkable single column, fixing overflow when the longest tax option is selected at 320px.

All products is a text-only link on every screen size and stays on one line. Its former grid icon is removed. Search is always available in the persistent header, including checkout. Reporting timeframe submission is labeled Apply.

## Verification

- Frontend **37 tests pass**, lint passes, final production build passes. Final entry bundle: 464.58 kB / 153.72 kB gzip. Logs: ignored `backend/target/control-polish-{tests,lint,build}.log`.
- Backend code/schema are unchanged in this follow-up. The preceding full backend suite passed 87 tests; it was not repeated for these UI-only changes.
- Browser: user details opened with a visible white 440px desktop panel; Escape returned focus to the selected user button. At 320px the panel exactly filled the width and retained its scrolling content. Admin details exposed no Edit control. [Desktop drawer](screenshots/controls-user-details-desktop.jpg), [mobile drawer](screenshots/controls-user-details-mobile.jpg).
- Browser: custom role menu rendered DOM options and selected User with End/Enter, filtering to two users. All Roles restored the full list. [Desktop custom dropdown](screenshots/controls-custom-select-desktop.jpg).
- Browser: catalog sort opened above the mobile filter drawer, with all menu bounds within the 320×740 viewport. Choosing Price: Low → High updated the draft; Apply filters committed `sortBy=sellingPrice&sortingOrder=asc&pageNumber=0`. No native select was present. [Mobile custom sort](screenshots/controls-custom-select-mobile.jpg).
- Browser: dashboard category selection filtered the catalog to Fashion; listing-category selection worked inside its modal; order-status options opened and dismissed with Escape. No listing or order mutation was submitted.
- Browser: promotion product and seven-day duration selections worked; no campaign was booked. Address-state End/Enter selected the final state; Escape closed only the menu and returned focus to its trigger. Address changes were cancelled, leaving saved data intact. A screenshot confirmed the owning dialog remained visible after selection.
- Browser: seller registration selected Limited liability partnership and GST portal enrolled, unregistered, revealing the enrolment field. The final grid measured `scrollWidth == clientWidth` at 320, 390, 768 and 1440px, including long selections. No account was created. [Seller registration](screenshots/controls-seller-registration-mobile.jpg).
- Browser: at 320px the checkout header had zero catalog icons, one 18px text line for All products, a 44px search target, and document width 305px matching the available client width. Search opened and Escape dismissed it on checkout. Help, Account and bag remained available. [Checkout header](screenshots/controls-checkout-header-mobile.jpg).
- Browser console showed no warning/error entries during these checks. Temporary viewport overrides were reset. `git diff --check` passes.

The supplied recording was reviewed and showed a Windows-native category picker. Verification used the production preview at `http://127.0.0.1:5175` and disposable demo data. Existing demo credentials remain `reviewer` / `ReviewOnly!2026`. Phone checks used viewport emulation; physical iOS/Android keyboard, safe-area and touch behavior still need hardware verification. No live gateway, email, carrier or settlement calls were made.
