# Jobs and database foundation evidence

Scope: local `stg`, no commits/staging and no OCI database writes. The initial `README.md` and protected `application-local.properties` were not edited by this work.

## Public jobs contract

Package `com.mercato.Jobs` exposes `DurableJobs.enqueue(String type, String businessKey, Map<String,Object> payload): JobRecord`. REQUIRED propagation joins the caller transaction; PostgreSQL conflict-safe insertion returns an identical existing job and rejects changed type/payload under the same globally unique business key. JSON decimal values remain exact BigDecimal values. A missing payload version defaults to 1; an explicit null/invalid/unsupported version is quarantined.

`JobHandler.type(): String` and `JobHandler.handle(JobRecord): void` are unchanged. `JobRecord` exposes UUID id, type, businessKey, payload Map, payloadVersion, status, attempts, dueAt, leaseOwner/token/expiry, failureFingerprint/count, sanitized failure and creation/update times. Handlers must reconcile ambiguous external outcomes using the stable key before repeating an effect; delivery is at least once.

`JobStore.claim(owner, limit, lease)` commits a REQUIRES_NEW transaction containing PostgreSQL `FOR UPDATE SKIP LOCKED` and caps claims at ten. The default two workers poll every two seconds and claim when ready; they renew 60-second leases every 20 seconds. Handlers execute outside the claim transaction. Completion, failure and lease renewal require the current token, owner, RUNNING state and an unexpired lease. Expired claims are recoverable and increment attempts.

Statuses are READY, RUNNING, WAITING, SUCCEEDED and NEEDS_ATTENTION. Known transient errors retry after 5s, 30s, 2m, 10m, 1h, then 6h, with 10% jitter. `WaitingJobException(message, Duration)` supplies a pending-state delay. `PoisonJobException` and `ManualInterventionJobException` stop immediately. Three consecutive identical unknown failure fingerprints stop for attention. Only category/class is persisted as the failure description; raw provider messages and payloads are not logged.

`DurableJobs.retry(UUID, actor, reason)` / `JobStore.retry` accept only WAITING or NEEDS_ATTENTION jobs, retain identity/key/attempt history and atomically append actor, reason, previous status and timestamp to `durable_job_retry_audit`. The administrative caller supplies authorization. `JobWorker.lastSuccessfulPoll()` exposes the DB claim-loop heartbeat. `mercato.jobs.enabled=false` disables workers in tests; poll-millis/workers/lease-millis override defaults.

## Database provenance and migrations

The actual schema-only OCI dumps are preserved at `docs/sql/baseline/oci-mercato.schema.sql` and `oci-mercato_beta.schema.sql`; source PostgreSQL and pg_dump are 14.24. `oci-version-extensions.txt` records only plpgsql 1.0 in each database. V1 derives from the actual prod dump, not JPA or a fabricated empty database. Its recorded raw source SHA-256 is BBEB6F0FA7AAA40ED20DCDA12123C1270B0DCAD76F678A0037982D863D25F271. Only psql restriction commands, public schema creation and session settings were removed.

V2 explicitly applies the recorded historical feature scripts and reconciles actual prod/beta key-order, duplicate-constraint and payment-method-check differences. V3 creates jobs/audit, V4 auth/MFA/abuse, V5 the Spring Session JDBC 3.5.3 PostgreSQL schema and V6 the parent-owned capture/inbox/refund/shipment-notification upgrade. Existing disabled users, lower commission and unknown historical snapshots are preserved. Capture amounts/times are never inferred from completed_at.

Production now validates Hibernate mappings, disables OSIV and uses checked Flyway upgrades. Automatic baselining and cleaning are disabled. Demo keeps its isolated H2 creation and Session initializer, with Flyway disabled and explicit demo-only MFA bypass. MFA encryption and private operational monitor values are deployment environment mappings. See `docs/DATABASE_MIGRATIONS.md` for the explicit adoption and release gates. The read-only `compare-legacy-schema.ps1` accepts the selected exact recorded schema and rejects a different baseline; it never baselines a database.

