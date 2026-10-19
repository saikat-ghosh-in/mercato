# Mercato seller operating policy — draft 2026-10

Prepared 19 October 2026 for local staging review. The matching customer-facing document is `/seller-policies`. This is an original proposed commercial policy, not a declaration that carrier, payout, support or legal operations are already live. Finalize the operating entity/address, grievance officer, contracts, tax treatment, category return windows and enforceable agreement before commercial launch. Mandatory consumer rights prevail.

## Onboarding and verification

Shopper and seller registrations share secure account creation but use separate paths. Seller applicants provide store/legal business names, entity type, PAN, GSTIN or eligible GST portal enrolment, pickup address/state/pincode, support email and categories. Never request passwords, OTPs, bank login details or Aadhaar here. An email-verified applicant remains `PENDING`, with customer permissions only, until an administrator independently checks identity, tax eligibility, pickup details and permitted categories and explicitly attests to review. Format validation alone is not government verification. Declines require a reason, visible to the applicant. Resubmission, document-upload and automated government verification integrations are future operational work; declined applicants use Help for review.

Approved enrolled/unregistered sellers are restricted at checkout to orders within their pickup state. This is a minimum technical restriction, not complete tax eligibility validation: the reviewer must verify applicable turnover, category, single-state/enrolment and other conditions. The launch process must review the final tax position. Bank beneficiary verification and payout onboarding are separate prerequisites for actual remittance.

Applicant business/tax details are accessible only to that applicant and authenticated administrators, never public product/user DTOs. Production database volumes/backups must use encryption and restricted operator access; establish retention and access-audit policies for PAN/GST records. Account deletion cascades application removal; legally required financial/notice records need a separate retention policy.

## Fees and settlement

Initial commission is **0.5%, capped at ₹500 per item sold**, persisted in `commission_settings`, across current categories. Basis: merchandise value after seller discounts, excluding delivery and separately stated taxes. Example: ₹1,000 commissionable value → ₹5 commission; a ₹200,000 item reaches the ₹500 cap. Applicable taxes and statutory deductions must be separately disclosed; do not assume this application calculates or files them. Proposed subscription/listing fee: none. No undisclosed packaging or unrelated service charges.

New orders capture `commission_rate_percent` and `commission_cap_per_item` on each order line and separate buyer platform/delivery charge snapshots. Changing a rate cannot rewrite historical snapshots. Existing historical lines have unknown/NULL rates and are not backfilled with an invented rate. Finance dashboards show paid merchandise, shipped sales, cancelled value, captured-rate commission, promotion bookings/credits, conservative unallocated refund holds and known net earnings. They are accrual reports, not bank payout statements. Unknown-rate sales are excluded from known net and shown separately. Buyer platform fees are not charged to sellers. Shipping collections are pass-through charges, not profit. Implement tax reconciliation, a remittance ledger and verified payout balances before live remittance. Proposed schedule: weekly settlement after delivery and the applicable return window, with verified beneficiary details. Holds/reserves require a documented dispute or legal reason and an itemised statement.

Cancelled or fully refunded merchandise should reverse its commission; unallocated partial refunds are held for reconciliation until item attribution is confirmed. Seller-attributable damage/wrong items and carrier loss are investigated before charges are allocated. The operating/refund ledger must implement this policy before launch; current payment/refund capabilities remain the existing gateway flows.

## Listing requests and optional promotions

Submitting a listing costs **₹0**. Sellers complete essentials, rich product details and imagery, then request approval. New requests are `PENDING`; administrators review claims, identity, category, safety and disclosures and record an approval/rejection note. A rejected product can be corrected and resubmitted through the same editor. Seller edits to an approved listing hide it pending another review; inventory supply changes do not reset approval. Existing legacy listings retain approval. Approval is enforced in public browsing, wishlist, bag and checkout, not merely hidden in the interface.

