# Beta payment handoff review — 25 October 2026

## What changed

The enforced Netlify Content Security Policy previously allowed forms only to `self`. Cashfree's sandbox SDK submits its hosted checkout form to `https://sandbox.cashfree.com/pg/view/sessions/checkout`, so the browser blocked the handoff after Mercato had already created or recovered a payment session. The policy now allows forms to the exact sandbox hosts `sandbox.cashfree.com` and `payments-test.cashfree.com`. Script, frame, connect, and framing restrictions remain scoped; no production payment form origin or wildcard script source was added.

The frontend SDK is fixed to sandbox. The backend gateway rejects production Cashfree URLs before making a request, while disabled loopback demo/test adapters remain usable. The checkout and handoff controls say **Continue to payment**. On the review step, the desktop action sits below the backend total in the sticky order summary. On phones, one visible action and total stay in a bottom bar while the page scrolls. The existing double-submit guard, ownership-checked payment recovery, server-quoted total, and delivery options remain in place.

## Browser evidence

- The signed-in beta customer reopened an existing pending order and reached the real Cashfree **sandbox** payment-options page. No test payment was submitted. [Sanitized screenshot](screenshots/payment/sandbox-options.png).
- On the review page, standard delivery showed ₹1,763.00; selecting expedited updated the server quote and displayed ₹1,812.00, then selecting standard restored ₹1,763.00. The action retained its label and location during the update.
- Desktop at 1440px: the order summary and payment action are visible beside the review form. [Screenshot](screenshots/payment/checkout-summary-desktop.png).
- Phone at 390px: the bottom action remains visible while reviewing items. [Screenshot](screenshots/payment/checkout-action-mobile.png).
- Browser DOM measurements at 320, 375, 768, and 1024px showed one visible payment action and no horizontal document overflow. The header/search stayed available while scrolling; the bottom action stayed visible at the footer.

## Automated checks

- Frontend: 82 tests passed, ESLint passed, and production build passed.
- Backend: 208 tests passed, zero failures/errors/skips, against disposable local PostgreSQL test databases.
- GitHub `Application checks` passed for staging commit `9969e6194edbfbac9c8a32046ae14cde40ae53b0`.
- Netlify beta deploy `6addf88777cdf097a4a92194` was ready at that exact commit.
- Live beta response CSP contains only exact Cashfree sandbox form targets; unsigned direct-origin `/api/auth/session` still returned `403`.

## Beta installation

GitHub prerelease [`v0.1.0-beta.3`](https://github.com/saikat-ghosh-in/mercato/releases/tag/v0.1.0-beta.3) built from the same checked staging commit. Its release job passed and published the backend JAR, SHA-256 manifest and redeploy script. The existing beta wrapper verified the JAR checksum and installed it on the `mercato-beta` service. It reported a successful signed Netlify session-readiness check. The installed JAR SHA-256 is `547a5b11744c8fe51918bc8fa85a33c22db9af840c7b8566427e999d77eeac81`.

Before and after installation, beta's database had 2 users, 7 products, 4 categories, 1 order and Flyway installed rank 8. The beta service restarted from PID 3674445 to 3676177 and remained active. The separate production `mercato` service stayed active at PID 1098123. The authenticated Browser session and five-item bag still loaded after restart; the existing order reopened Cashfree sandbox again without a CSP error. No database migration was required for this fix.

## Boundaries

The Browser check stopped at Cashfree's sandbox payment-options page. It did not submit a wallet/card payment, trigger a webhook, or test a refund in this pass. The pending test order was reused rather than creating another order. Production Cashfree is deliberately unavailable.
