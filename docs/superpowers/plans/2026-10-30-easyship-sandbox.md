# Easyship sandbox tracking implementation plan

> Execute inline using superpowers:executing-plans. Continue the approved minimal parcel workflow; the user requested Easyship instead of waiting for Delhivery and explicitly prefers direct implementation without extra design rounds.

**Goal:** Track Easyship sandbox parcels in Mercato, including safe automatic delivery, without changing payment, stock or marketplace rules.

**Approach:** Reuse ShipmentService, scoped endpoints, the existing job worker and ShipmentPanel. Add a sandbox REST adapter and explicit provider/reference persistence. MCP is unsuitable for sandbox execution: its documentation warns that actions have no separate test environment. No MCP action, production API, paid label or pickup is in scope.

**Evidence:** The provided sandbox token returned HTTP 200 for a read-only shipment listing from OCI using curl. Current v2024 sandbox host is `public-api-sandbox.easyship.com`; older endpoint pages mention a different host. Python's default HTTP client was blocked by the provider's firewall before authentication. Credentials remain outside source and test fixtures.

## Constraints

- Work on stg; beta only. Preserve README/local configuration edits; main and production unchanged.
- Require a `sand_` token and hard-code the current sandbox host. No redirect or production fallback.
- Seller handoff still requires exact packed quantities, verified funding, operation identity and order version.
- An Easyship parcel stores its Easyship shipment ID separately from its courier tracking number. Never reinterpret old Delhivery records as Easyship records.
- Match both returned shipment ID and tracking number. Missing, ambiguous, reverse or invalid-time data cannot complete delivery.
- Keep carrier HTTP outside transactions, preserve correction fences and Retry-After, and use bounded requests compatible with sandbox limits.
- Manual delivery remains available. Creating Easyship labels, pickup automation and checkout shipping-price changes are excluded.

## Checkpoints

### 1. Sandbox adapter and provider-aware persistence

- [x] Write adapter regressions for sandbox-only credentials/URL, Bearer authentication, batch filtering, delivery checkpoint evidence, ambiguous/foreign records, safe failures and rate limiting; observe failures, implement and pass.
- [x] Add additive V16 provider/reference fields preserving existing parcel history. Add PostgreSQL tests for Easyship handoff, provider isolation, tracking-number mismatch, correction races and delivery once.
- [x] Reuse the existing durable tracking job with provider-aware selection; old jobs remain readable. Add Easyship ID to handoff/correction UI and regression coverage for retained drafts and mobile layout.

### 2. Verification and beta delivery

- [x] Run focused and full backend/PostgreSQL tests, frontend tests/lint/build and affected Browser fixtures. Review the focused change and fix confirmed defects. Final backend 413/413, frontend 101/101, Browser 22/22; review findings fixed with regressions.
- [ ] Document setup/rotation, observed API evidence and limitations. Commit/push stg; deploy matching backend before frontend to beta only after CI.
- [ ] Configure the sandbox token privately on beta; prove sandbox connectivity using the application HTTP client. Test a synthetic sandbox parcel where available; report any missing provider capabilities honestly. Update this checkpoint.

## Reference

- https://developers.easyship.com/docs/sandbox
- https://developers.easyship.com/reference/shipments_trackings_index
- https://developers.easyship.com/reference/rate-limit
- https://support.easyship.com/hc/en-us/articles/35139823442834-Getting-Started-with-Easyship-MCP-AI-Powered-Shipping