Optional fixed-term promotion defaults: **Discovery ₹49/day** (catalog sponsored shelf), **Featured ₹99/day** (home and catalog shelves), **Spotlight ₹199/day** (priority within both shelves). Choose **3, 7 or 14 days**, starting today or within 30 UTC dates. A campaign starting today starts at booking; a future one starts at 00:00 UTC. Each day is 24 hours. Prices are configurable server properties; existing bookings retain their daily rate and total. The seller reviews the total and explicitly authorizes the fee deduction. No listing charge, automatic renewal or guaranteed sales/impressions. Current sponsored shelves show at most three eligible products, prioritise levels, and rotate within levels every five minutes; higher competition can prevent display. The public shelf is labeled Sponsored. Only owned, approved, active and in-stock products qualify, and eligibility is checked again at display time.

Campaign fees are booked against **eligible accrued seller earnings**, not charged to a card. The initial budget is shipped paid sales at known captured commission rates, excluding any refunded orders, less all net campaign charges. Three current/scheduled campaigns maximum; overlapping periods for the same product are rejected. Booking is serialized per seller, quotes are verified server-side and request IDs prevent duplicate charges on retries. Insufficient earnings cannot create a campaign. This is not a transferable wallet, cash deposit or payout balance; future production remittance must atomically reserve this expense to prevent double spending with payouts. No bank payouts are implemented in this revision.

Before start, cancellation credits the full fee. After start, each started day is charged and unused whole days are credited; completed campaigns cannot be cancelled. There is no automatic refund for stock shortages or seller edits/suspension: the seller must cancel to credit remaining days. The UI discloses eligibility and lack of guaranteed placement. Service-failure disputes go through Help for documented manual review before any live launch. Credits and deductions remain visible; records are retained when a product is removed. Promotion bookings fund a future service, and the admin report keeps them separate from accrued sales commission and customer platform fees. Tax invoice/tax-on-advertising integration remains a launch prerequisite.

## Commission change control

- Rates are bounded to **0–20%**, at most two decimal places; a change cannot exceed **5 percentage points** from the current rate. Unchanged rates are rejected.
- One pending change at a time. Require a 20–1,000 character reason and exact `SCHEDULE [rate]%` confirmation. Only administrators can schedule/cancel. History records actor, previous/proposed rate, reason, creation, status and effective time.
- Persist an in-app notice and email outbox item for every approved seller account, including inactive or locked accounts. The Seller notices tab and its unread badge inside the seller dashboard expose the change. Reading a notice never waives the notice period.
- A rate stays `NOTIFYING` until all required announcement emails have been accepted by the configured mail service. Failed attempts retry with backoff (bounded 5-second SMTP timeouts; batch size 20, maximum backoff 6 hours).
- Only then is its effective instant set to **60 full days after the latest successful email acceptance**. This uses UTC instants, not local calendar rounding. SMTP acceptance is not proof of inbox delivery; delivery/bounce monitoring is an operational requirement. The email states the minimum notice and directs sellers to the final dashboard date.
- Approving another seller during the pending period creates their notices and restarts/extends the global notice calculation. It cannot shorten an existing seller’s notice. Current rate is resolved under a database lock before order capture.
- Before effectiveness, cancellation leaves the current rate unchanged and queues cancellation notices. Undelivered announcements are superseded rather than falsely marked sent. Applied changes cannot be cancelled or edited; propose another properly noticed change.
- Email delivery is at least once: a process failure after SMTP acceptance but before database commit may duplicate a notice. Production requires bounce handling, monitoring, verified sender reputation and legal contact details.

## Fulfillment and delivery

**Mercato arranges shipping and approved reverse logistics.** Seller responsibilities: accurate stock, safe packaging, required labels/invoice, compliant goods and timely pickup handover. Do not book a second courier for the same order. Carrier contracts, tracking, serviceability and reverse-logistics integrations must be configured before physical delivery is promised.

The backend’s configurable initial schedule is ₹50 Standard delivery for merchandise subtotal **below ₹999**, free Standard from ₹999; Expedited adds ₹99 to the standard charge. Consolidated has the same threshold charge and allows 1–2 additional business days for grouped dispatch. Estimates: Standard 3–5, Expedited 1–2, Consolidated 4–7 business days **from dispatch**. These are staging estimates, not verified carrier SLAs. Environmental savings are not claimed without evidence. Remote/restricted destinations and special categories require serviceability rules before launch.

