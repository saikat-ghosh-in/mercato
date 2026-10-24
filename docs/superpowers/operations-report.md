# Operations source implementation report

24 October 2026. Scope: backup/recovery/CI follow-up only. No Git staging/commits, production commands, OCI writes, service installation or deployment occurred. Frontend remained unchanged after its completed handoff.

## Source delivered

- `ops/backup_agent.py`: standard-library Python commands for protected environment parsing, private Standard-tier OCI bucket checks, AES-256 pgBackRest policy, four-chain retention validation, measured capacity/key-recovery gates, full/differential backups, remote repository verification, and separate backup/WAL heartbeats. Backup freshness is evaluated after upload completion. Failure never emits a success heartbeat, and ping outages do not abort the underlying backup.
- `ops/pgbackrest/`: filled-in-by-operator examples for repository, secret environment, continuous PostgreSQL WAL, capacity evidence, independent key proof, and an explicitly provisioned private restore-probe schema. Example values/null measurements intentionally fail closed.
- `ops/systemd/`: weekly Sunday full, Monday–Saturday differential, and five-minute verified-WAL/backup monitor units/timers. Backup jobs have a four-hour service timeout. No units were installed or started.
- Named-point restore drill: a real source marker is committed and archived; recovery must run on another machine, in a new guarded directory outside production data. The isolated recovered cluster has archiving/network listening disabled. It verifies the exact marker, core table readability, separate-host key proof, RPO <=15m and end-to-end RTO <=4h including provisioning/key retrieval, then stops the drill cluster. It never deletes a target.
- `.github/workflows/application-checks.yml`: frontend test/lint/build/npm audit; full backend Testcontainers suite with Docker required and external-DB fallback unset; required non-skipped PostgreSQL schema/concurrency XML evidence; Python policy tests and Linux systemd syntax validation; resolved Java CycloneDX SBOM plus HIGH/CRITICAL Trivy scanning and report artifacts.
- `scripts/assert-postgres-reports.py`: build failure for missing, failed, skipped or incomplete required PostgreSQL schema/concurrency evidence.
- `docs/OPERATIONS_BACKUP_RUNBOOK.md`: configuration/installation review, OCI/key/capacity preparation, independent alerts, staging failure drills, actual off-instance restore commands and explicit release gates.
- Ignore files under `ops/` and `scripts/` keep caches, live environment files and private evidence out of Git.

## Validation and red/green evidence

The initial ten policy tests failed at the missing implementation assertion before the agent was created. Tests exercise real policy/orchestration code with native processes/network replaced at their external boundary. Later red/green cycles caught missing restore chronology validation, slow archive verification settings, a backup freshness clock evaluated before a long upload, and a preexisting healthy backup incorrectly satisfying a new-run success check.

Latest local unit run: 15 tests, 14 passed, 1 skipped because this Windows sandbox cannot create directory symlinks. The same test is enabled on Linux CI. Python compilation passed for the backup agent and PostgreSQL report checker. Report-checker tests reject missing, skipped, failed and errored required cases and accept complete fixtures.

Workflow YAML was parsed using the already-installed `js-yaml` package, checking all four jobs, the empty external-PostgreSQL fallback and required report-checker step. `git diff --check` passed for operations/workflow/docs (CRLF normalization warning only). CLI help passed. No new test/runtime packages were installed. Linux-native systemd/pgBackRest/PostgreSQL/OCI behavior is not represented by these local unit checks.

Running the required-PostgreSQL report checker against the reports currently saved under `backend/target/surefire-reports` failed closed on `com.mercato.Service.CashfreeFinalizeConcurrencyTest` because the saved suite contains failed/skipped evidence. Root was notified to distinguish stale reports from its final backend run. This is not a passing PostgreSQL/CI acceptance claim.

## External gates still open

No actual private backup bucket, independent secret retrieval, capacity measurement, encrypted upload, repository verification, off-instance recovery, RPO/RTO measurement, or delivered backup/dead-man alert was performed by this agent. The checked-in examples deliberately cannot pass those gates. Root owns actual infrastructure and the application outgoing heartbeat/private operational endpoints.

Windows cannot run systemd. A read-only Git Bash syntax command failed at MSYS process initialization with `NtCreateDirectoryObject ... 0xC0000022`; it did not execute the script. Linux workflow/native validation remains required and is not marked passed. No GitHub workflow run or live Trivy scan was performed by this agent.

The root task independently reported compatible npm dependency updates, advisories reduced to zero, and post-update 66/66 frontend tests plus lint/build passing (main chunk 540.01 kB). Those are root evidence, not new operations-agent executions. The new Java advisory job must still run against the resolved dependency BOM; no Java advisory-free claim is made here.

No service deployment writes or frontend edits are part of this increment. All starting dirty UI and protected files remain outside operations ownership.

## Final root verification, 24 October 2026

See docs/HARDENING_REVIEW.md for the final 185-test backend run, 72-test frontend run, scoped dependency assessments, Browser evidence and remaining external gates. Earlier pending/partial runs above are historical. Local PostgreSQL 18.1 passed; PostgreSQL 14.24 Testcontainers remains required in CI. No live deployment or paid resource was created.
