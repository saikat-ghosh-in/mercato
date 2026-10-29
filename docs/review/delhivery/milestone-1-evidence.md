# Milestone 1 — compact order summaries

## Early checkpoint

- Base: `19fa53eee7cab3066a58667a5344a32c045ae720`, branch `stg`.
- Read the binding task brief, TDD guidance, and local Emil UI guidance.
- Existing `WorkflowProgress` renders seven cumulative counters. Plan: a compact quantity/state row and only positive, disjoint mixed-state quantities; customer stages collapse to Processing, operational labels remain explicit.
- Existing CSS browser harness is `frontend/tests/fulfillment.browser.mjs` (there is no tracked `workflow.browser.mjs`). Reuse it for 320/390/desktop synthetic fixtures and keyboard checks.
- Protected README/local properties and controller plan/docs are untouched. No production edits or tests yet; controller baseline is pending.

## Final evidence

- Controller baseline: `npm test` => 95/95 pass, 0 fail/skip (`backend/target/delhivery-baseline-frontend.log`).
- Behavioral RED before production edits: `node --test tests/fulfillment.test.mjs` => 14 tests, 8 pass, 6 fail, exit 1. New literal expected summaries compared with `undefined` from the absent helper. Log: `backend/target/delhivery-m1-red.log`.
- GREEN focused command => 14/14 pass, exit 0 (`backend/target/delhivery-m1-green.log`).
- Full `npm test` => 101/101 pass, 0 fail/skip, exit 0 (`backend/target/delhivery-m1-tests.log`).
- `npm run lint` => exit 0 (`backend/target/delhivery-m1-lint.log`).
- `npm run build` => exit 0; existing >500 kB chunk advisory (`backend/target/delhivery-m1-build.log`).
- Final full actual CSS browser gate: `$env:PLAYWRIGHT_MODULE='C:/Users/007sa/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs'; node --test tests/fulfillment.browser.mjs` => 15/15 pass, 0 fail/skip, exit 0; duration 102779 ms. Log: `backend/target/delhivery-m1-browser-verified.log`.
- `git diff --cached --check` => exit 0 before commit.
- Commit: `5342153d67fb1955850b4e2d006667ff3d0f492b` — `feat: compact order quantity summaries [skip netlify]`. Exactly seven frontend files; no controller docs or protected local files included. No push/deploy.
- Initial browser full run: 14/15 pass. The new cancelled-item assertion accidentally matched both the order total text and the item's inner Qty span (`2 !== 1`). The focused attempt reproduced the same selector failure. First narrowing attempt used an unsupported `getByText` selector option, which Playwright ignored; the next full attempt still reported 14/15. Corrected it to `locator('p').filter({hasText:/^Qty 1$/})`, then the isolated cancelled-item browser check passed 1/1. No production fix was needed. Attempt logs: `backend/target/delhivery-m1-browser.log`, `backend/target/delhivery-m1-focused-browser.log`, `backend/target/delhivery-m1-browser-final.log`, and `backend/target/delhivery-m1-cancelled-browser.log`.

## Interfaces and scope

- `workflowLabel(record, audience='operations')`: existing operational labels remain the default. Customer callers collapse Confirmed/Accepted/Preparing/Packed to Processing and Awaiting funding to Awaiting payment. Legacy labels retain their uncertainty.
- `workflowSummary(record, audience='operations')` returns `{quantity, label, segments:[{label,quantity}]}`. Ordered quantity prefers workflow/record counters, then legacy quantity or a complete sum of line quantities. Missing/invalid quantity stays null; missing/inconsistent history produces no breakdown. Only positive disjoint active/shipped/delivered/cancelled buckets appear when more than one bucket exists; unconfirmed active units await payment.
- `WorkflowProgress` retains `record`/`showState` and adds `showQuantity` and `audience`. Compact text uses existing styles, with no new animation or dependency. Existing review warning is retained.
- Customer order list and detail use customer labels. Admin lines suppress duplicate Qty already in parent product text. Seller call sites inherit operational defaults and retain the existing separate stage badge. Order totals remain visible.
- QuantityCommand, allowed actions, operation IDs, versions, errors/drafts, refresh fences, fees, backend source and protected local files are unchanged.
- Harness is reused; synthetic screenshot output goes under ignored `backend/target/delhivery-m1-browser/`.

## Visual checks and concerns

- Inspected actual CSS 320px customer mixed and admin packed screenshots: text wraps within the viewport, mixed buckets are positive/disjoint, admin Qty appears once per line, and operation controls remain visible.
- Fixture screenshot pages explicitly identify synthetic data; account examples use `example.test`. No real account/provider data is captured.
- Legacy records without complete counters deliberately show only known quantity/state; no accepted/preparing/packed history is inferred. No carrier tracking features are part of this milestone.
- Existing build bundle-size advisory remains. No new dependency, backend change, push, deployment, SSH, provider/SMTP call, or subagent.

## Screenshot paths

All paths are relative to `C:/Projects/mercato/mercato-backend/` and contain synthetic fixtures:

- `backend/target/delhivery-m1-browser/customer-mixed-320.png`
- `backend/target/delhivery-m1-browser/customer-mixed-390.png`
- `backend/target/delhivery-m1-browser/customer-mixed-1440.png`
- `backend/target/delhivery-m1-browser/customer-cancelled-390.png`
- `backend/target/delhivery-m1-browser/seller-packed-320.png`
- `backend/target/delhivery-m1-browser/seller-packed-390.png`
- `backend/target/delhivery-m1-browser/seller-packed-1440.png`
- `backend/target/delhivery-m1-browser/admin-packed-320.png`
- `backend/target/delhivery-m1-browser/admin-packed-390.png`
- `backend/target/delhivery-m1-browser/admin-packed-1440.png`

Existing harness regression screenshots also use this ignored output directory. Independent review remains with the controller. Report and controller workflow artifacts remain local/ignored as instructed.
