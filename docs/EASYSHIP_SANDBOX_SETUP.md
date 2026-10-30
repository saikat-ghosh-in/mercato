# Easyship sandbox tracking

Mercato generates synthetic Easyship sandbox tracking or tracks existing sandbox shipments. Sellers select **Mark shipped** beside the specific item, generate its tracking references, then confirm the packed quantities handed over. The existing payment, stock, ownership, one-hour seller privacy and manual admin delivery controls remain in force. This integration does not purchase labels, book pickups or change checkout delivery charges.

## Access and configuration

Create an API integration in Easyship and select its **Sandbox** token. The application requires a token beginning with `sand_` and uses only `https://public-api-sandbox.easyship.com/2024-09`. Production tokens are rejected when enabled; redirects and production fallback are disabled. Tracking reads require `public.track:read`. Synthetic tracking generation requires `public.track_3p:write`. Webhook setup requires `public.webhook:read` and `public.webhook:write`.

Keep the token only in the beta backend environment:

```bash
sudoedit /opt/mercato-beta/.env
```

```dotenv
EASYSHIP_ENABLED=true
EASYSHIP_SANDBOX_TOKEN=<replace privately with your sandbox token>
EASYSHIP_TRACKING_BATCH_SIZE=50
EASYSHIP_SANDBOX_COURIER_ID=<sandbox courier UUID>
DELHIVERY_ENABLED=false
```

After installing the compatible release, restart only beta:

```bash
sudo systemctl restart mercato-beta
sudo journalctl -u mercato-beta -n 80 --no-pager
```

Never put the token in Git, screenshots, frontend variables or a Netlify client environment. When rotating the temporary token, replace `EASYSHIP_SANDBOX_TOKEN` privately and restart beta. Preserve all existing database, session, MFA and ingress secrets. Production's `/opt/mercato`, service `mercato`, database and Netlify project remain unchanged.

Set `EASYSHIP_ENABLED=false` for manual-only operation. Historical Delhivery parcels retain their provider and cannot be silently reinterpreted as Easyship shipments. Turning Easyship on does not enable Delhivery.

## Test a parcel

1. On a funded, packed Mercato beta fulfillment, select **Mark shipped** beside the item. Its maximum eligible quantity is selected initially.
2. Select **Generate sandbox tracking** to fill both references automatically. This creates synthetic tracking with test contacts, not customer details. After a timeout, **Check again** reconciles the same saved request. Alternatively enter existing sandbox references. Confirm the test handoff separately; generation alone does not ship an item or change stock.
3. With webhooks configured, updates arrive automatically. **Refresh tracking** remains available. The backend matches both identifiers and requires a dated forward checkpoint to confirm delivery.
4. After handoff, use Easyship's sandbox-only status simulation endpoint to generate a new dated event. A checkpoint earlier than Mercato's recorded handoff is intentionally rejected. The application does not expose the simulation endpoint to customers or automatically advance sandbox statuses.
5. A dated, matching forward **Delivered** status completes only that parcel's shipped quantities once. Return/reverse evidence, conflicting identities, ambiguous multiple tracking numbers and invalid/future timestamps require manual verification. Delivery does not deduct stock again.

The official simulation page still shows the older hostname. During 30 October verification, that URL returned 404; the same documented path worked on the current sandbox hostname:

```text
PATCH https://public-api-sandbox.easyship.com/2024-09/sandbox/trackings/{tracking_id}
Authorization: Bearer <sandbox token>
Content-Type: application/json

{"status_slug":"out_for_delivery"}
```

Use `delivered` for the final simulated event. `tracking_id` is the UUID returned by creating a tracking record, not the readable Easyship shipment ID or courier tracking number. Use the sandbox token only; no production URL is needed.

## Webhook setup

Install the compatible backend first, leaving `EASYSHIP_WEBHOOK_ENABLED=false` until its secret is configured. Create a webhook using the **sandbox** API or sandbox dashboard:

```text
https://mercato-in-beta.netlify.app/api/public/easyship/webhook
```

