# Deploy Mercato to OCI and Netlify

The GitHub repository is `saikat-ghosh-in/mercato`. The active frontend is `frontend/` inside this repository. The obsolete sibling `../mercato-frontend` is not part of the build and has been archived locally and removed.

## This upgrade needs a one-time cutover

Do **not** deploy this revision by overwriting the old JAR and restarting alone. Authentication now uses JDBC cookie sessions, CSRF and administrator MFA; Hibernate validates a Flyway-managed schema, and production Spring binds privately behind the authenticated Netlify proxy. The previous bearer-token release is not an authentication rollback target.

Rehearse on the beta database/service/site first. The production and beta services share the VM: do not replace their ports, certificates or database configuration together unintentionally.

1. Back up the actual database and uploaded files, copy the backup off the VM, and prove restoration. The free-only operations options and still-unconfigured off-instance backup/alert gates are documented in [the operations runbook](docs/OPERATIONS_BACKUP_RUNBOOK.md). No paid service is required or enabled by this code. A local backup alone cannot survive loss of the VM.
2. Stop the target API and workers. Follow [DATABASE_MIGRATIONS.md](docs/DATABASE_MIGRATIONS.md): compare a current schema-only dump with the recorded actual OCI baseline, explicitly baseline the matching existing schema at version 1, then migrate through V7 with the Flyway CLI. Never enable automatic baselining, Hibernate `update`, Flyway clean, or drop business tables to force startup. Keep PostgreSQL 14 compatibility. Fresh empty databases execute V1–V7 normally.
3. Follow [INGRESS_CUTOVER.md](docs/INGRESS_CUTOVER.md) to configure the dedicated DuckDNS/Caddy HTTPS origin, private Spring binding and signed Netlify rewrite/Edge Function. Inspect the existing VM listeners before assigning ports 80/443. Spring now uses loopback HTTP port 8080 by default; Caddy owns TLS. Preserve the second service's separate port and hostname. Remove obsolete SSL/8443/forwarding overrides from external Spring config.
4. Configure production environment privately. Retain the existing `DB_*`, `BREVO_*`, `CASHFREE_*`, `JWT_SECRET`/`JWT_TTL`, administrator and upload settings referenced by `application-prod.properties`. Set `SPRING_PROFILES_ACTIVE=prod`, canonical `FRONTEND_BASE_URL`, matching `NETLIFY_PROXY_KEYS`, `NETLIFY_SITE_ID`, `NETLIFY_SITE_URL`, `NETLIFY_DEPLOY_CONTEXTS`, and `MERCATO_MFA_ENCRYPTION_KEY` (base64-encoded random 32-byte key). Persist and back up that encryption key separately; replacing it loses enrolled TOTP factors. Production rejects demo profiles, review credentials and MFA bypass.
5. Configure Netlify from the **repository root**, with the root `netlify.toml`, build command `npm --prefix frontend ci --ignore-scripts && npm --prefix frontend run build`, and publish directory `frontend/dist`. Set `MERCATO_ORIGIN` (Builds scope) and secret `NETLIFY_PROXY_SIGNING_KEY` (Runtime and Builds scopes); the signing value must match an accepted backend proxy key. Use the site IDs and separate beta/production values in the ingress runbook. Do not use a `VITE_` secret or direct backend API base URL; browser requests use same-origin `/api`. Uploading `frontend/dist` alone omits required Edge/plugin configuration.
6. Coordinate the new backend and Netlify deploys during the cutover. Keep the exact legacy Cashfree webhook/return adapters enabled until outstanding old orders and retries drain; switch new order metadata to the Netlify routes as documented. Payment returns never prove capture. Verify raw webhook bytes, fast durable acknowledgments, forged forwarding headers, origin rejection, Secure cookies/CSRF, administrator MFA enrollment, guest cart merge, payment reload and configured Cashfree sandbox/mail flows. Start beta CSP report-only, then enforce; production acceptance requires enforced CSP.
7. Only after the target configuration, migrations and beta checks are complete, create the operator acknowledgment file `/opt/mercato/config/cookie-release-ready`. The redeploy script checks this marker; it is an acknowledgment, not an automated proof of the external release gates. For beta use its own app directory and marker.

The existing service command is `java -jar /opt/mercato/mercato-backend.jar --spring.config.additional-location=file:/opt/mercato/config/`, using `/opt/mercato/.env`. Never print that file or put its values in GitHub releases. The release does not include local `.env`, databases, uploaded files or credentials.

## Publish a new GitHub release

1. Wait for the merged `main` revision's Application checks to pass.
2. On GitHub, create a **new tag targeting main**, for example `v0.1.0`, and publish its release. Do not replace `v0.0.1` or reuse an old tag.
3. Wait for **Release backend** to finish. It reruns the frontend, PostgreSQL/backend, operations and dependency gates, builds the exact tagged revision, and attaches `mercato-backend.jar`, `mercato-backend.jar.sha256` and `redeploy.sh`. It refuses to overwrite assets silently. Do not deploy until the release assets and workflow success are visible.

## Install the safer redeploy script once

On your Windows machine, after the changes are merged, copy the reviewed script to the VM (or download the same release asset):

```powershell
scp -i C:\Users\007sa\Downloads\ssh-key-2026-02-24.key C:\Projects\mercato\mercato-backend\ops\redeploy.sh ubuntu@80.225.239.145:~/redeploy.sh
ssh -i C:\Users\007sa\Downloads\ssh-key-2026-02-24.key ubuntu@80.225.239.145
```

Keep `/opt/mercato` writable by the existing deployment user, and retain the service's read access to installed JARs. The script needs `curl`, `sha256sum`, `flock`, `install`, `python3` and non-interactive permission to restart **only** the intended service. Do not grant unrestricted passwordless sudo for deployments.

## Subsequent deployments

After the one-time cutover and release checks:

```bash
bash ~/redeploy.sh v0.1.0
sudo journalctl -u mercato -n 100 --no-pager
```

Replace the example with your actual new release tag. The script downloads to a temporary directory, validates the checksum and artifact size, retains the previous JAR, installs by atomic rename, restarts the service, and checks the session contract through Netlify for up to roughly seven minutes. It does not modify the database itself or fall back to insecure authentication. A failed readiness check needs investigation; an earlier artifact is retained for a **compatible** operator-directed rollback. Review schema compatibility first.

Beta can use the same script with its verified settings:

```bash
MERCATO_APP_DIR=/opt/mercato-beta MERCATO_SERVICE=mercato-beta MERCATO_SITE=https://mercato-in-beta.netlify.app bash ~/redeploy.sh v0.1.0
```

After deployment, verify `/products`, sign-in/MFA, Help/Account, bag quantities and totals, both checkout steps, saved addresses, orders and seller/admin reads on mobile and desktop. Inspect worker/notification failures and run the configured gateway sandbox tests before enabling real transactions. Verify that the frontend and backend are from the intended release; a GitHub release does not itself deploy the OCI service.

## Local build and checks

Use Java 17 and Node 22. Frontend: `npm ci`, `npm test`, `npm run lint`, `npm run build` from `frontend/`. Backend: `bash mvnw -B test package` from `backend/`, with Docker available for PostgreSQL Testcontainers. Output is `backend/target/mercato-backend.jar`. Windows uses `mvnw.cmd`. Production uses `ddl-auto=validate` and OSIV disabled; local demo is a separate in-memory profile and must never run against production.
