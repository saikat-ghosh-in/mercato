# Encrypted off-instance backup and recovery

These are reviewed source templates and operator procedures, not a claim that backups are installed or proven. Production is gated on a real private OCI repository, independent key recovery, an off-instance restore, measured RPO/RTO, and delivered failure/missing-heartbeat alerts. Do not replace those gates with unit tests or manually invented evidence.

The policy is four weekly full chains, differential backups on the other six days, and continuous WAL. The target is RPO no more than 15 minutes and end-to-end RTO no more than four hours, including replacement-host provisioning and key retrieval. An OCI instance or its attached volume is not an off-instance backup, especially where idle-instance reclamation is possible.

## Repository and keys

Provision a dedicated **private Standard-tier OCI Object Storage bucket**, separate from the database instance and its boot/data volumes. Use a different bucket, prefix, customer secret, and encryption passphrase for staging and production. Do not add public access, public preauthenticated requests, lifecycle expiration, archive-tier transitions, or Object Storage cleanup jobs that can remove pgBackRest data. pgBackRest owns expiration and retains four full chains plus their required WAL. Object versioning, if enabled, also needs separately budgeted old-version storage.

A physical backup includes every database in its cluster. A staging repository may only point at an isolated staging cluster; never back up a shared production cluster with lower-trust staging credentials.

