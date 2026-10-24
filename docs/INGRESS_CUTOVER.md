# HTTPS ingress and gateway cutover

This is a reviewed local configuration and verification procedure. It has not changed the live OCI services or either Netlify project.

The connected account exposes production `mercato-in` (`51c7e917-5665-4240-b15c-57bc7b31413e`) and beta `mercato-in-beta` (`02941023-e0ec-44f6-a289-4680a89b46a9`). The current owned upstream hostnames are `sg-mercato-backend.duckdns.org` and `sg-mercato-backend-beta.duckdns.org`; their old Cashfree webhook URLs use `/api/public/cashfree/webhook`. Public storefront origins are their corresponding HTTPS Netlify hostnames. Caddy was not found by the read-only server inventory. Inspect the existing listener/reverse-proxy service before assigning ports80/443; never replace both applications unintentionally.

## Trust boundary

`netlify/proxy-paths.mjs` is the shared path inventory: `/api`, `/api/*`, `/images`, `/images/*`. Tests compare the signed rewrites with Edge coverage. The static declarations in `netlify.toml` preserve Netlify's Runtime-secret signing contract; the local build plugin replaces only the upstream origin, keeps resolved signing metadata, and fails when configuration or signed declarations are absent. The old publish-directory SPA-first rule has been removed.

Native connection-IP provenance has not been established on the deployed signed proxy. The selected implementation is explicitly **Edge v1**; there is no native-header fallback. Before release, probe the deployed native header against current platform guarantees; omitting Edge is a separately reviewed configuration change only if all forgery tests prove it trustworthy. The current implementation always uses `context.ip`, strips caller metadata, covers every proxied path/method including Cashfree, never parses the body, and declares `onError: "fail"`. Test an intentionally crashing copy in an isolated preview before accepting deployment. Do not enable split testing, bypass rewrites, or custom-error routes that skip this boundary.

The backend verifies HS256, expiry, issuer, site ID, site URL and deploy context before forwarding/session filters. This authenticates the proxy origin, not the request/IP/body. It then requires the Edge metadata and derives scheme/host from configured canonical HTTPS values. Invalid/duplicate metadata is rejected. Container forwarding is disabled; explicit filter order is origin10, forwarding20, API headers30, then Spring Session50 relative to highest precedence. The pass-through CORS filter never short-circuits OPTIONS. Public API/session/payment responses use no-store.

## Configuration

Netlify environment values must be supplied through its secret/environment interface, never client-prefixed variables or committed files:

- `MERCATO_COOKIE_CUTOVER_READY=yes`: non-secret Builds acknowledgment, set only when the matching schema, cookie backend, Caddy origin and signing configuration are ready. Leave unset while the old OCI release is running: merges then cannot automatically publish the incompatible frontend. This is a cutover acknowledgment, not proof of deployed acceptance checks.
- `MERCATO_ORIGIN`: the matching dedicated Caddy HTTPS hostname, Builds scope.
- `NETLIFY_PROXY_SIGNING_KEY`: a random secret of at least32bytes, Runtime **and Builds** scopes (the build validates presence); separate production/beta values. Keep it secret, including build logs.
- `MERCATO_CSP_MODE=report-only` for initial beta observation; set `enforce` after evidence. The default is enforce. Scripts permit only self and the Cashfree SDK; no wildcard, inline script or eval is allowed. Fonts/images/frame/connect origins are explicit. Style attributes remain allowed for existing React components.
- `MERCATO_HSTS_SECONDS=300` initially. Increase production to31536000 after HTTPS verification. No preload/includeSubDomains is configured.

Backend production environment additionally supplies `NETLIFY_PROXY_KEYS` (current key, optionally previous key separated by a comma), `NETLIFY_SITE_ID`, `NETLIFY_SITE_URL`, `NETLIFY_DEPLOY_CONTEXTS=production`, `FRONTEND_BASE_URL`, the MFA encryption key, and private server port. `application-prod.properties` binds loopback HTTP; `ops/Caddyfile` owns TLS/certificate renewal. Use distinct private ports for the two applications. No metrics or health path bypass exists. Uploaded image URLs use the storefront origin; the frontend maps verified historical owned upload URLs through `/images` without changing stored records.

## Verification and deliberate gateway transition

1. Apply the database adoption, backup and tested release procedure first. Validate Caddy config/certificate renewal and private-only Spring binding. Deploy beta behind its own origin/site identity. Do not cut production cookies over using a partially configured transport.
2. Verify `/api`, descendants and owned image paths, IPv4/IPv6, mixed-case and duplicate headers, spoofed Forwarded/X-Forwarded/native-IP headers, raw signed-body hashes, multiple cookies, no-store, direct-origin rejection, OPTIONS, Edge crashes and uncovered-path attempts. Confirm valid webhook acknowledgement below2seconds under normal load. Local byte-preservation tests are not deployed proof.
3. Initially enable `LEGACY_CASHFREE_ADAPTER_ENABLED=true`. Only exact POST `/api/public/cashfree/webhook` and GET `/api/public/cashfree/return` have the old-origin adapter. The webhook still requires Cashfree HMAC; neither adapter uses forwarded IP/session identity. No other origin exception exists. The legacy return validates only an order identifier and redirects to the canonical frontend; it never proves payment.
4. Configure new order metadata/webhook settings to `https://<storefront>/api/public/cashfree/webhook-v2` and canonical frontend payment return. Old and new webhook controllers share the same durable inbox and attempt deduplication. Keep old return navigation valid for existing gateway orders.
5. Maintain an inventory of outstanding legacy gateway orders and last legacy traffic time without logging signatures/cookies/bodies. Reconcile every legacy capture/refund. Retire the old adapter only after gateway retries drain and seven consecutive days show no legacy traffic; otherwise retain the exact adapter.
6. Exercise cookie/CSRF rotation, MFA, expired sessions, cross-tab account switches, guest-cart merge, checkout reload, actual Cashfree sandbox callbacks and configured mail. Enforce CSP and complete off-instance backup/restore/dead-man checks before production acceptance.

Rotation: provision the new signing value on both sides while the backend accepts current+previous; deploy/verify the site on the new key, wait out signed-request expiry and in-flight retries, then remove the previous backend key. Never expose the key/JWS in diagnostics. Authentication rollback uses a cookie-compatible release and never restores bearer tokens.

## Remaining external gates

Connected Netlify access is confirmed. No draft/live deploy, Caddy install/reload, DNS edit, gateway switch or database adoption has been performed. Those actions require the completed application release plus real deployed probes; signing secrets, independent monitoring checks and off-instance backup credentials/capacity evidence must be configured. Local Node/Java tests prove the implementation, not those service gates.
