# Browser acceptance — 24 October 2026

Environment: isolated loopback demo, synthetic products/accounts only, in-memory H2, live payment credentials disabled. Browser checks used the in-app Browser. PostgreSQL transaction tests are separate from this UI environment. No real payment or external email was submitted.

## Verified interactions

- Catalog: mobile two-column grid, compact persistent header/All products, product add-to-bag action, filter drawer closed initially, draft category discarded by Cancel, reopening reflects the applied URL, Apply updates the URL once and closes the drawer with focus on the filter control.
- Bag: guest item added; increase from one to two units updates the accessible badge and backend totals. The checkout link remains present. Delivery changes update charges without replacing the review content.
- Authentication: guest checkout redirects to sign-in; synthetic shopper login returns to checkout and merges the two-unit guest bag. Logout clears private state and the badge. A second authenticated tab loses private wishlist membership after logout from the other tab.
- Checkout: address dialog opens correctly; saving a synthetic first address selects it as default and advances to review/payment. Expedited delivery updates the server total from ₹1,810 to ₹1,859 for the exercised bag. Unavailable local gateway produces an inline error and preserves the address, items and editable controls. Search, Help and Account stay available.
- Account: profile details initialize correctly; editing the synthetic first name saves and refreshes the profile. Account destinations remain accessible on mobile.
- Management: administrator and seller overviews load their financial breakdowns; mobile dashboard navigation opens. User activity shows the shopper's current bag/order count, while administrator accounts remain read-only. The custom role listbox opens and Escape dismisses it. The user-detail dialog is above its backdrop.
- Product details: gallery advances, rich sections are present, and wishlist membership loads before toggling. The successful toggle changes to Remove from wishlist. Cross-tab logout clears that membership.
- Final packaged release with demo MFA bypass disabled: a transient bootstrap error recovers through Try again; administrator sign-in reaches Set up your authenticator, and direct administrator navigation redirects back to MFA. Mobile layout/navigation was refined and captured in `admin-mfa-mobile.jpg`. No new authenticator/recovery credential was entered through Browser; enrollment, challenge/replay, recovery and replacement consumption are covered by backend tests.
- Widths: 320×780 and 390×844 exercised; measured document/body width did not exceed the viewport on checkout or administrator overview. Narrow catalog screenshot shows two usable columns and unwrapped All products navigation. Wide desktop checkout was also captured.

Screenshots are in `docs/screenshots/hardening/`: `catalog-320.jpg`, `checkout-recovery-mobile.jpg`, `checkout-recovery-desktop.jpg`, `admin-user-mobile.jpg`. These show the working tree's preserved UI work together with the hardening changes. A separate hardening-only frontend export is also tested/linted/built before staging; unrelated UI work is not silently included in the commits.

## Limits and remaining external acceptance

Actual Cashfree checkout/capture/refund and SMTP delivery require configured sandbox services. The disabled local gateway test verifies failure retention, not a completed payment. HTTPS Secure-cookie, deployed Netlify raw-byte/IP-forgery/Edge-failure checks, Caddy renewal, backup restoration and delivered dead-man alerts cannot be certified by loopback tests. PostgreSQL 14.24 Testcontainers remains a mandatory CI gate; the explicitly selected local PostgreSQL 18.1 run does not replace it. Real phone hardware and OS-specific keyboards were not exercised.