Use the OCI S3 compatibility endpoint for the actual namespace/region and path-style requests. The template uses the commercial `oraclecloud.com` realm; another OCI realm requires an explicit reviewed validator change, not a TLS bypass. Give the backup identity bucket metadata read access and only the object permissions needed in its dedicated repository. Retention requires object deletion; the recovery identity should instead have read-only repository access. Use a service gateway/private regional service path where available and test its DNS, TLS and IAM behavior. OCI CLI bucket metadata is checked on each backup/monitor run, so a private-bucket change cannot silently pass the next check. [OCI S3 compatibility](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/s3compatibleapi.htm), [OCI customer secret keys](https://docs.oracle.com/en-us/iaas/Content/Identity/access/working-with-customer-secret-keys.htm).

Keep the pgBackRest passphrase in an independently recoverable vault, with a second recovery method outside the instance. The OCI customer secret is separate from this passphrase. Loss of the passphrase makes the encrypted repository unusable. A copied boot disk containing both backups and the only keys does not pass the recovery gate. Recover the actual passphrase on a separate host, record its SHA-256 fingerprint and the private vault/drill reference, and refresh that proof at least every 90 days. Never commit keys, ping URLs, live evidence containing identifiers, or copied environment files.

## Prepare the host without changing production traffic

Use Python 3.12, PostgreSQL client/server tools matching the source major version (the recorded database is PostgreSQL 14), pgBackRest 2.60 or newer with `verify`, and a working OCI CLI. Verify package signatures/source provenance through the host's normal package process. The scripts do not install packages or alter services.

Review `ops/pgbackrest/pgbackrest.conf.example`, `backup.env.example`, and `postgresql-archive.conf.example` against the real cluster path, socket, namespace, region and bucket. Install the agent as `/usr/local/lib/mercato/backup_agent.py` and the filled configuration as `/etc/pgbackrest/pgbackrest.conf`. Keep the agent and configuration root-owned, with no write access for application/database roles. The environment file is root:postgres mode **0640**: the PostgreSQL archiver must read it, but application users and the public must not. Values are parsed as data, without shell expansion. Do not use `export`, command substitution, multiline secrets, or inline comments in values.

Create the root-owned empty include directory `/etc/pgbackrest/empty.d`. Additional configuration includes are intentionally blocked. Create these postgres-owned directories with mode 0700, adapting the unit read/write paths if the real data directory differs:

- `/var/lib/pgbackrest/lock`
- `/var/spool/pgbackrest`
- `/var/log/pgbackrest`
- `/var/lib/mercato-backup`
- `/var/lib/mercato-restore-drill`

Fill **real** capacity and key-recovery evidence at the private paths in `backup.env`. The supplied JSON examples intentionally fail closed. Capacity evidence must be no older than 30 days. Measure the whole physical cluster, worst observed daily differential and daily WAL storage. Before the first measured backup, use a conservative physical uncompressed cluster estimate; replace it with actual repository measurements after the first backup and a representative write window. Account for all databases/tablespaces in the cluster, OCI quota, budget, temporary overlap, WAL spikes and object-version overhead.

The minimum repository budget is `1.20 × (5 × fullBytes + 28 × maxDifferentialBytes + 35 × dailyWalBytes)`. The next full is included before expiration. Local WAL free space must exceed both 512 MiB and twice the measured daily WAL volume. Low capacity fails the job and alerts; it never silently lowers four-chain retention. Confirm actual object storage consumption/budget and host filesystem capacity with the operator evidence, rather than treating this estimate as an OCI reservation.

As postgres, after filling all values and independent key proof:

```sh
python3 /usr/local/lib/mercato/backup_agent.py stanza-create
```

Then install the reviewed PostgreSQL archive settings through the cluster's normal include mechanism and perform the controlled restart needed by `wal_level`/`archive_mode`. The archive command writes encrypted WAL to the off-instance repository. `archive_timeout=300s` limits quiet-period segment delay. Synchronous archive push is deliberate; PostgreSQL retries failures, and no queue-size limit is allowed to discard WAL. Check PostgreSQL archiver errors and free space immediately.

```sh
python3 /usr/local/lib/mercato/backup_agent.py preflight
python3 /usr/local/lib/mercato/backup_agent.py backup full
python3 /usr/local/lib/mercato/backup_agent.py monitor
```

The backup operation checks policy and the remote archive, completes the backup, checks the archive again, reads encrypted repository metadata, and runs `verify` for the completed backup set. Only then does it send success. No real backup is claimed until these commands have completed against the intended bucket. The strict command/config options and environment precedence follow the [pgBackRest command reference](https://pgbackrest.org/command.html) and [configuration reference](https://pgbackrest.org/configuration.html).

## Schedules and independent alerts

Review and install `ops/systemd/` units only after the first full backup passes. The templates run a full backup Sunday 02:00 UTC, differential backups Monday–Saturday 02:00 UTC, and a remote WAL/backup monitor every five minutes. Backup timers are persistent and add up to ten minutes of jitter. The monitor verifies WAL arrival even on an idle database, a complete backup within 26 hours, a full within eight days, the private bucket, capacity and key recovery proof. A long host outage may make both persistent backup timers due; resolve competing runs by completing a full backup first and inspect the failed run rather than hiding it.

```sh
systemd-analyze verify /etc/systemd/system/mercato-backup@.service /etc/systemd/system/mercato-backup-monitor.service /etc/systemd/system/mercato-backup-*.timer
systemctl daemon-reload
systemctl enable --now mercato-backup-full.timer mercato-backup-diff.timer mercato-backup-monitor.timer
systemctl list-timers 'mercato-backup*'
```

Configure **separate Healthchecks.io checks** before enabling timers. Keep their UUID URLs secret. Suggested settings: daily-backup cron `0 2 * * *`, UTC, grace 4h20m (including jitter and service timeout); WAL/backup monitor period 5m, grace 3m. The application's separate one-minute outgoing heartbeat and three-minute absence alert are owned by the application operations implementation, not this script.

Set external alert delivery to `OPS_ALERT_EMAIL`, falling back to `ADMIN_EMAIL` when unset, and verify delivery through the external monitor's own email integration. It must work when this instance is down and when Brevo SMTP credentials are unavailable. Backup start/fail/success signals do not contain database data. A failed ping does not abort the underlying backup; a missing success is detected independently. [Healthchecks start/grace behavior](https://healthchecks.io/docs/measuring_script_run_time/), [failure signaling](https://healthchecks.io/docs/signaling_failures/).

On staging, prove actual alert delivery for a failed backup, failed WAL upload/check, and a missing monitor heartbeat. Use staging-only denied credentials or a disconnected staging host; never delete or corrupt production backup objects to test monitoring. Record start, detection and delivered-email timestamps, then restore the staging settings. Confirm that a failure never emitted a success ping. Recheck that application alerts remain independent from these backup monitors.

## Off-instance restore drill and release gate

Record the real UTC `DRILL_STARTED_AT` **before** preparing the source probe and provisioning the replacement host. Keep that timestamp in independent operator records; do not reset it when the restore binary starts. Record source/fresh machine IDs, backup version/set, repository identity, key recovery reference, and actual timings privately.

Provision the explicit `backup_probe` schema once with `ops/pgbackrest/restore-probe.sql` on the approved source. It grants no public access and does not change application/Flyway tables. Source probe creation is an explicit write, never a background job:

```sh
python3 /usr/local/lib/mercato/backup_agent.py prepare-drill --allow-probe-write --output /var/lib/mercato-backup/probe-UNIQUE.json
```

This commits a unique marker with core-table counts, creates a named PostgreSQL restore point, and confirms that its WAL reached the remote repository. `simulatedFailureAt` is recorded after that real archive check. Transfer the protected probe record to a **different machine**. Do not carry the only keys in that file. Recover the passphrase separately from the vault on the new host, generate matching key recovery proof with that host's `/etc/machine-id`, and retain the external start time. Prepare the filled configuration there with a read-only recovery S3 credential and OCI metadata-read identity.

On the fresh host, create only the dedicated drill root. The source `PGDATA` value remains a reference and must not overlap the new target. The target below must not already exist; choose a short direct child name so the Unix socket path stays within the platform limit:

```sh
python3 /usr/local/lib/mercato/backup_agent.py restore-drill \
  --probe /var/lib/mercato-backup/probe-UNIQUE.json \
  --target /var/lib/mercato-restore-drill/drill-UNIQUE \
  --started-at "$DRILL_STARTED_AT" \
  --output /var/lib/mercato-backup/restore-UNIQUE.json
python3 /usr/local/lib/mercato/backup_agent.py gate --evidence /var/lib/mercato-backup/restore-UNIQUE.json
```

The drill refuses the source machine, existing targets, symlinks/nested escapes and production path overlap. It restores to the named point, starts an isolated socket-only PostgreSQL instance on port 55432, overrides the data/config paths, disables archiving, and verifies the marker and readable user/order/payment/refund tables before stopping it. It checks separate-host key proof and measures RPO from source marker to simulated failure, and RTO from the operator's original start to completion. Both must pass 900s/14400s. No target is automatically deleted and no service is attached to the recovered data.

Retain private evidence and inspect the drill logs before any explicit cleanup. The basic table checks do not replace application-level finance reconciliation or checkout/session tests against the isolated recovered database. Do those with jobs, real payments and mail disabled. Before a real cutover, reconcile durable jobs and external captures/refunds; never launch two active writers against the same payment obligations. Do not connect a live application merely because a JSON gate passed.

Production remains blocked until an operator has reviewed the actual upload, repository verification, off-instance restore, separately recovered keys, measured RPO/RTO, capacity and delivered alert evidence. Rehearse again after material database, backup, key, storage, or recovery-host changes. The repository contains no fabricated passing restore evidence.

The script validates timing, marker, path and recovery fields; an operator must still attest the independently captured start time, vault recovery record and real service logs. Editing JSON flags by hand does not produce acceptable release evidence.

## CI and dependency gates

`application-checks.yml` runs frontend tests/lint/build and full npm audit, the full backend suite with mandatory Docker-backed PostgreSQL Testcontainers, and a report checker requiring the populated/fresh normalized schema comparison and PostgreSQL concurrency tests to execute without skips. OCI schema provenance and upgrade rules remain in `docs/DATABASE_MIGRATIONS.md`; H2 does not satisfy those tests.

The Java job builds a resolved direct/transitive CycloneDX SBOM and scans it with Trivy for HIGH/CRITICAL advisories, including unfixed findings. Missing scanner databases/network failures fail the job. Two exact-version, non-executable view-rendering findings have explicit OpenVEX assessments plus a runtime registration regression guard; see `docs/DEPENDENCY_SECURITY.md`. Do not mark the security scan clean based only on a dependency declaration: use the resolved BOM and current advisory database. The final frontend has 72 tests, lint/build success and zero npm advisories after compatible fixes. [CycloneDX Maven plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/), [Trivy action](https://github.com/aquasecurity/trivy-action).

## Free-tier-only boundary, 24 October 2026

The user has no existing backup bucket/S3 profile or Healthchecks checks and requested that non-permanently-free services be skipped. No paid service, trial, subscription, bucket or monitoring account was created. Read-only OCI inspection found no pgBackRest, OCI CLI/profile or Caddy command, no `/etc/pgbackrest`, and `archive_mode=off`; the instance has about 38 GiB free locally. Local disk space does not satisfy off-instance recovery.

[Healthchecks Hobbyist](https://healthchecks.io/pricing/) is currently an ongoing $0/month tier for 20 checks, sufficient for the worker, backup and WAL checks. The user still needs to establish the account/checks and email destination. We cannot promise a third party will never change its terms.

[OCI Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) includes Object Storage for the life of the account within its home-region allowances: 20 GB combined storage for an Always-Free-only account; paid/trial accounts have 10 GB per storage tier; both list 50,000 Object Storage API requests/month. Verify the actual tenancy state, existing usage, region, storage and request headroom before enabling the Standard repository. Four chains plus WAL and request traffic must fit the unused free allowance; do not reduce retention or enable paid overflow to make it fit. Quotas and current pricing must be rechecked at setup. If it cannot fit, leave this live integration disabled and record the unmet recovery target.

Backup/upload/restore, heartbeat delivery and configured real mail/payment checks remain unverified, rather than being presented as passed or replaced with same-instance backups. No production authentication or gateway cutover was attempted.
