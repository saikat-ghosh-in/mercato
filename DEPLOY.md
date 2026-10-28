# Beta first: fresh Mercato launch on OCI + Netlify

**Already installed beta:** use section 11 for updates. Do not repeat the initial database/account setup or regenerate encryption keys. The fulfillment revision uses additive migrations and preserves existing accounts, MFA, orders and uploads. Keep delayed authorization disabled until its enabled-account sandbox acceptance is recorded; deploy the compatible financial reader before changing Cashfree's account-wide settings.

This guide replaces **only mercato-beta** with an **empty database and no old accounts, orders, listings, sessions or cart data**. It does not import or preserve the old application's data. Use a new database named `mercato_beta_launch`; there is no need to reset another database to launch this one.

**Skip the legacy adoption procedure for a fresh database:** no manual Flyway baseline or old-order import is needed. Keep the checked-in migrations: on an empty database, Flyway runs all versioned migrations automatically before Hibernate validates the schema. On an already installed beta database, it applies only pending migrations, including their explicit compatibility backfills. Do not manually import the old schema, enable automatic baselining, or change Hibernate to `update`.

The repository is [saikat-ghosh-in/mercato](https://github.com/saikat-ghosh-in/mercato). The active frontend is `frontend/` inside that repository. The deployment targets are:

- Storefront: `https://mercato-in-beta.netlify.app`
- Netlify project ID: `02941023-e0ec-44f6-a289-4680a89b46a9`
- Backend HTTPS origin: `https://sg-mercato-backend-beta.duckdns.org`
- OCI server: `ubuntu@80.225.239.145`
- Service/application directory: `mercato-beta` / `/opt/mercato-beta`
- Private Java listener: `127.0.0.1:8081`

Perform the numbered steps in order. The beta storefront may be unavailable during replacement. **Do not stop or change production**: preserve service `mercato`, `/opt/mercato`, database `mercato`, the shared `mercato-admin` database role, production Nginx configuration and Netlify project `mercato-in`. Beta uses its own database roles, runtime user, port, signing key and MFA encryption key.

SSH inventory on 25 October 2026 confirmed both old services are active, PostgreSQL is private on port 5432, and Nginx already owns ports 80/443 with separate hostname configurations. Retain Nginx for this beta-only cutover. The original Caddy replacement is deferred until a separate production ingress change; do not install a competing listener on these shared ports.

## 1. Publish a new GitHub release

For the email-delivery repair and monthly admin/seller reports, see [Email operations](docs/email-operations.md). The additive `V8` migration runs during the normal release upgrade; keep the existing beta database and MFA keys. Production-profile releases enable monthly reports by default (`EMAIL_REPORTS_ENABLED=false` disables them).

1. Wait for **Application checks** on the exact release commit to pass. Beta prereleases target `stg`; stable production releases target `main`. Beta's frontend and backend must use the same application revision.
2. For beta, create a **GitHub prerelease** with a new tag such as `v0.1.0-beta.2` targeting the checked `stg` commit. Stable releases use a tag such as `v0.1.0`, must not be marked prerelease, and must descend from `main`. The release workflow enforces tag type and branch ancestry. Never move or reuse a tag.
3. Publish it and wait for **Release backend** to finish successfully. It reruns the frontend, PostgreSQL/backend, Linux operations and dependency checks, then attaches:
   - `mercato-backend.jar`
   - `mercato-backend.jar.sha256`
   - `redeploy.sh`

Do not proceed until all three assets are present. Publishing a release does not restart OCI or publish the Netlify site.

## 2. Connect to OCI and prepare the application directories

Run this in **Windows PowerShell**:

```powershell
ssh -i C:\Users\007sa\Downloads\ssh-key-2026-02-24.key ubuntu@80.225.239.145
```

All subsequent shell commands are run **on the Ubuntu VM**, unless marked otherwise.

```bash
sudo systemctl stop mercato-beta
java -version
sudo ss -ltnp
getent ahostsv4 sg-mercato-backend-beta.duckdns.org
```

Use Java 17. The DuckDNS hostname must resolve to this VM. Preserve the existing Nginx listeners and production site; step 5 edits only the beta hostname. Keep PostgreSQL private, and allow inbound TCP 80/443 through the OCI security list/NSG and the VM firewall. Keep SSH available; do not open PostgreSQL or Java port 8081 to the internet.

Create a dedicated runtime user if it does not exist, while keeping `ubuntu` as the deployment user:

```bash
id mercato-beta >/dev/null 2>&1 || sudo useradd --system --user-group --home-dir /opt/mercato-beta --shell /usr/sbin/nologin mercato-beta
sudo usermod -aG mercato-beta ubuntu
sudo install -d -o ubuntu -g mercato-beta -m 2750 /opt/mercato-beta /opt/mercato-beta/config /opt/mercato-beta/releases
sudo install -d -o mercato-beta -g mercato-beta -m 0750 /opt/mercato-beta/uploads
```

Reconnect your SSH session after the group change. The fresh database will not reference old uploaded images; there is no need to copy or delete the old image files as part of launch.

## 3. Create the empty PostgreSQL database

Use the PostgreSQL 14 instance already running on the VM; this procedure does not upgrade the database engine. These names must be new: if a command says a role/database already exists, inspect it rather than silently adopting it. PostgreSQL remains on loopback; the example uses port 5432, so change `DB_URL` below if your instance uses another port.

Create separate migration-owner and application roles. Enter strong, different passwords at the prompts, and retain them privately for step 4:

```bash
sudo -u postgres createuser --login --no-superuser --no-createdb --no-createrole --pwprompt mercato_beta_launch_owner
sudo -u postgres createuser --login --no-superuser --no-createdb --no-createrole --pwprompt mercato_beta_launch_app
sudo -u postgres createdb --owner=mercato_beta_launch_owner --encoding=UTF8 --template=template0 mercato_beta_launch
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d mercato_beta_launch <<'SQL'
REVOKE ALL ON DATABASE mercato_beta_launch FROM PUBLIC;
GRANT CONNECT ON DATABASE mercato_beta_launch TO mercato_beta_launch_owner, mercato_beta_launch_app;
ALTER SCHEMA public OWNER TO mercato_beta_launch_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO mercato_beta_launch_owner;
GRANT USAGE ON SCHEMA public TO mercato_beta_launch_app;
ALTER DEFAULT PRIVILEGES FOR ROLE mercato_beta_launch_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mercato_beta_launch_app;
ALTER DEFAULT PRIVILEGES FOR ROLE mercato_beta_launch_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO mercato_beta_launch_app;
SQL
```

Do not run any `baseline`, `clean`, schema-import or manual V1 SQL command. The packaged application will perform the fresh migrations in step 7. The limited audit-table grants are finalized after those tables exist.

## 4. Configure the backend environment

Generate **three different values**, running this command separately for each one:

```bash
openssl rand -base64 32
```

Use them for:

- `MERCATO_MFA_ENCRYPTION_KEY`: encrypts authenticator secrets. Keep a recovery copy separately; do not regenerate it on redeploy.
- `NETLIFY_PROXY_KEYS`: the backend's proxy signing secret. Copy the **same value** into Netlify's `NETLIFY_PROXY_SIGNING_KEY` in step 8.
- `JWT_SECRET`: a new legacy guest-token signing key. It is not browser login authentication; deployed user bearer tokens remain rejected.

Edit the existing environment file privately:

```bash
sudoedit /opt/mercato-beta/.env
```

Replace obsolete settings instead of appending duplicate keys. Retain your actual Brevo/Cashfree provider credentials if they are still valid; application data is fresh, but you do not need new provider accounts. Use the following template and replace **every `REPLACE_...` value**. Use quotes around values containing spaces or special characters; this is a systemd `EnvironmentFile`, not a shell script. Do not `source` it or commit it.

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8081

DB_URL=jdbc:postgresql://127.0.0.1:5432/mercato_beta_launch
DB_USERNAME=mercato_beta_launch_app
DB_PASSWORD=REPLACE_APP_DATABASE_PASSWORD
SPRING_FLYWAY_USER=mercato_beta_launch_owner
SPRING_FLYWAY_PASSWORD=REPLACE_OWNER_DATABASE_PASSWORD

FRONTEND_BASE_URL=https://mercato-in-beta.netlify.app
FILE_UPLOAD_DIR=/opt/mercato-beta/uploads
FILE_BASE_URL=https://mercato-in-beta.netlify.app/images
IMAGES_BASE_URL=https://mercato-in-beta.netlify.app/images

NETLIFY_PROXY_KEYS=REPLACE_PROXY_SIGNING_SECRET
NETLIFY_SITE_ID=02941023-e0ec-44f6-a289-4680a89b46a9
NETLIFY_SITE_URL=https://mercato-in-beta.netlify.app
NETLIFY_DEPLOY_CONTEXTS=production
LEGACY_CASHFREE_ADAPTER_ENABLED=false
MERCATO_MFA_ENCRYPTION_KEY=REPLACE_MFA_ENCRYPTION_KEY
JWT_SECRET=REPLACE_NEW_BASE64_GUEST_TOKEN_KEY
JWT_TTL=86400000

ADMIN_USERNAME=REPLACE_NEW_ADMIN_USERNAME
ADMIN_PASSWORD=REPLACE_STRONG_ADMIN_PASSWORD
ADMIN_EMAIL=REPLACE_ADMIN_EMAIL
ADMIN_PHONE=REPLACE_ADMIN_PHONE

BREVO_USERNAME=REPLACE_BREVO_SMTP_LOGIN
BREVO_SMTP_KEY=REPLACE_BREVO_SMTP_KEY
EMAIL_FROM=REPLACE_VERIFIED_SENDER_EMAIL
OPS_ALERT_EMAIL=REPLACE_ADMIN_EMAIL

CASHFREE_APP_ID=REPLACE_SANDBOX_APP_ID
CASHFREE_SECRET_KEY=REPLACE_SANDBOX_SECRET_KEY
CASHFREE_BASE_URL=https://sandbox.cashfree.com/pg
CASHFREE_API_VERSION=2025-01-01
CASHFREE_RETURN_URL=https://mercato-in-beta.netlify.app/checkout/payment-confirmation
CASHFREE_NOTIFY_URL=https://mercato-in-beta.netlify.app/api/public/cashfree/webhook-v2
```

Use Cashfree **sandbox only**: set `CASHFREE_BASE_URL=https://sandbox.cashfree.com/pg` with sandbox credentials. The frontend SDK is fixed to sandbox and the backend rejects production gateway URLs; loopback remains available only for disabled demo/test adapters. `VITE_CASHFREE_MODE` cannot enable live payments. Never place gateway/SMTP/database secrets in a `VITE_` variable. [Cashfree integration guidance](https://www.cashfree.com/blog/building-cashfree-agent-skills-a-task-aware-knowledge-layer-for-ai-coding-assistants/).

The admin is created on the first successful startup using `ADMIN_*`. Do not use `reviewer`, `ReviewOnly!2026`, the demo profile or an MFA-bypass setting: deployed startup rejects them. Changing `ADMIN_PASSWORD` later does not reset an account already created.

```bash
sudo chown root:root /opt/mercato-beta/.env
sudo chmod 600 /opt/mercato-beta/.env
```

Systemd reads this file and supplies the values to the service; the runtime user does not need to read it directly.

### How the first administrator gets an account

There is no separate `SUPER_ADMIN` role: `ADMIN` is the highest current role. **Do not use public registration for the first administrator.** Before the first backend startup, set your own `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` and `ADMIN_PHONE` in the beta environment file. Use a real email address and a strong password distinct from your production credentials. Keep these values private.

On an empty database, the startup initializer creates that account automatically, hashes the password, marks its email verified and grants administrator access (plus existing shopper/seller capabilities). No emailed invitation is required for this bootstrap account. Ordinary registration cannot grant administrator privileges.

After the matching beta frontend is deployed, open `https://mercato-in-beta.netlify.app/login` and sign in with those values. Select **Start setup**, add the displayed setup key to your authenticator app (or use **Open authenticator app**), enter its six-digit code, save the ten single-use recovery codes privately, confirm they are saved and continue. Administrator actions remain unavailable until MFA verification completes. Open **Account → Admin dashboard** to manage the marketplace.

Future logins require the password plus an authenticator/recovery code. Keep `MERCATO_MFA_ENCRYPTION_KEY` stable. Changing `ADMIN_PASSWORD` in `.env` after the account exists does not reset its password; use password recovery. Use this setup once per genuinely fresh database, not for each redeploy.

## 5. Adapt only the existing beta Nginx HTTPS site

Do not stop Nginx or install Caddy during this beta-only deployment. Production shares Nginx's ports 80/443. The beta configuration is `/etc/nginx/sites-available/mercato-beta`, enabled through `/etc/nginx/sites-enabled/mercato-beta`.

```bash
sudo nginx -t
sudoedit /etc/nginx/sites-available/mercato-beta
```

Keep beta's existing port-80 redirect/ACME handling and port-443 TLS/certificate directives. The installed certificate paths are:

- `/etc/letsencrypt/live/sg-mercato-backend-beta.duckdns.org/fullchain.pem`
- `/etc/letsencrypt/live/sg-mercato-backend-beta.duckdns.org/privkey.pem`

Inside **only the beta HTTPS server block**, replace the old catch-all proxy to `https://127.0.0.1:8444` with these locations and response headers. Remove conflicting old beta locations; do not append a second catch-all. Preserve certificate renewal configuration.

```nginx
client_max_body_size 6m;
add_header Strict-Transport-Security "max-age=300" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;

location ~ ^/(api|images)(/|$) {
    proxy_pass http://127.0.0.1:8081;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header Connection "";
    proxy_set_header Forwarded "";
    proxy_set_header X-Forwarded-For "";
    proxy_set_header X-Forwarded-Host "";
    proxy_set_header X-Forwarded-Proto "";
    proxy_set_header X-Real-IP "";
    proxy_cache off;
    proxy_no_cache 1;
    proxy_cache_bypass 1;
}
location / {
    return 403;
}
```

The proxy keeps the request URI, method, body, Netlify signature/normalized metadata and cookies. Do not configure `proxy_set_body`, reinterpret client forwarding headers or strip `X-Nf-Sign`. Spring checks signed origin authenticity before using the Edge metadata. See the [official Nginx proxy reference](https://nginx.org/en/docs/http/ngx_http_proxy_module.html).

```bash
sudo nginx -t && sudo systemctl reload nginx
sudo openssl x509 -in /etc/letsencrypt/live/sg-mercato-backend-beta.duckdns.org/fullchain.pem -noout -dates -checkend 86400
```

Reload only after syntax validation succeeds. Verify production still responds after the reload. Check the existing ACME renewal timer and renew beta's certificate if needed; a certificate file existing is not proof that it is valid. Spring now uses private HTTP on port 8081, so the new service does not need the old Java keystore or public port 8444. Before the new backend starts, beta proxy 502 is expected. Afterwards direct beta-origin API calls must return 403 because they lack Netlify authentication.

## 6. Download and verify the release

Set `release_tag` to the unused tag you actually published in step 1:

```bash
release_tag=v0.1.0-beta.1
release_tmp=$(mktemp -d)
cd "$release_tmp"
release_url="https://github.com/saikat-ghosh-in/mercato/releases/download/$release_tag"
curl --fail --location --retry 3 --output mercato-backend.jar "$release_url/mercato-backend.jar"
curl --fail --location --retry 3 --output mercato-backend.jar.sha256 "$release_url/mercato-backend.jar.sha256"
curl --fail --location --retry 3 --output redeploy.sh "$release_url/redeploy.sh"
sha256sum --check mercato-backend.jar.sha256
```

Stop if the checksum check fails. With the old service stopped, install the checked artifact and the new script:

```bash
sudo install -o ubuntu -g mercato-beta -m 0640 mercato-backend.jar /opt/mercato-beta/mercato-backend.jar
sudo install -o ubuntu -g mercato-beta -m 0750 redeploy.sh /opt/mercato-beta/redeploy-release.sh
cat > ~/redeploy-beta.sh <<'BASH'
#!/usr/bin/env bash
set -Eeuo pipefail
export MERCATO_APP_DIR=/opt/mercato-beta
export MERCATO_SERVICE=mercato-beta
export MERCATO_SITE=https://mercato-in-beta.netlify.app
exec bash /opt/mercato-beta/redeploy-release.sh "$@"
BASH
chmod 0750 ~/redeploy-beta.sh
```

**Do not use `redeploy.sh` for this first startup.** It requires a ready Netlify session endpoint and an acknowledgment marker, which you establish in steps 8–10. Subsequent deployments use the script normally.

## 7. Start the backend and let Flyway create the schema

Edit only the beta service:

```bash
sudoedit /etc/systemd/system/mercato-beta.service
```

Use this unit. The explicit classpath config location ignores old `/opt/mercato-beta/config/application.*` and other external Spring files, preventing obsolete SSL, schema-update or profile overrides from leaking into the fresh launch. All deployment values come from the environment file.

```ini
[Unit]
Description=Mercato beta backend
Wants=network-online.target
After=network-online.target postgresql.service

[Service]
Type=simple
User=mercato-beta
Group=mercato-beta
WorkingDirectory=/opt/mercato-beta
EnvironmentFile=/opt/mercato-beta/.env
ExecStart=/usr/bin/java -jar /opt/mercato-beta/mercato-backend.jar --spring.config.location=classpath:/
Restart=on-failure
RestartSec=5
TimeoutStartSec=180
SuccessExitStatus=143
UMask=0027
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

If the existing service has additional systemd drop-ins, inspect `sudo systemctl cat mercato-beta` and remove only conflicting beta overrides before proceeding.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now mercato-beta
sudo systemctl status mercato-beta --no-pager
sudo journalctl -u mercato-beta -n 120 --no-pager
sudo ss -ltnp
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d mercato_beta_launch -c 'SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;'
```

Wait for startup to complete. Expect successful migrations **1 through 7**, the configured beta administrator, no demo catalog/accounts and a listener on **127.0.0.1:8081**. If startup fails, read the journal instead of setting Hibernate to `update` or enabling automatic baseline.

Now that the tables exist, restrict the runtime role's audit/history permissions:

```bash
sudo -u postgres psql -X -v ON_ERROR_STOP=1 -d mercato_beta_launch <<'SQL'
REVOKE UPDATE, DELETE ON TABLE public.account_access_events, public.durable_job_retry_audit FROM mercato_beta_launch_app;
REVOKE ALL ON TABLE public.flyway_schema_history FROM mercato_beta_launch_app;
SQL
```

Flyway continues to use the separate owner credentials on future releases. The application retains SELECT/INSERT on its append-only audit tables and normal permissions on its other tables.

## 8. Configure and deploy Netlify

Open the existing [mercato-in-beta project](https://app.netlify.com/projects/mercato-in-beta). Link it to `saikat-ghosh-in/mercato`, production branch **stg**. This is the beta site's published branch: its Netlify deploy context is still **`production`**, despite the site's beta name. Do not change the separate production project. Use the repository-root `netlify.toml`:

- Base directory: repository root (empty or `.`), not `frontend`.
- Build command: `npm --prefix frontend ci --ignore-scripts && npm --prefix frontend run build`
- Publish directory: `frontend/dist`
- Node: 22, already declared in the file.
- No stale package-directory setting pointing to the removed outer frontend.

The root build plugin, signed rewrites and Edge Function must all deploy. Do not drag-and-drop `frontend/dist` alone. File-based settings take precedence over conflicting UI settings. [Netlify configuration reference](https://docs.netlify.com/build/configure-builds/file-based-configuration/).

Set these **production-context** variables in Netlify's environment settings:

- `MERCATO_ORIGIN=https://sg-mercato-backend-beta.duckdns.org` — Builds.
- `NETLIFY_PROXY_SIGNING_KEY` — select **Contains secret values**; use the exact value of backend `NETLIFY_PROXY_KEYS`, in this site's **production context only**. Include Runtime and Builds. On this site's plan, a secret automatically uses **Builds, Functions, Runtime**, excluding Post processing; use those supported scopes, rather than an unsupported subset or `All scopes`. Keep Deploy Preview, branch, agent-runner and local-development values unset. [Signed-proxy requirements](https://docs.netlify.com/manage/routing/redirects/rewrites-proxies/).
- `MERCATO_COOKIE_CUTOVER_READY=yes` — Builds; set now, after the fresh beta backend/schema, Nginx HTTPS origin and matching signing key are ready. Without it the build deliberately fails.
- `MERCATO_CSP_MODE=enforce` — Builds. If a third-party integration needs investigation, use `report-only` during pre-launch testing, resolve the exact allowance, then restore `enforce` before public acceptance.
- `MERCATO_HSTS_SECONDS=300` — Builds initially; increase to `31536000` after HTTPS/certificate behavior is verified.
- Cashfree SDK mode is fixed to sandbox; no `VITE_CASHFREE_MODE` variable is needed. Remove any old production-mode value.

Remove the old direct-origin `VITE_API_BASE_URL` setting if present; the browser now calls same-origin `/api`. Keep secrets out of Git and client-prefixed variables.

Trigger a **published deploy from stg on the beta project** after saving the variables. Check its build log for successful secure-ingress plugin and Edge Function deployment. Frontend-only local builds passing do not prove this deployed proxy path.

## 9. Verify HTTPS, email, MFA and the empty marketplace

From the VM:

```bash
curl --fail --show-error --silent https://mercato-in-beta.netlify.app/api/auth/session
curl --fail --show-error --silent https://mercato-in-beta.netlify.app/api/auth/csrf
curl --include --silent https://sg-mercato-backend-beta.duckdns.org/api/auth/session
```

The first response must be session JSON containing `authenticated` and `contextId`, not the SPA HTML. CSRF must return a token/header name. The direct-origin request must return **403**. In browser network tools, confirm host-only `Secure`, `HttpOnly`, `SameSite=Lax` session cookies and no cached private/session responses.

Then, in Browser on desktop and mobile:

1. Sign in using the new `ADMIN_USERNAME`/`ADMIN_PASSWORD`, enroll the required authenticator and save the single-use recovery codes privately. Full administrator access is withheld until MFA completes.
2. Expect an empty catalog and empty analytics. Create categories and real products in admin, then add stock through inventory. The deployed `prod` profile does not seed the disposable demo products or `reviewer` account.
3. Register and verify a fresh shopper email. Confirm Brevo actually delivers verification/reset mail from the configured sender. Exercise sign-in/out, bag quantities, addresses, delivery selection, checkout reload, orders and failed-request Retry.
4. Exercise seller registration, administrator approval, listing requests and fulfillment using fresh test accounts before opening selling access publicly.
5. Check uploaded images, search, filters, Account/Help access, mobile layout and loading/error states. Investigate actual browser console/CSP failures before accepting the launch.

A reset database removes the need to restore old data. Automatic off-instance backups and external dead-man alerts are still unconfigured: this procedure does not buy or provision services. The outstanding protection/acceptance gates for **new** data are described in [the operations runbook](docs/OPERATIONS_BACKUP_RUNBOOK.md).

## 10. Configure Cashfree for new orders and complete first installation

In the matching **sandbox** Cashfree account, configure:

- Allowed storefront domain: `https://mercato-in-beta.netlify.app`.
- Webhook: `https://mercato-in-beta.netlify.app/api/public/cashfree/webhook-v2`.
- Return base: `https://mercato-in-beta.netlify.app/checkout/payment-confirmation`. The backend appends `?order_id=...` to each newly created order.
- The payment and refund event subscriptions supported by this integration.

Use only the new routes for this fresh project; leave `LEGACY_CASHFREE_ADAPTER_ENABLED=false`. There is no old-order inventory or seven-day legacy-adapter drain to carry over. A return-page visit is never proof that money was captured.

Complete sandbox success/failure/cancellation/refund flows. Verify signed webhook delivery through Netlify, unchanged raw-body signature verification, durable acknowledgment below two seconds under normal load, correct orders/stock/earnings/refunds and delivered notifications. Use [the ingress acceptance procedure](docs/INGRESS_CUTOVER.md) for forwarded-header forgery, Edge failure and signed-origin tests; its **legacy database/old-order transition sections do not apply** to this launch. Real service behavior remains unverified until these deployed tests pass.

For beta, after the joined backend/Netlify session path, migrations, CSRF and administrator MFA gate checks succeed, create the redeploy acknowledgment marker. This permits subsequent beta updates; it does not approve live payments, public selling or production release:

```bash
sudo install -o ubuntu -g mercato-beta -m 0640 /dev/null /opt/mercato-beta/config/cookie-release-ready
```

Permit `ubuntu` to restart only this service without an interactive sudo prompt, so the script can run. Check `command -v systemctl`, then use `sudo visudo -f /etc/sudoers.d/mercato-beta-deploy` and add the matching absolute path (Ubuntu normally uses `/usr/bin/systemctl`):

```sudoers
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart mercato-beta
```

Validate with `sudo visudo -c`. Do not grant unrestricted passwordless sudo. The marker acknowledges completed setup; it does not replace the checks above.

## 11. Subsequent releases: your usual redeploy command

The fulfillment beta remains a **side-project sandbox**. Keep Cashfree authorization/delayed-capture activation disabled and use ordinary sandbox payment collection. Existing admin shipping/delivery actions are the target flow. New parcel and customer-return systems, advanced RTO, deductions and seller disputes are deferred. Do not enable account-wide card preauthorization to try out unfinished features.

For each beta update, publish a **new prerelease tag from tested `stg`** and wait for its **Release backend** job/assets. Before a release containing new migrations, take a private recovery dump of the installed beta database on OCI:

```bash
sudo install -d -o root -g root -m 0700 /opt/mercato-beta/backups
sudo bash -c 'umask 077; runuser -u postgres -- pg_dump --format=custom --dbname=mercato_beta_launch > /opt/mercato-beta/backups/before-upgrade-$(date -u +%Y%m%dT%H%M%SZ).dump'
```

Verify the dump with `sudo pg_restore --list /opt/mercato-beta/backups/<actual-file>.dump`, substituting its actual filename, without restoring it. A dump on this VM is a short-term migration recovery aid; it does not satisfy the off-instance backup production gate. Do not automatically restore it over a running application or roll back an incompatible schema.

Then update only beta:

```bash
bash ~/redeploy-beta.sh v0.1.0-beta.5
sudo journalctl -u mercato-beta -n 100 --no-pager
```

Replace the example with the actual tag. The beta wrapper fixes the directory, service and site explicitly; the generic release script otherwise defaults to production. The updated script downloads/verifies the JAR, rejects changed content under an existing tag, preserves the previous artifact, installs by atomic rename, restarts the service and checks session JSON through Netlify. Retrying the same unchanged release after a failed restart is supported. Future additive migrations run automatically on startup; do not recreate this fresh database for routine updates.

The beta Netlify project deploys its `stg` frontend separately; verify frontend/backend compatibility for each release. Preserve `.env`, the MFA encryption key, uploads, database and signing keys across redeploys. A failed readiness check requires investigation; do not automatically restore the old bearer-token application or roll back an incompatible database schema.

## 12. Delete obsolete beta data and artifacts after acceptance

After the fresh beta database/migrations and joined HTTPS authentication checks pass, retire beta's old data. Full gateway/mail and seller/business acceptance remains necessary before public selling. The 25 October inventory found old database **`mercato_beta`**, old Java listener **8444** and old script **`~/redeploy-beta.sh`**. The new installation replaces that script and active beta JAR in place. It uses `mercato_beta_launch` and private port 8081.

1. Verify the new beta service is active, Flyway history exists in `mercato_beta_launch`, the new session endpoint works and no process listens on 8444. Do not delete the service unit or active JAR: their names are reused by the new deployment.
2. Confirm no remaining application connections target `mercato_beta`, and verify its name against the active beta `DB_URL` without printing passwords. Then drop only the old `mercato_beta` database. Do not use force termination to bypass unexpected connections; investigate them first.
3. Inspect beta's upload directory before removing old images. If it already contains new test uploads, retain those and remove only identified obsolete files. The old inventory showed no beta upload files, so no blanket upload purge is needed.
4. Remove obsolete beta Java keystore/external Spring files only after confirming the new service uses classpath configuration and Nginx owns TLS. The beta `config/` directory was empty during inspection; retain the new `config/cookie-release-ready` marker.
5. Remove only identified old beta release backups when no longer needed. Keep the current release, its checksum, `.env`, encryption/signing keys and new uploads. Never delete `/opt/mercato-beta` as a whole.

**Leave production alone:** database `mercato`, role `mercato-admin` (shared by both old deployments), service `mercato`, `/opt/mercato`, `~/redeploy.sh`, production TLS/Nginx files and production Netlify settings. Do not drop shared database roles or remove PostgreSQL, Nginx or certificate-renewal services.

Production deployment and production cleanup are a separate step after beta approval. Use separate production database/credentials/keys and the production site identity; do not point production at the beta database or copy beta sessions/MFA data.

## Troubleshooting

- **Netlify says cutover is not ready:** complete steps 3–7, then set `MERCATO_COOKIE_CUTOVER_READY=yes` in the correct site/context and redeploy.
- **403 through Netlify:** check the exact signing-secret match, site ID, canonical HTTPS site URL, Runtime scope, `production` context and deployed Edge coverage. Never make the API generally public to work around this.
- **502:** inspect the beta Nginx routing and `mercato-beta` journal, private port 8081 and VM firewall/TLS setup.
- **Flyway sees a non-empty unversioned schema:** `DB_URL` points to the old database. Use the empty `mercato_beta_launch` database; do not baseline it blindly.
- **Database permission error:** verify the datasource role, Flyway owner credentials and default grants from step 3. Do not give the runtime account superuser privileges.
- **MFA encryption/startup error:** use a stable base64 encoding of exactly 32 random bytes and the `prod` profile only.
- **Admin password differs from `.env`:** initialization creates a missing administrator; it does not overwrite an existing one. Use the account recovery path rather than expecting a redeploy to reset passwords.
- **Release asset 404:** the tag is wrong or **Release backend** has not uploaded the assets successfully yet.

The beta deployment was performed on 25 October 2026. See [the beta deployment review](docs/BETA_DEPLOYMENT_REVIEW.md) for the installed release, cleanup performed, verification evidence and remaining manual acceptance. Steps here describe the installation procedure; do not recreate the database or regenerate keys on an already installed beta instance.
