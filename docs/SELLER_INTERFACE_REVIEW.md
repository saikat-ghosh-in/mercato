# Seller dashboard alignment and navigation arrows

Local `stg` follow-up, 20 October 2026.

## Interface changes

Admin and seller now use one `DashboardFrame`: the same 24px heading, content width, spacing, neutral selected tabs, 16px navigation glyphs and folded mobile navigation. Desktop tabs are 36px high and mobile targets are 44px. Keyboard selection closes the mobile navigation and moves focus to the dashboard heading. Seller notice counts retain their full accessible count and only appear when there are unread notices.

Overview, catalog, perpetual inventory, fulfillment, promotions and notices keep their existing functions. Pending/rejected applicants still see the onboarding status screen; selling suspension still keeps its existing explanation and restrictions. Shared metric widgets now use plain labels and figures instead of coloured icon tiles. Table headings use the same quiet sentence-case treatment in both dashboards. Finance amounts, captured commission rates and existing calculations are unchanged.

The supplied 3.67-second video was inspected as a visual reference. Its small northeast arrow is muted at rest and darkens with the hovered card. `ActionArrow` applies that shape consistently to navigation rows, account destinations, discovery links, related-product links, login destinations and payment-provider navigation. Card borders become slightly stronger on hover. Search submission and back/next checkout-step arrows retain their directional meaning. Decorative arrows are hidden from assistive technology and have no separate focus or hit target. Dark buttons retain white arrows.

## Design and motion

Continue the existing **Achromatic ledger** direction (seed **786952345**) from the [Taste frontend skill](../../.agents/skills/design-taste-frontend/SKILL.md), with Mercato's palette, DM Sans, mark and marketplace data. No new direction was drawn because the requested target was the approved admin interface. Emil's guidance informs restrained feedback: 140ms presses and 180ms arrow colour feedback, suitable-pointer hover rules, immediate keyboard feedback and reduced-motion overrides. No page entrance or data-refresh animation was added.

The seller screen answers **what did this store earn and what needs attention?** The account/discovery cards answer **where can I go next?** Figures continue to come from the backend. The visible demo order carries its original captured commission rate; this UI change does not reprice it.

The visual review addressed three defects: the seller-only oversized heading/decorative store chip, drifting tab sizing/selection styles, and mixed arrow shapes/hover treatments. The existing mobile table scroll frames and substantive finance breakdown remain. Zero counts stay zero; there are no new invented metrics or charts.

## Validation

- Frontend: **43 tests pass**, lint and production build pass. Two new regression tests cover deep-link fallback and preservation of unrelated URL parameters without mutating the prior location.
- Taste source checks for the shared dashboard frame, stylesheet and arrow: **0 failures, 0 warnings**. Rendered checks used Browser; the separate skill browser launcher was not used.
- Browser at **1440×960**: all five seller tabs, catalog/inventory switching and product form open/dismiss. Admin/seller headings both measured **24px**, began at **104.5px**, and used **36px** navigation buttons.
- Browser at **390×844**: all seller tabs and inventory, fulfillment history, menu open/close, keyboard selection/focus restoration. No page horizontal overflow; mobile navigation buttons measured **44px**.
- Browser at **320×740**: overview, catalog and promotions without page overflow; tab changes preserve unrelated query parameters and browser Back restores the prior tab.
- Browser at **768×1024**: overview and promotions without page overflow; folded navigation remains usable.
- Reference arrows measured **16px**, a **1.5** SVG stroke, muted rest colour and dark keyboard-focus colour. User detail drawer open/Escape still restores focus. Account links, homepage dark-button arrow contrast, and category selection from Search & discover were checked; category selection closed the drawer and reached the expected catalog URL.
- Screens exercised the isolated demo admin's existing seller capability. No product, inventory, fulfillment, promotion purchase or account-access write was submitted. No backend/schema changes were made or required for this follow-up; the prior backend suite was not rerun. Physical-device and OS reduced-motion checks remain external validation.

Screenshots: [seller desktop](screenshots/seller-match-desktop.png), [seller phone](screenshots/seller-match-mobile.png), [mobile navigation](screenshots/seller-match-mobile-menu.png), [tablet](screenshots/seller-match-tablet.png), [admin comparison](screenshots/seller-match-admin-comparison.png), [account arrows](screenshots/navigation-arrows-account.png), [discovery arrows](screenshots/navigation-arrows-discovery.png).

The local preview remains at `http://127.0.0.1:5175/seller/dashboard`. Existing [startup and demo credentials](EDITORIAL_REVIEW.md#run-locally) apply. Earlier account-access/schema deployment requirements remain documented in [ROLE_ACCESS_REVIEW.md](ROLE_ACCESS_REVIEW.md). Existing README and local configuration edits are excluded. No push or change to `main` is part of this delivery.
