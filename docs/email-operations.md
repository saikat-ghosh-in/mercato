# Transactional email and monthly reports

## Branded, accessible messages

Active transactional notifications use one responsive Mercato HTML template with the original M mark embedded as a CID PNG, a text wordmark, restrained blue accent, readable spacing and black action buttons. Verification links use **Verify email**, resets use **Reset password**, and reports link to the recipient's appropriate dashboard. Commission notices use the same design. The small fallback URL remains available if a client strips button styling.

Messages are `multipart/alternative` with both UTF-8 plain text and HTML, nested with the embedded logo. No external image host, font download, tracking image, script or form is needed. Text is HTML-escaped; actions stay on the configured storefront. An SMTP acceptance still does not prove inbox delivery.

Synthetic visual previews (not valid account links): [Verification](previews/email/verification.html), [Monthly seller report](previews/email/monthly-report.html). Desktop/mobile screenshots are in [screenshots/email](screenshots/email). These previews contain no real verification/reset tokens or recipient data.

## Delivery status

Account creation and resend requests enqueue mail atomically with the account/token change. They do **not** prove sending or delivery. The registration browser can poll its own session-bound status; arbitrary email addresses cannot be used to look up delivery status. Refreshing the page retains that server-side status. A different browser receives an unconfirmed, generic response.

The admin dashboard's **Email activity** tab shows recipient, purpose, attempts, scheduled retries, safe SMTP diagnostics and provider-acceptance time. It never returns message bodies, verification/reset links, OTPs, SMTP credentials or job business keys. Only an administrator with a completed MFA session can access it. Customers and sellers cannot access the email log.

- **Queued / Sending:** waiting for the durable worker or SMTP response.
- **Accepted by email service:** the SMTP operation succeeded. This does not confirm inbox delivery. Accepted jobs are not resent if worker completion is retried.
- **Retry scheduled:** a temporary SMTP/network error; the durable worker applies its normal backoff.
- **Needs attention:** permanent SMTP rejection or invalid configuration, requiring correction and an audited retry. SMTP `525` means the sending IP is unauthorized; `535` indicates authentication failure. Explicit SMTP 4xx errors remain retryable, including temporary authentication failure `454`.
- **Superseded / skipped:** expired, replaced or consumed verification/reset links or sign-in challenges, or a no-longer-eligible report recipient.
- **Completed before tracking:** historical completed mail with no recorded SMTP acceptance time. The migration does not fabricate delivery evidence.

Commission notice email failures also appear here. Their existing separate outbox keeps retrying; the full 60-day commission-change notice period starts only after all required SMTP acceptances. That policy has not changed.

For an email that was accepted but bounced or was filtered later, inspect **Brevo → Transactional → Email → Logs**. Brevo's downstream events are not imported into this application yet. The application deliberately does not display “delivered.” SMTP handoff is at least once: a crash after provider acceptance but before recording it can still produce a duplicate.

## Monthly reports and SMTP-key activity

Production-profile deployments enable monthly operational reports by default. Set `EMAIL_REPORTS_ENABLED=false` to disable them. Demo/local profiles default to disabled; no chat automation or external scheduler is required.

The scheduler checks hourly, first running two minutes after startup. It queues one report per recipient for the previous complete calendar month in `Asia/Kolkata`, using a unique `monthly-report:<month>:<user ID>` business key. Restarting or running multiple instances does not create another report for the same month and person. Up to 100 unscheduled recipients are added per pass. A newly started deployment catches up only the latest complete month, not every historic month. Zero-activity reports are included.

Recipients are active, unlocked, email-verified administrators and approved, unsuspended sellers. An administrator who also sells receives one marketplace report. Eligibility and the recipient address are checked again immediately before preparing the message; a changed or deactivated recipient is skipped.

Admins receive marketplace financial aggregates and listing counts. Sellers receive only their own fulfilled sales, refunds, commission, seller-paid delivery, promotions, refund holds, unresolved amounts, net earnings and listing/low-stock counts. Messages exclude customers, addresses, order ledgers and other sellers. Financial figures use the same calculation as the dashboard, including the captured commission terms, per-unit cap and refund treatment. They describe orders created in the reported month with their current fulfillment/refund status, not bank payouts. Product counts reflect report preparation time.

Reports use the existing SMTP key and durable retries. A successful monthly send provides regular SMTP activity, but a queued/failed report or SMTP login alone does not keep a key active. Check **Email activity → Last provider acceptance** regularly. Brevo's free daily quota still applies; this feature does not purchase credits or paid services.

Brevo's current policy deactivates SMTP keys after 90 days without successful SMTP sending, including keys without an expiry date: [SMTP key policy](https://help.brevo.com/hc/en-us/articles/7959631848850-Create-and-manage-your-SMTP-keys).

## Beta incident: 25 October 2026

OCI reached Brevo over STARTTLS, but SMTP authentication returned `525 5.7.1 Unauthorized IP address`. The account had SMTP IP blocking enabled with no authorized addresses. With the owner's action-time approval, only `80.225.239.145` was authorized; blocking stayed enabled. SMTP authentication then returned `235`. Brevo recorded verification mail to the reported test account as delivered at 13:30. Three queued superseded verification jobs were suppressed in beta, preserving the current link and account.

Never disable the IP allowlist to fix a changed server address. Authorize the verified outbound address narrowly, remove obsolete addresses when no longer used, and keep provider keys private.

## Deploy / rollback

`V8__email_activity.sql` adds safe mail metadata, commission-notice diagnostics and a sign-in challenge delivery identifier. It preserves account, product, order and job data; malformed historic job payloads do not break this migration. Fresh and populated PostgreSQL migration checks cover the change. Normal Flyway owner credentials apply it; the runtime role uses the existing default table permissions.

Deploy the checked `stg` commit as a new GitHub **prerelease**, then run `bash ~/redeploy-beta.sh <new-tag>`. Stable releases still require `main`; prereleases require `stg` ancestry. Do not recreate the beta database or rotate MFA/session keys. See [DEPLOY.md](../DEPLOY.md). An earlier cookie-compatible artifact can run against the additive columns, but it lacks these mail guards and truthful status screens; prefer a forward fix.