Select API version `2024-09` and events `shipment_tracking_status_changed` and `shipment_tracking_checkpoints_created`. Save the returned secret privately in beta's environment:

```dotenv
EASYSHIP_WEBHOOK_ENABLED=true
EASYSHIP_WEBHOOK_SECRET=<webh_ secret returned by the sandbox webhook>
```

Restart only `mercato-beta`, test both subscriptions, then activate the webhook. Keep secrets out of Git and frontend configuration. The exact POST receiver verifies Easyship's HS256 `X-EASYSHIP-SIGNATURE`; browser shipment actions still require session, CSRF and ownership checks. Ingress still passes through the authenticated Netlify proxy.

Easyship's documented signature authenticates a company claim, not the event body. Notifications therefore durably queue an authenticated canonical tracking read and acknowledge immediately. They cannot directly mark a parcel delivered. Duplicate bodies deduplicate; cooldowns defer the job rather than dropping updates. Webhook mode stops periodic Easyship carrier polling. A single initial read after handoff and explicit manual refresh remain. Browser revalidation reads only Mercato's saved data.

Easyship may deactivate a webhook after repeated failures. Check its activation/test status if updates stop. To explicitly restore periodic polling while repairing delivery, set `EASYSHIP_WEBHOOK_ENABLED=false` and restart beta.

## Limits and observed evidence

- The client makes at most one request every ten seconds per running instance, without sleeping worker threads. API 429/Retry-After is persisted in the existing job/parcel retry path. Sandbox limits are documented as six requests per minute and one per second.
- Webhook mode replaces the 15-minute Easyship polling loop. Manual refresh has a 60-second per-parcel cooldown. UI revalidation reads Mercato, not the carrier.
- Read-only shipment listing and tracking calls returned HTTP 200 from OCI. Java HTTP transport also returned 200. Python's default HTTP client was rejected by Easyship's browser-signature firewall; application requests use an explicit user agent.
- A synthetic tracking record was created with HTTP 201 and advanced to Out for Delivery and Delivered with HTTP 200; the normal tracking API returned the matching ID, number and dated checkpoints. The installed beta.7 adapter also accepted this evidence in a read-only probe. No Mercato order was changed by the probe, and no label, pickup, credit purchase, production API or MCP action was called.
- The sandbox's India-origin supported-courier filter returned no entries. Its sample couriers were used for the synthetic record. This proves test API behavior, not real domestic serviceability or a carrier contract.
- The MCP documentation warns that MCP actions have no separate test environment. Mercato uses the REST sandbox directly and does not depend on an AI/MCP runtime.

## Current beta verification

On 30 October 2026, beta.8 generated a sandbox tracking record from the seller's item-level dialog and filled both references. The maximum packed quantity was selected automatically; closing/reopening retained the references. Confirmation enabled only after confirming handoff. No handoff was submitted: shipment quantities and physical inventory stayed unchanged.

Both provider webhook tests succeeded through the beta Netlify URL; the version `2024-09` subscription is active for status changes and tracking checkpoints. A forged signature returned HTTP 401. Canonical delivery application, duplicate events, cooldown retry and polling exclusion are covered by PostgreSQL regressions. Sandbox progress still needs provider test events; this does not create a real carrier booking or automatically simulate delivery.

## Official references

- [Sandbox and current domains](https://developers.easyship.com/docs/sandbox)
- [Tracking shipments](https://developers.easyship.com/docs/how-to-track-a-shipment)
- [Shipment tracking endpoint](https://developers.easyship.com/reference/shipments_trackings_index)
- [Sandbox status simulation](https://developers.easyship.com/reference/sandbox_trackings_update)
- [API rate limits](https://developers.easyship.com/reference/rate-limit)
- [Tracking creation](https://developers.easyship.com/reference/trackings_create)
- [Webhook signatures and setup](https://developers.easyship.com/reference/webhooks-guide)
- [MCP guidance](https://support.easyship.com/hc/en-us/articles/35139823442834-Getting-Started-with-Easyship-MCP-AI-Powered-Shipping)