The selected method persists in the server bag, survives refresh/sign-in merging for a previously empty account bag, and recalculates when quantities change. Checkout uses the actual charge, captures the method/estimate and rejects a client quote that changed before payment. An existing nonempty account bag retains its delivery preference when a guest bag merges.

## Returns, cancellations, refunds and disputes

Publish verified category-specific return windows, packaging/accessory requirements and lawful change-of-mind exclusions on product pages before sale. Do not silently invent a universal return window. Defective, damaged, counterfeit, misdescribed and otherwise non-conforming goods retain mandatory remedies, including where an optional change-of-mind exclusion exists. Delay-related remedies follow applicable law and disclosed schedules.

Customers contact marketplace Help; Mercato coordinates evidence, seller response, authorised pickup, inspection and approved refund/replacement. Sellers respond promptly; inspection must not indefinitely hold a justified remedy. Seller-attributable defects/wrong items/poor packaging are allocated to the seller after review. Carrier-attributable loss/damage follows the carrier claim process. Optional return charges must be disclosed before purchase. Return approved amounts to the original payment method; disclose the gateway/bank’s actual completion timeline. Full/partial refund and commission adjustments must reconcile in the launch settlement ledger.

Customers use eligible cancellation actions in Your orders. Cancellation follows the existing order state/window; do not promise all post-dispatch orders can be cancelled. Sellers must complete outstanding accepted orders, returns/refunds and settlement obligations when leaving. Listing access may be suspended with a reason for safety, fraud or serious breaches, with review through Help.

## Support and privacy

Launch must identify Mercato’s legal entity, business address and designated grievance officer/contact. Proposed support standard: acknowledge complaints within 48 hours and resolve within one month, without limiting statutory remedies; maintain a copy/reference and escalation path. Support inbox/ticketing staffing is not provided by the current static Help page. Do not publish placeholder contacts as working support.

Use buyer information only for fulfilment/support and disclose necessary carrier sharing. Do not reuse it for marketing without the required consent. Limit staff access, log sensitive admin review access, document deletion/retention and establish incident response before launch.

## Reference framework and launch review

These are primary references consulted, not copied agreement text:

- [Department of Consumer Affairs — consumer protection rules](https://consumeraffairs.gov.in/pages/consumer-protection-acts)
- [2020 E-Commerce Rules, government PDF](https://thc.nic.in/Central%20Governmental%20Rules/Consumer%20Protection%20(E-Commerce)%20Rules,%202020.pdf)
- [2026 amendment announcement, effective 1 January 2027](https://www.pib.gov.in/newsite/erelcontent.aspx?lang=2&reg=48&relid=294532). This adds launch requirements including price history, disclosures, grievance handling and other controls; this revision is not a certification of those future requirements.
- [GST portal official guidance](https://tutorial.gst.gov.in/) and [GSTN functionality report covering unregistered e-commerce supplier reporting](https://tutorial.gst.gov.in/downloads/news/new_functionalities_compilation_april_2023_march_2024.pdf)
- [CCPA environmental-claims guidance](https://www.pib.gov.in/Pressreleaseshare.aspx?PRID=2064963)

## Packed measurements and bulky delivery

New listings require packed weight (kg) and length, width and height (cm) for one sellable unit. Complete, verified measurements determine delivery pricing; legacy omission preserves known values. Chargeable weight is max(actual kg, L × W × H / 5,000). The first 5 kg are included, then ₹50 per started additional 5 kg per item. A side above 100 cm sets a ₹100 minimum surcharge; the larger weight/size charge applies, without double charging. Multiply by quantity and add to every delivery method, even when base delivery is free. The bag shows the authoritative total before payment. These are configurable staging business defaults; carrier serviceability and commercial rates require integration. See [package pricing review](PACKAGE_PRICING_REVIEW.md) for migration and verification details.
