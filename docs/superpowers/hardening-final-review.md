# Final review disposition

The independent source review identified three actionable areas before its remaining pass was interrupted by the review agent's account usage limit. This is a partial independent review, followed by implementer verification; it is not represented as an unconditional independent approval.

1. Cancellation loaded an unlocked entity graph before acquiring the aggregate lock. Since a JPA lock does not refresh an already-managed snapshot, a concurrent allocation/shipment could leave stale cancellation quantities. Cancellation now locks first, then checks ownership and reads mutable state. `OrderCancellationLockTest` records the failing and passing path.
2. Payment retry returned an entity with an unresolved lazy payment association after its transaction. OSIV-disabled PostgreSQL coverage now proves the snapshot is initialized before gateway I/O. Retry also recovers an initial gateway order that never arrived using the same stable merchant identity.
3. The binding implementation contract requires signed raw event persistence. The inbox now stores the exact verified bytes/signature/timestamp privately alongside its normalized, redacted work payload. V7 adds those optional columns; historical/polled events do not receive fabricated proof. PostgreSQL coverage proves byte identity, duplicate idempotence and a single processing job.

Further regression work found and fixed cancelled lines being resurrected during allocation, unpaid orders inflating collection reports, a generic failed status masking verified capture, and missing early validation of the deployed MFA encryption key. Automatic refunds retain the actual captured amount including buyer charges and keep zero commission/earnings. Red/green evidence and complete backend runs are recorded in ignored target logs and summarized in `docs/HARDENING_REVIEW.md`.

External transport/backup/mail/gateway gates remain explicitly outstanding. No source review or local test substitutes for those live checks. Exact dependency exceptions and their runtime regression guard are in `docs/DEPENDENCY_SECURITY.md`.
