# Staging review — storefront and application hardening

## Current revision: spacious editorial redesign

The approved white / black / soft-blue redesign supersedes the green styling described in the historical baseline below. Read [the current review notes](docs/EDITORIAL_REVIEW.md) for screenshots, rich-product schema deployment, startup instructions, fixes and external-service limitations, and [the validation checklist](docs/VALIDATION_CHECKLIST.md) for route/action coverage. Final checks: frontend lint/build and 27 tests pass; backend 49 tests pass. Browser checks cover desktop and mobile customer, seller and admin flows. The latest refinement adds a compact persistent header with Help/Account access, explicit control sizes, aligned native select arrows, denser catalog layout and on-demand Apply-based filters. Mobile coverage includes widths from 320px and landscape; cart updates remain visually stable. The latest follow-up completely redesigns checkout and payment states, introduces an original multi-category marketplace campaign with restrained homepage motion, and aligns admin/seller search controls (40px desktop / 44px mobile with a full phone row). Four additional checkout regressions pass. The newest UI refinement replaces the header shopping links with one Explore panel, moves sorting into the Apply-based filter drawer behind a single icon, reduces shared controls and refines pointer/keyboard motion. This remains a local `stg` review only, with pre-existing README and local configuration edits excluded.

## Historical baseline

This change is prepared for review on local branch stg. It builds on the existing production-hardening branch at 62a53ee, which was already 47 commits ahead of origin/stg. Review the new commit separately from that inherited history. No remote push or main merge is part of this change.

The pre-existing README.md and application-local.properties edits remain outside this commit.

## What changed

The customer storefront has a cohesive green and warm-neutral design, a persistent search field, editorial home page, real category navigation, reusable product cards and shopping bag, and a working shopping-help page. Product names and image links support normal browser navigation. Account and address forms expose accessible labels and errors. Navigation, account menus, mobile filters and the address dialog support keyboard dismissal and focus management. Reduced-motion preferences are respected.

The catalog now owns its loading and error state, cancels obsolete requests, ignores stale responses, and offers retry and empty-result states. It supports the savings sort used by promotional links. Malformed page values and seller names containing percent signs no longer break browsing. Route-level code splitting keeps account, checkout, seller and admin code out of the initial storefront load.

Bag removal now handles the API's text acknowledgement correctly, so deleting one item does not clear the whole local bag. Quantities use confirmed server responses and disable controls during updates. Cart and checkout share cancellable product enrichment, and unavailable item metadata blocks checkout. Address-save failures retain the form; editing a selected address refreshes its checkout snapshot. Network failures while refreshing the account no longer discard a valid session.

Guest JWTs now have unique identifiers. Public catalog requests no longer mint competing guest tokens. This prevents same-second guest-session collisions and initial-page requests replacing the token for an existing bag.

Backend checks now reject invalid catalog pagination, negative/inverted prices, oversized searches, malformed cart/address requests and malformed stock-update batches. Inactive items cannot be added or checked out. Checkout checks reservation quantity and expiry before creating a gateway order.

Guest-cart merging releases its existing holds before reserving the merged bag within the same transaction. Stock corrections and product edits cannot discard paid-order holds. Reservation transfer reconciles quantity differences if the shopper changes their bag during payment. Payment completion removes purchased quantities while retaining unrelated items and additional units; repeat callbacks for an already-finalized order leave the current bag alone. The frontend confirmation poll stops at terminal states, cancels on unmount, and refreshes the server bag instead of issuing another destructive clear.

Uploads check file signatures, normalize image URLs and restrict deletion to generated files under the owned upload directory. Unexpected errors return a generic message to clients. Parameter errors and authorization failures get appropriate HTTP responses. CORS uses explicit allowed origins. Gateway HTTP calls have connection/read timeouts.

GitHub checks run frontend lint, regression tests and build, plus the Java tests for PRs and pushes to stg/main.

## Local review

Run from the monorepo root in two terminals:

    cd backend
    .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo

    cd frontend
    npm ci
    npm run dev -- --host 127.0.0.1 --port 5174 --strictPort

Open http://127.0.0.1:5174. Use reviewer / ReviewOnly!2026 to exercise account, checkout, seller and admin screens.

The explicitly selected demo profile binds the API to 127.0.0.1, creates an in-memory H2 catalog, and uses only synthetic credentials. Restarting the API resets this database. Its Cashfree endpoint points to an unused loopback port and SMTP points to local port 1025; actual payment and email delivery are disabled. Never select the demo profile for a deployed environment. Existing local/prod configuration remains the deployment source.

## Validation

    cd frontend
    npm run lint
    npm test
    npm run build

    cd backend
    .\mvnw.cmd test

Browser checks cover desktop search and product details, adding multiple items, removing one while retaining another, quantity totals, guest sign-in and bag merging, saving an address, progressing checkout through review to the payment step, saved items, mobile navigation and price filters, Escape-to-close, and API-unavailable states. Real money was not transacted.

Verified locally: 44 backend tests passed with zero failures/errors/skips; 7 frontend regression tests passed; lint and the production build passed. The initial application bundle is approximately 471 KB before gzip, down from approximately 757 KB before route splitting.

The backend suite includes the full application context, real security filters and H2/JPA integration for guest isolation, sign-in merge, cart updates, expired reservations and invalid requests, in addition to inventory/payment unit regressions.

## Before a production release

This is a substantial reviewable UI and correctness improvement, not proof of enterprise capacity. Verify Cashfree sandbox payment/return/webhook/refund flows and mail delivery against the intended staging infrastructure, then validate PostgreSQL migrations and concurrency with the actual deployment configuration. Load/soak tests, backup restore, monitoring/alerting, operational support and returns policies, and a formal accessibility/security review remain production release work. H2 integration and mocked concurrency tests do not establish production-database behavior under load.

Editorial photography and the demo product images use Unsplash URLs; fonts use Google Fonts. Confirm asset rights and host/cache the production assets according to your delivery and privacy requirements.
