# Mercato hardening review â€” 24 October 2026

Authority: [approved implementation contract](superpowers/plans/2026-10-22-mercato-hardening.md). Work started on local `stg` at `e9abdc4`. No push, remote/main change, OCI database alteration, deployed service restart, Netlify deployment or gateway URL cutover was performed.

## Review units

1. **Database/jobs:** actual OCI schema provenance, additive Flyway V1â€“7, explicit drift reconciliation, populated/fresh normalized schema comparison, OSIV disabled, database job claims/leases/fences/backoff/poison handling and audited retries.
2. **Commerce:** authenticated raw-byte inbox plus atomic processing job, monotonic attempts/capture, separate capture/allocation transactions, ordered inventory locks, cancellation/termination, actual-capture refund obligations, reconciliation/classification, isolated notifications and verified financial reporting.
3. **Ingress:** explicit signed Netlify rewrites, fail-closed Edge path inventory/IP normalization, private canonical-HTTPS backend filters, Caddy configuration, security headers/CSP and dual-URL cutover runbook. These are prepared configurations; real deployed transport acceptance remains open.
4. **Authentication:** JDBC cookie sessions, CSRF/account context, indexed revocation/access versions/absolute expiry, commit-before-elevation, admin TOTP/recovery/replacement/operator recovery, customer step-up, persistent abuse limits and early environment/MFA-key guards. The minimal frontend session adapter ships with this contract.
5. **Frontend state:** bootstrap states/Retry, cross-tab invalidation/response fencing, RTK Query private reads, serialized authoritative bag changes, wishlist barriers, dirty-profile initialization, guest migration and authorized payment recovery by order ID. Private/token/payment persistence is removed.
6. **Operations:** outbound job-loop heartbeat, deduplicated alerts/private retry API, guarded encrypted pgBackRest backup/restore tooling, mandatory CI PostgreSQL evidence and current dependency checks. Live recovery/alert gates remain unverified.

Seller onboarding, listing approval, promotions, delivery options/seller-paid shipping and historical commission terms are retained. New commission calculations remain per-unit HALF_UP at 0.5%, capped at â‚¹500 per item. The full notice period starts after all required notices are accepted for delivery; SMTP runs outside the settings lock.

## Verification

- **Backend:** final complete suite **185 tests, 0 failures, 0 errors, 0 skips**, Java 17, explicit disposable local PostgreSQL 18.1 for migration/jobs/commerce/auth races. Required PostgreSQL report gate passes; release jar packages successfully. Logs: `backend/target/backend-release-verification.log`, `package-release-verification.log`, Surefire XML.
- **PostgreSQL:** actual OCI source/dump version 14.24, only plpgsql 1.0; both prod/beta schema-only exports are checked in without customer rows. Tests compare columns/defaults/constraints/indexes/sequences, validate final ORM mapping and retain unknown legacy terms. Docker is unavailable locally: **PostgreSQL 14.24 Testcontainers was not run here** and remains mandatory, without skips, in CI.
- **Frontend:** final **72/72 tests**, lint and production build exit 0 after the last dependency fix. A hardening-only export excluding initially dirty UI changes also passes tests/lint/build. Logs: `backend/target/frontend-release-*.log`, `isolated-frontend-*.log`. Vite retains an approximately 540 kB main-chunk advisory.
- **Dependencies:** npm audit 0 vulnerabilities. Resolved CycloneDX/Trivy scan exits 0 with no unassessed HIGH/CRITICAL findings. Two non-executable Spring MVC rendering findings have exact-version OpenVEX assessments and a runtime regression guard; see [dependency assessment](DEPENDENCY_SECURITY.md).
- **Operations tooling:** 15 Python tests, 14 pass and one Windows-only symlink test skipped (enabled in Linux CI). Python/CLI checks pass. Linux systemd execution, actual backup/restore and external alert delivery are not claimed.
- **Browser:** catalog/bag/sign-in/cart merge, addresses/default selection, delivery recalculation, failed-payment value retention, profile editing, PDP gallery/wishlist, cross-tab logout and administrator/seller reads exercised. Mobile 320px/390px and desktop evidence: [Browser acceptance](HARDENING_BROWSER_CHECKS.md). No live charge/email was sent.
- **Review:** partial independent source review found cancellation snapshot, OSIV retry and raw-evidence gaps, corrected with regressions. Its remaining pass hit the review agent's usage limit; it is not unconditional independent approval. [Disposition](superpowers/hardening-final-review.md).

## Preservation

The initial 54 tracked dirty UI/README/configuration entries have byte-preserving copies and SHA256 manifest under ignored `backend/target/hardening-baseline/`. README and `application-local.properties` hashes still match. Existing UI-only changes, their untracked helpers/screenshots and prior HomeData deletion remain in the working tree. Overlapping hardening changes are staged through reviewed three-way candidates without overwriting those files; an isolated frontend build proves no dependency on uncommitted UI helpers. Local commit identities are appended after creation. No remote branch is modified.

## Remaining external gates and cost boundary

- Adopt the verified existing schema only after backup/recovery gates, then deploy a cookie-compatible staging release. Automatic baselining remains off; no live adoption occurred. [Migration runbook](DATABASE_MIGRATIONS.md).
- Configure matching Netlify/OCI secrets/private ports and deploy transport. Verify raw bytes/fast acknowledgement, IP forgery/Edge crashes, direct-origin rejection, Secure cookie/CSRF navigation and enforced CSP through real HTTPS. Retain legacy callbacks until reconciliation/retries drain and seven days show no legacy traffic. [Ingress runbook](INGRESS_CUTOVER.md).
- Enroll actual admin MFA; verify configured Cashfree sandbox/refunds and real mail; finish staging HTTPS payment/fulfillment acceptance. Demo/mocks do not satisfy these gates.
- User confirmed no backup bucket/S3 profile/Healthchecks checks and requested ongoing free tiers only. No paid/trial service or account was created. Read-only OCI inspection confirmed pgBackRest/OCI CLI/profile/Caddy command absent and WAL archiving off. Healthchecks Hobbyist and OCI Always Free can support a small installation after account/quota/capacity/API-request verification; third-party terms are not guaranteed never to change. Skip billing/paid overflow. [Free-only setup boundary](OPERATIONS_BACKUP_RUNBOOK.md).
- Scheduled encrypted off-instance backup, independently recovered keys, replacement-host restore, measured RPO/RTO and missing-heartbeat alert delivery are **not yet proven**. Original production release gates remain unmet; same-instance backups are not substituted.

Rollback must remain schema-compatible and cookie-compatible; never restore browser-stored bearer authentication. Preserve the MFA encryption key and its separate recovery copy: replacing it without an audited re-encryption/recovery process makes existing factors unreadable. Proxy key rotation and gateway reconciliation are documented in the cutover runbook.

## Local review commits

- 1: `a9160596cd52` — feat(db): baseline actual OCI schemas and add durable PostgreSQL jobs
- 2: `74720b972d5a` — fix(commerce): persist captures and reconcile allocation refunds safely
- 3: `96c8e46a5188` — feat(ingress): prepare signed fail-closed Netlify and private HTTPS origin
- 4a: `65541bee4a5a` — feat(auth): adopt cookie sessions revocation and server proof enforcement
- 4b: `c518bcd05b8e` — feat(auth-ui): add MFA enrollment challenge recovery and replacement
- 5: `1a5c7c04c70f` — refactor(state): fence private requests and migrate account reads to RTK Query
- Operations, CI and final evidence are the following local commit. These are ordered review units; production authentication requires both the server/session and MFA UI units.
