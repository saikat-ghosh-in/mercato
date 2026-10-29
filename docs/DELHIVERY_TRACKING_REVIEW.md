# Delhivery tracking and seller-remorse revision

## Scope and delivery

Approved29 October2026: compact quantity summaries, seller visibility only after the one-hour remorse period, seller parcel handoff and Delhivery staging tracking. Milestones are committed and reviewed separately. Main/production remain unchanged; beta rollout is coordinated after the compatible code passes final gates.

Delhivery token/test waybills are not yet configured. The carrier client remains disabled by default and manual admin delivery remains available. Tests with mocked provider transport are not live carrier acceptance.

## Milestone checkpoints

1. Compact summaries — complete, source`5342153d67fb1955850b4e2d006667ff3d0f492b`.101 frontend tests,15 CSS Browser cases, lint/build passed; scoped spec/quality review approved. Existing chunk advisory remains Minor. Screenshots are synthetic, not live beta.
2. Seller privacy and release — complete, source `afee9bcd6f0936f0d8f05233524609f2c21ff10f`. 84 focused backend tests, final 48-test workflow verification, 101 frontend tests, four affected CSS Browser cases, clean lint and successful build; scoped spec/quality review approved.
3. Shipments and Delhivery — implemented in `5f988b5`, `f3cc425`, with review fixes in `9fb2ee0`. Final exact-source CI and beta rollout are the remaining release gates. Carrier access stays disabled until staging credentials are verified.

## Decisions and boundaries

- Use existing requested stg checkout, preserving README/local-properties edits. Cost: staging commits rather than separate isolation.
- Reuse Windows artifact helpers because bash helpers were previously blocked; no application behavior change.
- Three sequential implementers with scoped reviews; the final task review also evaluates whole-branch integration separately. Cost: broader last review instead of duplicate review hydration.
- Reuse the existing server release timestamp rather than creating another publication engine. Cancelled history may become visible only after release; fully cancelled held fulfillments create no new incoming task/mail.
- Seller incoming views revalidate on focus and every60s while visible, without exposing hidden-order counts/deadlines. Cost: a small scoped periodic read.
- No label/pickup/rate/return automation, new payment engine, live carrier mode or paid services. Existing0.5%/500-per-unit commission and60-day policy notice remain.

## Acceptance evidence

Baseline frontend95/95 passed; exact current baseline source19fa53e had previously passed full Application checks40696571411. New milestone evidence and provider/Browser limitations will be recorded here before final handoff.

Milestone1: [evidence](review/delhivery/milestone-1-evidence.md), [review](review/delhivery/milestone-1-review.md), [cancelled390px](review/delhivery/compact-cancelled-390.png), [mixed320px](review/delhivery/compact-mixed-320.png).

Milestone2: [evidence](review/delhivery/milestone-2-evidence.md), [review](review/delhivery/milestone-2-review.md). Local checkpoint only; beta still runs the previous release until the compatible shipment revision is ready.

Milestone3: [evidence and review fixes](review/delhivery/milestone-3-evidence.md), [independent review](review/delhivery/final-review.md), [seller 320px](review/delhivery/parcel-seller-320.png), [customer desktop](review/delhivery/parcel-customer-desktop.png). Screenshots are synthetic fixtures. Final Browser suite: 21/21; frontend 101/101; clean lint and successful build. Full GitHub CI on the final commit is required before the beta release.

Delhivery One account access was inspected on 29 October. Its explicitly labeled staging playground returned serviceability data for synthetic pincode 122003. The account's exportable API token control is labeled live, so it was not used or generated. Playground access does not prove Mercato's backend has a usable staging token or manifested test waybill. No wallet recharge, live booking, label or pickup request was made. See [staging setup](DELHIVERY_SETUP.md).
