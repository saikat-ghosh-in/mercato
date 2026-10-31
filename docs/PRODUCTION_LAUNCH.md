# Production launch and redeployment

The storefront is `https://mercato-in.netlify.app`, backed by the OCI service `mercato`, `/opt/mercato`, and the HTTPS origin `https://sg-mercato-backend.duckdns.org`. Beta remains separate at `mercato-in-beta.netlify.app` and service `mercato-beta`.

## Fresh launch

The launch uses a new PostgreSQL database `mercato_prod_launch` on the existing private OCI PostgreSQL server. Separate migration-owner/application roles own and access it. Flyway runs every migration from an empty schema, with automatic baselining disabled and Hibernate set to validate. The old production database is retained, backed up and disconnected from the new application; it is not imported.

Only the seven existing catalog products, their required categories, ordered rich details, gallery images and current physical inventory are copied from beta. Products belong to the newly bootstrapped production `launchadmin`; old seller/customer accounts are not imported. Reservations start at zero. Orders, payments, carts, sessions, notifications, seller requests, promotions and shipment history start empty. Imported review attribution is set to the new administrator instead of retaining old account references.

The production administrator is created from the private `ADMIN_*` environment settings, using the initial launchadmin credentials already configured for beta. This is a newly created account, so set up its authenticator and save new recovery codes on the production site. Beta MFA codes/recovery records are not copied. Subsequent redeploys retain the production MFA encryption key and database.

The service runs Java 17 as the dedicated `mercato-prod` runtime user on `127.0.0.1:8080`; Nginx terminates HTTPS. Java/PostgreSQL ports remain private. Production Netlify uses its own signing key, exact site ID/URL and enforced CSP. Only signed requests through that site's proxy can reach application/image endpoints. The new frontend is published after the compatible backend is ready.

Despite the production hostname, this personal project uses **Cashfree sandbox and Easyship REST sandbox only**. No paid shipping, labels, pickups or live payment APIs are enabled. Day-based tracking simulation follows the beta behavior; production gets a separate sandbox webhook subscription. Brevo sends actual account and transactional email through the existing verified sender.

## Future releases

1. Merge the reviewed `beta` changes into `main`. Use `[skip netlify]` for a coordinated backend/frontend rollout so a Git push cannot publish the frontend prematurely.
2. Wait for **Application checks** on the exact `main` commit. Publish a new stable GitHub release from that commit (for example `v0.1.1`; never reuse a tag), without the prerelease checkbox.
3. Wait for **Release backend** to succeed and attach `mercato-backend.jar`, its `.sha256`, and `redeploy.sh`.
4. Connect to OCI:

   ```powershell
   ssh -i C:\Users\007sa\Downloads\ssh-key-2026-02-24.key ubuntu@80.225.239.145
   ```

5. Take a database backup, then run the installed deployment wrapper, replacing the example with your new tag:

   ```bash
   bash ~/redeploy.sh v0.1.1
   sudo systemctl status mercato --no-pager
   ```

   The wrapper downloads the checksummed release, preserves the previous JAR, restarts only `mercato`, and checks the production session endpoint. It does not recreate the database or copy beta data. Do not replace the production environment file or MFA/signing keys with beta's values.

6. In Netlify project **mercato-in**, trigger a deploy from **main**. Confirm the published commit matches the release. Leave project **mercato-in-beta** unchanged.
7. Verify Sign in by username/email, production admin MFA, public images/catalog, bag totals, sandbox checkout, orders and tracking. An unsigned direct request to the DuckDNS origin must return `403`.

Keep `/opt/mercato/config/cookie-release-ready` and the runtime's access to the uploaded images. Do not restore the old bearer-token application against this database. Local deployment backups remain on the instance; off-instance backup/monitoring services have not been purchased or provisioned.
