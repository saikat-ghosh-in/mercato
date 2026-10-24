# Mercato hardening implementation ledger and contract

Authority: the user's approved “Mercato hardening — final revised implementation plan”, 22 October 2026, in this conversation. This file records its executable increments and decisions; it does not reduce its requirements.

## Global constraints

- Work on local stg; no push or main changes. Preserve all initial working changes, especially README and application-local.properties.
- Keep Netlify frontend/OCI backend and the current UI and commerce capabilities.
- Preserve 0.5% commission, HALF_UP per-unit rounding, 500.00 cap per unit, captured legacy terms, 60-day notice and existing delivery policies.
- Real PostgreSQL baseline must come from the current database schema-only dump. Never manufacture provenance or enable baseline-on-migrate. External access/real HTTPS verification remains a release gate when unavailable.
- Red/green regression tests before fixes; PostgreSQL is authoritative for transaction/concurrency behavior.
- No database locks across HTTP/SMTP. Durable, idempotent, at-least-once work.
- Never restore browser bearer authentication as rollback after cookie cutover.

## Task 1: database, jobs and transaction foundation

Capture real schema/version/extensions, reconcile drift, version Flyway upgrades, compare fresh versus populated-upgrade normalized schema. Disable OSIV and explicitly map/fetch under transactions. PostgreSQL Testcontainers is mandatory CI coverage.

DurableJobs.enqueue(String type, String businessKey, Map<String,Object> payload) joins the caller transaction. Handlers implement JobHandler.type() and JobHandler.handle(JobRecord). Jobs have unique business keys, versioned payloads, due time, attempt count, lease owner/token/expiry, failure fingerprint and sanitized failure. Atomic SKIP LOCKED claims, 2s polling, batch 10, two workers; commit claims before processing. Fenced completion and lease recovery. Retry 5s,30s,2m,10m,1h,6h with jitter. Deterministic poison errors immediately NEEDS_ATTENTION; three repeated unknown fingerprints stop. Operator retries retain the key and are audited.

## Task 2: commerce

Persist signed raw webhook events and processing jobs before immediate acknowledgement. Unique provider/attempt IDs; validate amounts/currency/order; same reconciliation for polling. Record capturedAmount/capturedAt in its own committed transaction. Monotonic captured/refunded states. Allocation is all-or-nothing in a separate transaction with ordered locks. Insufficient stock queues full actual-capture refund; transient infrastructure errors retry. Cancelled orders stay cancelled on late capture. Persist gateway termination when cancelling. Shipment requires payment/allocation/accepted quantity/reservation. Cancellation status derives from quantities.

Refund merchant IDs stable; reconcile ambiguous outcomes before retry. Network/429/5xx retry; insufficient funds wait+alert; pending/onhold poll; permanent and poison NEEDS_ATTENTION. Full automatic refund is captured amount after discounts minus successful/in-flight refund amounts, never line recomputation. Refund all buyer charges, zero commission/seller earnings and reverse accruals. Customer and isolated seller notifications on initiation/completion. BigDecimal or integer paise only. Historical read-only anomaly report; unknown evidence is not fabricated.

## Task 3: ingress and gateway cutover

