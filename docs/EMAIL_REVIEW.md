# Email reliability, reports and branded messages — 25 October 2026

The reported verification failure was caused by Brevo rejecting OCI's sending IP with SMTP `525`. The owner approved authorizing that single address; SMTP blocking remained enabled. Authentication subsequently succeeded, and Brevo showed a delivered verification event for the reported account. Three queued obsolete verification messages were suppressed without changing account data.

## Changes

- Responsive Mercato HTML emails: embedded original M logo, wordmark, restrained blue accent, clickable verification/reset actions and readable financial rows. Both UTF-8 plain text and HTML are sent; a quiet fallback URL remains available.
- Administrator **Email activity** tab with safe diagnostics, current failures, attempt counts, scheduled retries, last provider acceptance, filters and audited retries. No bodies, OTPs, verification/reset tokens, business keys or SMTP credentials are returned by the log API.
- Registration status belongs only to its requesting browser session. Queueing and SMTP acceptance are described accurately; no automatic “delivered” claims. Errors remain visible inline.
- Superseded, expired and consumed authentication mail is skipped. Sign-in OTP jobs are bound to their exact challenge and live session; temporary SMTP 4xx authentication failures retry rather than stopping permanently.
- Monthly reports for active verified admins and approved sellers, including zero-activity months. Admins receive marketplace aggregates; each seller receives only their own financial figures and product counts. Eligibility is checked again before sending; jobs are deduplicated by month and recipient. See [Email operations](email-operations.md) for scheduling, source-period semantics, quota and inactivity policy.
- Additive `V8` migration preserves existing data and tolerates malformed historic job payloads. Existing per-unit commission terms, shipping contributions, refunds and the full 60-day commission notice policy remain intact.
- Explicit GitHub prereleases can be published from checked `stg` commits; stable releases still require `main`. The existing beta redeploy wrapper remains the installation path.

## Verification

- Backend: 206 tests, zero failures/errors/skips using disposable local PostgreSQL databases. Includes fresh/populated schema equivalence, malformed job migration, real locking/session tests, SMTP classification/fencing, response shape, MIME roundtrip and two-seller report isolation.
- Frontend: 80 tests, lint and production build pass. Existing bundle-size advisory remains.
- Operations: 24 local tests with eight Linux-only deployment cases skipped on Windows; Linux CI is required before installing the release.
- Code review caught temporary-auth retry and stale OTP defects; both have regressions. Follow-up review found no remaining concrete issues, including branded MIME/CTA safety.
- Browser: desktop 1440px and mobile 390px verification failure, acceptance after retry, administrator log, custom filter, audited retry dialog and responsive branded email/report previews. No horizontal overflow was observed at mobile width. A real SMTP rejection was simulated only by a loopback test sink; no disposable accounts were created on beta.
- Browser exposed H2/PostgreSQL column-name casing differences in the log API; a response-contract regression and explicit normalization fix cover it.

## Visual evidence

- [Branded verification, desktop](screenshots/email/branded-verification-desktop.png)
- [Branded verification, mobile](screenshots/email/branded-verification-mobile.png)
- [Branded seller report, mobile](screenshots/email/branded-report-mobile.png)
- [Verification failure, mobile](screenshots/email/verification-failure-mobile.png)
- [SMTP acceptance, desktop](screenshots/email/verification-accepted-desktop.png)
- [Admin activity, desktop](screenshots/email/activity-desktop.png)
- [Admin activity, mobile](screenshots/email/activity-mobile.png)

Synthetic standalone previews: [verification](previews/email/verification.html), [seller report](previews/email/monthly-report.html). Preview links are deliberately invalid and contain no account secrets.

## Limits and deployment

SMTP acceptance is not inbox-delivery evidence. Downstream delivery/bounce events remain in Brevo's transactional logs; they are not imported by this release. A crash between SMTP acceptance and recording that acceptance can produce a duplicate. Browser previews establish responsive layout, not complete coverage of every desktop email client.

Monthly reporting only maintains key activity when a message successfully sends. It does not purchase credits or bypass Brevo's free quota. Demo/local defaults disable scheduled reports. Paid backup/monitoring services remain unconfigured, as requested.

Deploy the checked beta prerelease with `bash ~/redeploy-beta.sh v0.1.0-beta.2` once its release checks and assets are complete. Keep beta's database and existing encryption/session keys; do not run the fresh-launch deletion/bootstrap steps again. Production and `main` remain unchanged. [Deployment guide](../DEPLOY.md).
