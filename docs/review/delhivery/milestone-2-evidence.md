# Seller release privacy: milestone 2

Source: `afee9bcd6f0936f0d8f05233524609f2c21ff10f`, based on checkpoint `bac68ef50e0a31a02d370e59c725a6444e0ee1ea`. This evidence concerns local tests, not a deployed beta or live email test.

Seller order details, dashboard totals, financial reports and order-linked email eligibility now use the existing effective release time. Seller commands use explicit seller routes, including accounts with both admin and seller roles. Customer and admin reads retain their scope. Seller lists refresh on focus and every visible minute without clearing current content.

## Verification

- PostgreSQL behavioral RED: three tests failed without infrastructure errors, reproducing held-order list, dashboard and seller-refund-notice leaks.
- Focused backend GREEN: 84 tests, no failures, errors or skips. Selection: `PostgresQuantityWorkflowTest,PostgresPracticalFinanceTest,PracticalRefundTest,FulfillmentPaymentBoundaryTest,FulfillmentWorkflowPolicyTest,MonthlyReportTest,MailFailureTest`.
- Final workflow verification: 48 tests passed after removing a redundant experimental fixture. The database already rejects its inconsistent confirmation/release state; no production change was retained for that case.
- Frontend: 101 tests passed; lint passed without warnings; production build passed. The pre-existing bundle-size advisory remains.
- Four actual-CSS Browser tests passed: seller quantity commands and retries, account/record response fencing, focus/visible-minute revalidation, and dual-role seller whole cancellation.
- Staged diff whitespace check passed. README and local application configuration edits were excluded from the commit.

Exact local commands and logs are recorded in the ignored task workspace's `task-2-report.md`. Broader backend validation and shipment/provider checks belong to milestone 3. No carrier, SMTP, production or deployment operation ran during this milestone.
