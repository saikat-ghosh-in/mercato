# Feedback, loading and release follow-up — 25 October 2026

References: [Interfere toasts](https://design.interfere.com/toast/), [spinner](https://design.interfere.com/spinner/) and [skeletons](https://design.interfere.com/skeleton/), together with the existing Emil motion guidance and approved Mercato styling. The scoped Interfere package returned npm 404; Spinner and Skeleton are explicitly local components, rather than an installed or impersonated private package.

## Changes

- One Sonner provider: compact white notifications, a fine border, restrained shadow and semantic icons. Mobile offsets keep messages inside the viewport. Identical failures deduplicate, account changes dismiss old messages, and reduced motion disables toast transitions.
- Removed routine bag, wishlist, sign-out, registration, address, category, product, fulfillment and image-upload success messages. Forgot-password confirmation stays in the page. Important cancellation, reset, saved-profile, email-resend and clipboard confirmations remain. Clipboard success is reported only after writing succeeds. Image validation/errors are inline and clear on correction; bulk update errors produce one summary instead of a toast per row.
- One current-color, twelve-segment Spinner, replacing the rotating refresh icons and checkout border spinner. Sizes follow the reference, including compact button use. Reduced motion gives a static segmented indicator.
- Shared soft Skeleton shapes and content-specific catalog, product, bag/checkout, account/profile, form, address, dashboard, table/list and payment layouts. Replaced anonymous route bars, bespoke pulses and initial text-only loaders. Loading announcements sit outside busy decorative content. Existing data remains during background refreshes.
- Cached payment details remain visible during verification, but the SDK and retry action cannot use them until current verification succeeds. Revalidation cannot automatically reopen the same session/attempt. A failed check exposes Retry verification while preserving the visible details.
- Included the existing shared-heading, responsive workspace, control and restrained transition refinements reviewed in [UI_REVIEW.md](UI_REVIEW.md).

## Cleanup

The active application is `mercato-backend/frontend`: Netlify, CI and the running preview use it. The outer `C:\Projects\mercato\mercato-frontend` was an unreferenced non-Git legacy copy containing 103 files. Its five unique paths were old editor settings and superseded user/home/checkout components. A complete local archive was retained at `backend/target/cleanup-backups/unused-mercato-frontend-2026-10-24.zip` before removing it; that ignored archive is not a release asset.

Removed six unreferenced legacy images/logo assets and the accidentally generated root Vite cache. Removed unused MUI, Emotion and Swiper dependencies, plus the replaced react-hot-toast and react-loader-spinner libraries. Kept the project’s skills, Git history, required configuration, uploads and review evidence. The pre-existing root README and local backend configuration edits remain uncommitted and byte-for-byte preserved.

## Verification

- Frontend: 77 tests pass, including the cutover publication guard; ESLint and production build pass. npm audit reports zero vulnerabilities. The build still reports its existing entry-chunk size advisory (about 567 kB minified); it is not hidden by raising the warning threshold.
- Regression coverage includes segmented spinner accessibility, decorative skeleton shapes, one unblocked loading announcement, every layout variant, cached-payment handoff gating and toast deduplication, alongside existing session/cart/filter/race tests.
- In-app Browser used the production build on loopback, synthetic demo data and disabled real payments. Add-to-bag and quantity changes update the accessible badge and totals without success toasts; the checkout control remains present. Guest checkout redirects to sign-in; shopper sign-in merges the two-unit bag and returns to checkout. Saving a synthetic first/default address advances to review without a success toast. Expedited delivery updates the total from ₹1,810 to ₹1,859 without replacing the content. The disabled gateway returns an inline error, no duplicate toast and retains editable address/items/totals; search and account navigation remain available. The profile loads the current shopper correctly.
- All ten loading layouts were rendered from the actual shared components in a temporary ignored verification fixture at 320, 390, 768 and 1440px: 40 measurements, no horizontal document overflow and no busy status region suppressing its label. This fixture is not an application route or committed release file. Measurements and screenshots are under `screenshots/feedback/`.
- Independent review found and resolved premature cached gateway opening, suppressed loading announcements, stale corrected image errors and inability to retry an incompletely installed immutable release.
- The release installer has Linux regression cases for invalid tags, missing cutover acknowledgment, checksum/path rejection, artifact retention, restart-failure retry, identical-tag retry and rejection of changed content. Linux CI runs these; the Windows host skips the Linux-only installer cases. Git Bash syntax validation passes. PostgreSQL 14 Testcontainers/backend and resolved Java dependency gates remain mandatory in Application checks and the release workflow.
- [GitHub verification](https://github.com/saikat-ghosh-in/mercato/actions/runs/40127253268): 185 backend tests passed with no failures/errors/skips, including real PostgreSQL Testcontainers migrations and concurrency plus the mandatory report checker; all 22 Linux operations tests passed. The initial security-config failure was reproduced and corrected as recorded in DEPENDENCY_SECURITY.md; the final pull-request checks must all pass before merge.

## Release boundaries

The new [deployment guide](../DEPLOY.md) replaces obsolete Windows-service/8443/Hibernate-update/direct-origin instructions. A published, newly tagged release runs the application checks before attaching the JAR, checksum and safer redeploy script. The usual `bash ~/redeploy.sh <new-tag>` interface is retained after the documented one-time Flyway, HTTPS ingress, cookie and MFA cutover.

No live OCI database migration, service deployment/restart, Caddy installation, gateway cutover, paid service or production credential change was performed. Deployed HTTPS/raw-body/IP/CSP verification, configured gateway/mail flows and off-instance backup/monitoring acceptance remain external gates. Real phone hardware and OS screen readers were not available; browser viewport and DOM accessibility checks do not substitute for them.