Netlify HTTPS -> Caddy DuckDNS -> private Spring, proxy paths /api,/api/*,/images,/images/*. Signed Netlify JWS proves origin only. Authenticate before interpreting forwarding; canonical public host/proto configured. Native IP metadata may replace Edge only after platform guarantees + deployed spoof tests. Otherwise fail-closed Edge on EVERY proxied method/path including webhook, overwrite IP with context.ip, strip forwarding, preserve body stream, onError fail. Reject missing metadata; never silent fallback. Separate stage/prod secrets, safe rotation, no secret logs.

Raw body/fast acknowledgement direct tested in Task 2, real Netlify verified in Task 3. New+legacy webhook feed same inbox. Temporary exact legacy webhook bypasses ONLY Netlify signature, still Cashfree signed; no sessions/forwarding/IP logic there. Switch new gateway URLs only after deployed tests; retain legacy returns with validated order IDs and no payment trust. Retire only after legacy orders reconcile, retries drain and 7 days no traffic. No public health exception. CSP report-only then enforce, precise origins, no wildcard scripts/unsafe eval/inline script. Static headers on Netlify and API headers on backend. Staging short HSTS, production 1y, no preload/subdomains without validation.

## Task 4a: sessions and adapter

Spring Session JDBC, stable-user-ID principal index, minimal serializable principal without password. Production __Host-mercato-session Secure HttpOnly SameSite=Lax Path=/, no Domain; loopback demo separate name. Rotation on login and guest merge. CSRF for all browser mutations, exact signed webhooks exempt only. Session/CSRF bootstrap returns identity/profile, expiry and account-context. Mutation context mismatch 409 SESSION_CONTEXT_CHANGED. AuthenticatedAt and absolute expiry immutable; customer idle24h/absolute7d, seller1h/24h, admin30m/12h, strictest role. Logout current; reset/deactivate/access changes revoke all indexed sessions and increment accessVersion. Atomic reset-token consumption. Recover payment session from authorized orderId, not storage. Reject legacy auth JWT after cutover; valid guest-token migration one time only.

## Task 4b: MFA and abuse

Explicit enrollment/challenge/recovery-code/replacement/customer step-up screens. Admin TOTP required for privileged session; encrypted seed, ten hashed single-use recovery codes, step replay protection, recent established proof for replacement, audited operator recovery only. Reset doesn't bypass MFA. Startup guard BEFORE migration/seed/listener: stage/prod rejects demo/test/bypass/demo creds; demo only memory H2/loopback URLs/binding/no live payments; mixed prod/demo fails.

DB counters/deadlines; client/account progressive delays, broad IP controls and 50 account failures/rolling hour step-up across IPs. Existing sessions unaffected, no permanent lock, generic responses. Customer short single-use email challenge, admin established MFA. Recovery 3/account/hour,20/IP/hour; bounded attempts/resends, Retry-After, no sleeping HTTP threads.

## Task 5: frontend state

Single in-memory session bootstrap state with recoverable failure/retry. Cross-tab invalidation, cancellation and session generation fences for every private response and mutation. RTK Query incrementally owns profile/address/wishlist/order/management reads. Cart serialized authoritative totals unchanged. Fence wishlist reads against mutations, gate initial toggles. Profile initialization waits; dirty form preserved on refresh. Delete private/credential/cart/payment persistence, validate/version safe preferences and tolerate storage failure. Guest migration before deleting valid legacy token. URL filters/sort and local form drafts preserved. Refresh payment by order ID without creating another order.

## Task 6: operations and acceptance

Private metrics and DB-job-loop outgoing heartbeat every minute; Healthchecks.io missing3min -> OPS_ALERT_EMAIL fallback ADMIN_EMAIL. Separate backup heartbeat. Deduplicated Brevo alerts independent from external monitor mail. SMTP outside commission lock. Paginate lists 25 default/100max; SQL aggregate finance with paginated ledger. Triage npm and resolved Java advisories.

Encrypted pgBackRest private off-instance OCI Object Storage, weekly full/daily differential+continuous WAL, 4 weekly chains. RPO15m/RTO4h. Separate recoverable keys; capacity verified not silent pruning. Real backup/upload/restore/alerts gate production (OCI idle reclaim risk).

## Acceptance / evidence

Each task owns tests listed above, plus full frontend test/lint/build and relevant backend suite. PostgreSQL migration/locking mandatory CI, not replaced by H2. Browser desktop/mobile commerce, multi-tab, error/slow requests, MFA and recovery. Real HTTPS, Cashfree sandbox/SMTP/backup setup recorded explicitly when not available. External gates must not be marked passed by mocks. Focused commits and evidence in docs/HARDENING_REVIEW.md, never unrelated changes.
