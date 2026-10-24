# Session and authentication implementation evidence

Status: source implementation and the cookie fixture cutover are in the shared local branch; no commit or staging was performed. The first complete targeted session run passed 17 tests using Java 17. Additional regressions and migrated commerce fixtures are awaiting the coordinated full run; they are not represented as passed below.

## Browser API contract

- `GET /api/auth/session` and successful password sign-in return `{authenticated,user,contextId,expiresAt,mfa:{state}}`. `user` is the sanitized profile DTO when fully authenticated, otherwise null. `expiresAt` is the immutable absolute expiry. States are `NONE`, `ENROLL_REQUIRED`, `CHALLENGE_REQUIRED`, `STEP_UP_REQUIRED`, and `VERIFIED`.
- `GET /api/auth/csrf` returns `{token,headerName}`. Mutations require that header and `X-Account-Context`. Any supplied stale context on other API reads is also rejected. The two GET bootstraps can recover a stale context. Mismatch is HTTP 409 with `code: SESSION_CONTEXT_CHANGED`.
- `POST /api/auth/signout` invalidates the current session and returns the anonymous bootstrap contract. Password reset, account access changes, voluntary deactivation, seller approval/rejection and operator MFA recovery increment `accessVersion` and revoke indexed sessions. Voluntary reactivation increments it again.
- `POST /api/auth/legacy-guest-cart` accepts `{guestToken}`. Only a currently valid, signed `type=guest` JWT can be claimed, and the claim has a unique durable hash. An anonymous session adopts it; an authenticated session merges it. The cookie and context rotate. Authentication bearer JWTs never authenticate the security chain.

## MFA APIs

All endpoints below require CSRF and the session context. Pending MFA sessions do not authorize privileged APIs.

- `POST /api/auth/mfa/enroll`, body `{}`, returns `{secret,otpauthUri}`.
- `POST /api/auth/mfa/verify`, body `{code}`, returns the session contract. Confirming enrollment or replacement additionally returns `recoveryCodes`: exactly ten single-use strings. They are full 32-character uppercase hexadecimal values and are never returned again.
- `POST /api/auth/mfa/recovery`, body `{code}`, returns the session contract after atomically consuming one recovery hash.
- `POST /api/auth/mfa/replace`, body `{code}`, returns `{secret,otpauthUri}`. An established TOTP proof, or established MFA/recovery proof within five minutes in this same session, is required. A recent proof permits a blank code. The context changes only when `/verify` confirms the replacement.
- `POST /api/auth/step-up/send`, body `{}`, durably queues a short email challenge and returns `{message}`. `/api/auth/step-up/verify`, body `{code}`, consumes it and returns the session contract. The challenge is bound to the session, expires after five minutes, and permits five attempts. Password proof for a pending challenge expires after ten minutes.
- `POST /api/auth/mfa/operator-recovery`, body `{userId,reason,expectedVersion}`, is restricted to another administrator with established MFA in the last five minutes. It checks the target version, records `MFA_OPERATOR_RECOVERY` in the existing account audit, removes the target authenticator, increments the target version, and revokes the target sessions. Self recovery is forbidden. It returns `{message}`. This is a dedicated recovery command; it does not alter the read-only administrator management rules.

MFA seeds use AES-256-GCM with the stable user ID as authenticated data. Recovery values are hashed. TOTP steps cannot be reused. Pending enrollment/replacement confirmation is bound to its initiating session, so an abandoned replacement cannot block another session's established authenticator. MFA consumption and customer step-up commit before a privileged session is established. Password sign-in similarly commits account updates and cart merge before issuing its session.

## Session and abuse behavior

Spring Session JDBC holds a minimal serializable identity: stable user ID, access version, original authentication time, immutable absolute expiry and MFA state. The stable ID is explicitly indexed for revocation. A password-free request principal is reconstructed from the authoritative account every request; the password-bearing `UserDetailsImpl` is never persisted in the session.

Strictest-role timeouts: customer idle 24 hours / absolute 7 days; seller idle 1 hour / absolute 24 hours; administrator idle 30 minutes / absolute 12 hours. MFA completion does not extend the original window. Production cookies are `__Host-mercato-session`, Secure, HttpOnly, SameSite=Lax, Path=/, without Domain. The separately named insecure cookie exists only in the guarded loopback demo.

Database buckets store counters and retry deadlines. Failed password attempts trigger progressive client/account delays; successful sign-ins do not consume those failure budgets. Broad IP controls cover attempts. Timestamped account failures enforce the rolling fifty-failure/hour customer step-up across client addresses. Existing sessions are unaffected; deadlines expire and do not permanently lock accounts. Recovery/email requests have account and IP budgets and HTTP 429 includes Retry-After. HTTP threads do not sleep.

The `EnvironmentPostProcessor` guard is registered in `META-INF/spring.factories`, before migrations, seeds and listener beans. Staging/production rejects demo/test combinations, the demo bypass and demo credentials. Demo requires memory H2, loopback binding/URLs/origins and disabled payment credentials. The seed encryption property is `mercato.auth.mfa-encryption-key`, bound by configuration to `MERCATO_MFA_ENCRYPTION_KEY` (32 random bytes in base64).

