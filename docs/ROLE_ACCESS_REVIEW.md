# Account access and user management review

Local `stg` follow-up, 20 October 2026. The supplied `role-structure-plan.md` was used as a reference, with a deliberately limited implementation that preserves Mercato's existing marketplace capabilities.

## What changed

- Keep the existing User, Seller and Admin roles. Every account retains the base User role. Seller registration, business details, manual verification, listing approval, commissions and direct admin product creation remain in place.
- Replace arbitrary role checkboxes with explicit account deactivation/reactivation and selling suspension/restoration. Admin accounts remain read-only, including through the API. Selling restoration requires an approved application, verified email and an active, unlocked account.
- Selling suspension hides products from the catalog, PDP, sponsored placements and public seller list. The seller can still sign in, shop, inspect earnings, cancel promotions and manage existing fulfillments. New catalog submissions/edits and promotion bookings are blocked on the server; the dashboard explains the restriction.
- Require a 10–500 character reason and the current access version for each administrative access change. Row locks serialize decisions; stale versions return a conflict. Recorded events retain actor, subject, time, reason and before/after states. Seller approval/rejection decisions also appear in this history. History is paginated and only accessible to admins.
- Close the login reactivation defect: admin-deactivated accounts cannot reactivate themselves by signing in. Voluntarily paused accounts restore only with the correct password. Login, pause and admin decisions use row locks; pause/promotion operations refresh state after acquiring the lock.
- Revoke existing JWT sessions on account access changes. Revoked sessions remain invalid even after access is restored. Tokens must match both the current account identifier and access version. Existing tokens without a version remain compatible only at version zero.
- Retain disabled accounts and their history. Remove the former seven-day purge and hard-delete action. Existing disabled accounts default to an admin-controlled state during migration; their origin is not guessed. Voluntary pause is explicitly recorded going forward.

## Frontend refinement

Installed and applied [design-taste-frontend](../../.agents/skills/design-taste-frontend/SKILL.md) from [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill). The project skill is outside this Git checkout, at `C:/Projects/mercato/.agents/skills/design-taste-frontend`.

Direction: **Achromatic ledger**, seed **786952345**, no redraw. Keep Mercato's DM Sans, neutral palette, restrained blue accents, original brand and existing navigation shell. The dealt icon rail was not substituted for the approved header/tabs: the user explicitly requested selective improvements. The screen's distinguishing content is Mercato account identity beside independently stated account and selling access, followed by recorded commerce activity and access decisions.

The Users screen answers: **Which account needs attention, and what access does it have?** The detail panel answers: **What has this account done, and what will this access change affect?** Counts come from the current rows; no sample analytics were introduced.

- Quiet rows replace gradient avatars and multi-colour role chips. The identity column carries name, email and username. Shopper details do not show store fields. Seller applicants retain store and application status details.
- Search, role/status filtering, date/name sorting, empty results and reset actions work together. Refresh retains existing content and cancels superseded requests. The admin tab is represented in the URL, including back navigation.
- Desktop toolbar controls are 36px; mobile targets are 44px. A global minimum input height and oversized heading rule were explicitly corrected in this scope. Narrow phones give the longer status control a full row; no OS-native select was introduced.
- Mobile rows expose the same account information without horizontal table scrolling. Detail panels scroll internally, keep their header visible, restore focus after cancellation/Escape and retain entered reasons on errors.
- Access/activity refreshes retain current content. Existing shared 140ms press, 180ms menu/backdrop and 240ms drawer feedback, keyboard handling and reduced-motion CSS remain in use. There are no page entrance or refresh animations.

### Visual critique

Three review passes addressed the largest defects first: (1) mobile horizontal table overflow and noisy avatar/status treatments; (2) inherited 36px page heading and oversized search input; (3) excessive nested header padding and truncated status labels at 320px.

The seven app-screen checks were applied: hierarchy prioritizes account identity and access; blocks answer a concrete management question; chrome stays neutral and secondary; counts and states agree with server rows; zero activity is an honest empty state; no chart legend or invented financial data is involved; phone navigation folds while rows and actions remain accessible. Remaining contextual limits: wide historical commerce tables elsewhere retain their existing local scroll frames; this revision does not redesign every dashboard analytics view.