## Tests and observed red/green evidence

Initial missing-foundation RED: `backend/target/jobs-red.log`. Actual H2 RED identified Spring @Repository translation wrapping intentional input rejections; JobStore now uses @Component because JdbcTemplate already translates SQL errors. Later H2 and PostgreSQL RED identified an explicit null version being defaulted incorrectly and a decimal payload being rounded through Double; both were fixed and passed on both engines.

`backend/target/jobs-migration-drift-red.log` records the real normalized-schema mismatch between prod/beta: primary key ordering on product_secondary_images and user_role plus the beta-only payment-method constraint. V2 now reconciles these exact differences. Comparator harness fixes apply Spring Boot naming strategies and explicit PostgreSQL internal-char casts. The acceptance comparison queries actual columns/types/defaults, constraint definitions, indexes and sequence definitions; it excludes physical column ordering and duplicate semantic constraints, not missing business constraints.

Latest jobs run: H2 `DurableJobsIntegrationTest` 12/12 passed; real PostgreSQL `PostgresDurableJobsTest` 14/14 passed, including disjoint simultaneous claims, concurrent same-key enqueue, rollback coupling, fenced stale operations, lease recovery, payload poison, exact decimals, retry schedule and audited retry. No cases were skipped. Evidence: `backend/target/jobs-foundation-green.log` and matching Surefire XML/text reports.

Migration tests passed legacy snapshots and a pre-existing lower 0.25% commission. Both populated actual OCI schema fixtures converged with the fresh normalized schema. The final full-application ORM validator then caught the parent's newly added `shipment_email_queued_at` column missing in the copied V6; V6 has been resynced with its source. The final three-case migration rerun is pending at the time of this report and must pass before claiming the migration acceptance complete.

Local PostgreSQL verification uses the explicit authorized loopback PostgreSQL 18.1 server on port 5099 and random `mercato_foundation_<UUID>` disposable databases only; credentials are read privately from backend/.env and environment values restored. Created databases are closed and dropped afterward. The original local application databases and OCI databases are not reset. The first sandbox-only socket attempt was denied by Windows; authorized elevated runs succeeded.

## Remaining release evidence

The default PostgreSQL tests start PostgreSQL 14.24 Testcontainers and fail if Docker is absent; there is no silent skip. Docker was unavailable locally, so the local PostgreSQL 18.1 run does not constitute the mandatory PG14.24 Testcontainers CI result. The required CI result, production schema adoption/migration, OCI restore/backup and deployed HTTPS/gateway/mail verification remain external release gates. The complete repository suite is parent-owned and is not claimed passed by this scoped foundation result.

## Files

- `backend/src/main/java/com/mercato/Jobs/`: facade, store, record, handlers, typed failures, worker and JPA schema mappings.
- `backend/src/test/java/com/mercato/jobs/`: H2 jobs tests, PostgreSQL concurrency/core tests, disposable database support and migration acceptance.
- `backend/src/main/resources/db/migration/V1__*` through `V6__*`: recorded baseline and explicit upgrades.
- `backend/src/main/resources/application.properties`, `application-prod.properties`, `application-demo.properties`: schema ownership, OSIV, demo separation and requested MFA/monitor environment mappings.
- `backend/scripts/verify-postgres-foundation.ps1`, `compare-legacy-schema.ps1`: explicit local PostgreSQL acceptance and read-only adoption check.
- `docs/sql/2026-10-hardening-jobs.sql`, `docs/DATABASE_MIGRATIONS.md`, this report.

## Final root verification, 24 October 2026

See docs/HARDENING_REVIEW.md for the final 185-test backend run, 72-test frontend run, scoped dependency assessments, Browser evidence and remaining external gates. Earlier pending/partial runs above are historical. Local PostgreSQL 18.1 passed; PostgreSQL 14.24 Testcontainers remains required in CI. No live deployment or paid resource was created.