## Verification

Executed targeted run: `SessionSecurityTest`, `SessionLifecycleTest`, `SessionBoundaryTest`, `SeedCipherTest`, `MfaLifecycleTest`, and the original two `CookieSessionIntegrationTest` methods: **17 tests, zero failures/errors**. This included real Spring Session cookie/CSRF requests, customer sign-in/signout and stale-context rejection, administrator enrollment/TOTP/replay, immutable expiry, seed encryption binding, ignored bearer identity, and the abandoned-replacement/recovery/failed-commit regressions. The normal sandbox denied Java HttpClient's internal loopback connection during context creation; the same suite passed when elevated. This is an environment restriction, not an external HTTPS result.

Additional regressions now present for password-reset durable mail and indexed revocation, sign-in commit failure, operator recovery audit, startup ordering, signed guest migration, rolling account step-up and persistent backoff. Their latest coordinated result must be appended after execution. Six existing integration fixtures now use real cookie/CSRF/context bootstrap through `CookieBrowserHarness`; no bearer compatibility mode was added. `ApplicationFlowTest` uses two anonymous sessions and merges the actual guest browser at login. Explicit stale-read header tests remain in the session suite; ordinary domain GET fixtures use cookies without the optional read context header.

PostgreSQL authentication races, deployed HTTPS cookie/header behavior, real authenticator/mobile flows, delivered email challenges and audited operator identity verification remain required acceptance evidence. Mock/H2 runs do not establish those external results. Production MFA key provisioning and an independently recoverable operator process are deployment requirements; no credential or bypass was fabricated.

### Coordinated run update, 24 October

The root's `commerce-session-migration-run.log` and the individual Surefire reports now confirm all selected newer auth regressions passed: `AuthSigninCommitTest` (1), `CookieSessionIntegrationTest` (6), `MfaLifecycleTest` (4), `StartupGuardTest` (1), zero failures/errors in those 12 selected tests. Combined with the unchanged earlier utility/boundary classes, the session suite has 24 individually verified tests. The combined 50-test run itself was **not green**: it had 9 errors, including 2 password-reset unit fixture errors because Mockito did not inject the field-based persistence context. The unit fixture now explicitly injects its EntityManager mock; its rerun remains pending. The other combined-run errors were root-owned commerce fixture configuration/teardown issues. The six migrated domain fixtures have not yet been represented as passing.

### Cookie-domain verification, 24 October

The focused elevated run in `backend/target/cookie-domain-run.log` completed with exit 0. All six migrated fixtures passed without weakening their commerce assertions: `AccountAccessTest` 6, `AdminUserActivityTest` 5, `ApplicationFlowTest` 1, `MarketplaceFinancePromotionTest` 7, `MarketplaceJourneyTest` 11, `RichProductFlowTest` 1 (**31 domain tests**). Seller approval now additionally verifies the previous cookie is revoked and signs in again before checking the new selling access.

The same run also passed `PasswordResetServiceImplTest` (3), `CorsBoundaryTest` (1) and `SessionSecurityTest` (7): **42 tests, zero failures/errors** in that focused run. The earlier two password-reset fixture errors are resolved. CORS no longer grants origin/header access or short-circuits OPTIONS; the exact legacy GET payment return skips session logic. After an observed missing-ingress RED failure, the pre-startup guard now rejects incomplete deployed signed-ingress configuration, non-HTTPS public/site origins, public binding, backend SSL and unverified automatic forwarding. A complete private HTTPS ingress configuration passes its guard test. This does not establish deployed HTTPS or signed proxy behavior; those are separately verified by the ingress owner.

### PostgreSQL consumption acceptance added

`PostgresAuthConsumptionTest` now exercises two simultaneous consumers on a fresh disposable real PostgreSQL database using synthetic accounts. The reset test requires exactly one successful password change, one access-version increment, one call of the real indexed revocation bean, and deletion of both actual Spring Session JDBC records. The recovery test requires exactly one successful proof, exactly one elevated administrator session, and removal of the recovery hash. Unexpected exceptions are not treated as a successful losing contender. The test defaults to the mandatory PostgreSQL 14.24 Testcontainers support; the explicitly configured loopback PostgreSQL alternative remains separate evidence. It overrides both the JDBC driver and Hibernate dialect and closes its context before cleaning up support-owned databases. Execution is part of the root's pending complete backend run; no PostgreSQL auth pass is claimed yet.

## Final root verification, 24 October 2026

See docs/HARDENING_REVIEW.md for the final 185-test backend run, 72-test frontend run, scoped dependency assessments, Browser evidence and remaining external gates. Earlier pending/partial runs above are historical. Local PostgreSQL 18.1 passed; PostgreSQL 14.24 Testcontainers remains required in CI. No live deployment or paid resource was created.
