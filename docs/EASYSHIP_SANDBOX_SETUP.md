# Easyship sandbox tracking

Mercato tracks existing Easyship sandbox shipments. Sellers record the Easyship shipment ID, courier tracking number and exact packed quantities at handoff. The existing payment, stock, ownership, one-hour seller privacy and manual admin delivery controls remain in force. This integration does not purchase labels, book pickups or change checkout delivery charges.

## Access and configuration

Create an API integration in Easyship and select its **Sandbox** token. The application requires a token beginning with `sand_` and uses only `https://public-api-sandbox.easyship.com/2024-09`. Production tokens are rejected when enabled; redirects and production fallback are disabled. Tracking reads require `public.track:read`. Creating synthetic tracking records and simulating their status additionally requires the optional `public.track_3p:write` scope; those operations are test setup, not application endpoints.

Keep the token only in the beta backend environment:

```bash
sudoedit /opt/mercato-beta/.env
```

```dotenv
EASYSHIP_ENABLED=true
EASYSHIP_SANDBOX_TOKEN=<replace privately with your sandbox token>
EASYSHIP_TRACKING_BATCH_SIZE=50
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

1. Create a synthetic tracking record or shipment in the **Easyship sandbox**, without any production label purchase or pickup. Keep both the Easyship ID (for example `ESIN10000001`) and its exact courier tracking number.
2. On a funded, packed Mercato beta fulfillment, select **Mark shipped**. Enter both references, choose handed-off quantities and confirm the test handoff. A tracking number or label alone does not ship an item.
3. Refresh tracking or wait for polling. The backend matches both identifiers, retains known data during failures and never marks a parcel delivered from an undated status alone.
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

## Limits and observed evidence

- The client makes at most one request every ten seconds per running instance, without sleeping worker threads. API 429/Retry-After is persisted in the existing job/parcel retry path. Sandbox limits are documented as six requests per minute and one per second.
- Active parcels are polled every 15 minutes. Manual refresh has a 60-second per-parcel cooldown. UI revalidation reads Mercato, not the carrier.
- Read-only shipment listing and tracking calls returned HTTP 200 from OCI. Java HTTP transport also returned 200. Python's default HTTP client was rejected by Easyship's browser-signature firewall; application requests use an explicit user agent.
- A synthetic tracking record was created with HTTP 201 and advanced to Out for Delivery and Delivered with HTTP 200; the normal tracking API returned the matching ID, number and dated checkpoints. The installed beta.7 adapter also accepted this evidence in a read-only probe. No Mercato order was changed by the probe, and no label, pickup, credit purchase, production API or MCP action was called.
- The sandbox's India-origin supported-courier filter returned no entries. Its sample couriers were used for the synthetic record. This proves test API behavior, not real domestic serviceability or a carrier contract.
- The MCP documentation warns that MCP actions have no separate test environment. Mercato uses the REST sandbox directly and does not depend on an AI/MCP runtime.

## Official references

- [Sandbox and current domains](https://developers.easyship.com/docs/sandbox)
- [Tracking shipments](https://developers.easyship.com/docs/how-to-track-a-shipment)
- [Shipment tracking endpoint](https://developers.easyship.com/reference/shipments_trackings_index)
- [Sandbox status simulation](https://developers.easyship.com/reference/sandbox_trackings_update)
- [API rate limits](https://developers.easyship.com/reference/rate-limit)
- [MCP guidance](https://support.easyship.com/hc/en-us/articles/35139823442834-Getting-Started-with-Easyship-MCP-AI-Powered-Shipping)
