# Mercato frontend

The active storefront and management app lives in this directory. It uses React 19, React Router, Redux Toolkit/RTK Query, Headless UI, Tailwind and Vite. Use Node 22.

```bash
npm ci
npm run dev
npm test
npm run lint
npm run build
```

The local Vite proxy sends `/api` and owned `/images` requests to the loopback backend on port 8080. The production browser uses same-origin `/api` through the signed Netlify ingress. See [deployment instructions](../DEPLOY.md) before deploying; uploading `dist` alone omits required root Netlify configuration.

Authentication uses HttpOnly cookie sessions with CSRF and account-context headers. Session state and private caches stay in memory. Do not reintroduce bearer tokens, user records, totals or payment sessions in local storage. Filters and sorting live in the URL; drafts stay local. Keep serial cart mutations and backend-calculated money totals.

Shared feedback lives in `src/components/shared/Spinner.jsx`, `LoadingStates.jsx`, `Notifications.jsx` and `feedback.css`. Spinner and Skeleton are local implementations matching the requested Interfere references; the private Interfere package is not installed. Sonner handles accessible toast positioning, stacking and swipe dismissal through one root provider. Call the shared `utils/toastUtils` facade; repeated failures are deduplicated. Routine bag, wishlist, upload and sign-out successes use the visible UI state instead of toasts. Keep form validation next to its field and use a toast only for an actionable failure or important confirmation not otherwise clear.

Use layout-specific skeletons only for initial unresolved data. Retain existing content on background refresh. Busy gateway handoff must remain blocked until current payment verification completes, even when cached payment details render. Decorative loading shapes are hidden from assistive technology; their status label is separate from the busy content. Motion respects reduced-motion preferences.

`npm test` covers cart serialization/counts, filters, session isolation, wishlist races, payment recovery, loading primitives and toast deduplication. Browser evidence and external-service limits are recorded in `../docs/FEEDBACK_REVIEW.md` and `../docs/HARDENING_BROWSER_CHECKS.md`.