## Verification

- Backend full suite: **99 tests, zero failures/errors/skips**. New integration coverage exercises admin-only access, protected admins, required reasons, stale commands, retention, voluntary pause, admin deactivation, token revocation across restoration, seller suspension and retained shopping/fulfillment access. Regression checks also cover hidden catalog/PDP listings and blocked promotions. Existing commerce, payment, listing, commission and inventory tests pass.
- Frontend: **41 tests pass**, lint and production build pass. New regression tests cover combined filters/sorting, non-mutating ordering, protected admins, seller eligibility, and independent account/selling states.
- Additive migration exercised twice in H2 PostgreSQL mode: preserve existing account state, fill missing base roles without duplicates and retain independent audit records.
- Taste source lint for the new user-management surface, its stylesheet and paused-account screen: **0 failures, 0 warnings**. The skill's separate browser launcher was not used; all rendered inspection was performed through the required Browser tool.
- Browser at **1440×960, 768×1024, 390×844 and 320×740**: no page horizontal overflow in the user-management screen. Verified custom status menu, combined search, empty/reset behavior, keyboard sort selection, mobile navigation, URL/back behavior, user/admin/seller drawers, shopper/store-field separation, admin read-only controls, short-reason error, cancel/Escape focus restoration, internal drawer scrolling, and retained seller application review. Registration's shopper/seller toggle, confirm-password fields and business field information remain available. No registration or seller-approval submission was performed in Browser.
- Reduced motion is covered by the shared CSS paths; physical-device Safari/Android testing is still required. No live email, payment, payout or production identity-verification service was exercised. Demo SMTP delivery is unavailable and logs expected connection failures; local validation does not establish email delivery.

Screenshots: [desktop](screenshots/roles-desktop.png), [tablet](screenshots/roles-tablet.png), [phone](screenshots/roles-mobile.png), [phone details](screenshots/roles-mobile-details.png), [narrow custom menu](screenshots/roles-narrow-dropdown.png), [registration](screenshots/roles-registration-mobile.png).

## Deployment and review

1. Back up the staging database and stop all old application instances, including scheduled jobs. Old binaries contain the retired account-deletion task; do not run them alongside the new account-retention behavior.
2. Run [2026-10-account-access.sql](sql/2026-10-account-access.sql) against PostgreSQL before deploying. It adds three account columns and an independent access-event table, then backfills the base User role. Repeat execution is safe. No previous disabled account is automatically reactivated. A real PostgreSQL rehearsal is still required; H2 compatibility is not a substitute for that check.
3. Use a migration owner distinct from the runtime database role. Grant runtime SELECT/INSERT and identity-sequence usage for access events; withhold UPDATE/DELETE. The application exposes no event-edit/delete operation. Provision these grants for your actual database role names.
4. Deploy the frontend and backend together. Unreasoned legacy admin role/deactivate/reactivate write routes now reject requests; the new UI uses the audited access command. Do not roll back to old binaries with automatic cleanup enabled.
5. Start the disposable local demo using the existing `demo` profile instructions in [EDITORIAL_REVIEW.md](EDITORIAL_REVIEW.md). API: `http://127.0.0.1:8080`; preview: `http://127.0.0.1:5175`. Admin: `reviewer` / `ReviewOnly!2026`; shopper: `shopperreview` / `ShopOnly!2026`; pending seller applicant: `sellerreview` / `SellerOnly!2026`. These are isolated demo fixtures, not production credentials.

The reference's super-admin hierarchy, admin invitations/MFA, granular admin scopes, needs-information/resubmission workflow and payout holds are **not introduced** in this selective revision. Existing admin accounts and seller registration are preserved. A separate data-retention/erasure process may be needed; deactivation no longer promises automatic erasure.

Delivery remains local to `stg`. Remote branches and `main` are unchanged. Existing uncommitted README and local configuration edits remain excluded from the commit.
