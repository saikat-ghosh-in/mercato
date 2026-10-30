# Easyship sandbox checkpoint — 30 October 2026

The carrier switch is implemented and verified locally. It is not yet pushed, released, configured or deployed to beta. Existing beta remains v0.1.0-beta.6; main and production are unchanged.

The adapter reuses existing parcel, quantity, funding, privacy, ownership and job controls. V16 preserves legacy providers and adds a separate Easyship reference. Seller/admin forms retain drafts and require both identifiers. The REST client accepts sandbox tokens only and has no production, label, pickup or billing operation.

Final local acceptance: **413 backend tests**, zero failures/errors/skips, including real disposable PostgreSQL migrations, normalized schema comparison and locking; **101 frontend tests**, clean lint, successful production build; **22 actual-CSS Browser cases** at mobile/desktop widths and reduced motion. The focused 320px handoff fixture also passed after adding a screenshot. Existing bundle-size advice remains; physical hardware was not tested.

Independent source review found two Important issues. A single test-driven fix pass added reverse-checkpoint history protection and prevented transient failures from consuming their durable retries. Both defects were reproduced before fixes; the full 413-test backend suite then passed. Review did not find a Critical issue. The corrected diff was verified through regressions rather than a duplicate review cycle.

Actual Easyship sandbox evidence from OCI: shipment/tracking reads and Java HTTP transport returned 200; a clearly synthetic tracking record was created with 201 and advanced to Out for Delivery and Delivered with 200. The regular tracking endpoint returned the exact shipment ID, courier number and dated checkpoints. No real Mercato order was mutated. No MCP action, production API, label, pickup, credit purchase or customer email was triggered.

Sandbox courier examples do not prove real domestic serviceability. Current code tracks an existing Easyship shipment; automated label creation is excluded. See [setup and rotation](EASYSHIP_SANDBOX_SETUP.md) and the [implementation plan](superpowers/plans/2026-10-30-easyship-sandbox.md).

Remaining delivery work: focused commit/push to stg, exact-source CI, beta prerelease, private sandbox-token configuration, V16/backend installation before matching frontend publication, and beta postflight evidence. Preserve the existing README/local-property edits. The user requested this pause before leaving.
