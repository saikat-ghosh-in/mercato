# Delhivery staging setup

These instructions activate the tracking integration only after the matching shipment/Delhivery milestone is deployed. The application remains usable with manual admin delivery while carrier access is unavailable. No labels, pickup bookings or live shipping are created by this integration.

## Obtain test access

Use a Delhivery One account and request its **staging API token** through the account/API setup process. Obtain a manifested test waybill belonging to that same staging account. An allocated but unmanifested AWB is not sufficient for tracking, and shared Playground shipments may belong to a different account. Do not request or configure a production token for this sandbox. [Official API FAQ](https://one.delhivery.com/developer-portal/document/b2c/detail/faq).

Sign in to Delhivery One, then open **Settings → API Setup → Test our APIs**. The FAQ says the business account contact provides the testing token; if API Setup exposes only a live token, request staging access rather than configuring the live token. No shipping purchase or production activation is needed by Mercato's tracking code. Delhivery's account eligibility and access terms must be checked with them.

Their [official integration MCP](https://one.delhivery.com/developer-portal/document/b2c/detail/delhivery-mcp) is available at `https://mcp-client-integration.delhivery.com/mcp`. It provides reference knowledge and examples, not authenticated shipment execution. Its tracking specification was consulted during implementation; it confirms the staging endpoint, Token authentication, 50-waybill batches and nested response fields. It does not replace the staging token needed by the running backend. The app calls the staging REST tracking API directly; it does not ship an AI/MCP dependency.

## Configure beta only

Use the backend environment file on OCI, never a frontend/Netlify client variable. Keep its existing permissions and preserve all database, MFA/encryption and ingress keys. Do not paste API tokens into chat, screenshots or Git.

```bash
sudoedit /opt/mercato-beta/.env
```

Set these values, replacing the token placeholder privately:

```dotenv
DELHIVERY_ENABLED=false
DELHIVERY_STAGING_TOKEN=<your-staging-token>
DELHIVERY_TRACKING_BATCH_SIZE=50
```

The tracking client is restricted to `https://staging-express.delhivery.com`. Delhivery documents up to50 comma-separated waybills per tracking call; the configured batch size may be reduced to any integer1–50. This is separate from the bulk-waybill allocation API and its different limits. [Tracking reference](https://one.delhivery.com/developer-portal/document/b2c/detail/order-tracking).

After the matching backend is installed and test access is ready, set `DELHIVERY_ENABLED=true` and restart **only beta**:

```bash
sudo systemctl restart mercato-beta
sudo journalctl -u mercato-beta -n 80 --no-pager
```

Do not change `/opt/mercato/.env`, production service `mercato`, or the separate production Netlify site. Enabled with an absent token or an invalid batch size must fail clearly; disabled with no token is the default working mode.

## Validate

1. Through a released seller fulfillment, enter the same-account test AWB and packed quantities, then confirm actual test handoff. The app is tracking an existing staged parcel; it does not create its Delhivery manifestation.
2. Refresh tracking and verify the response's AWB matches the saved parcel. Check last-update and failure messages; never accept an unrelated sample response as proof.
3. Verify forward delivery updates only the linked shipped quantities once. Reverse/RTO scans must not mark customer delivery. Test stages not supplied by Delhivery staging use mocked regression fixtures; do not claim live evidence for them.
4. Verify a different customer's order ID returns404, and seller deep links remain unavailable before the one-hour release. Admin manual delivery remains an audited fallback.

Automatic polls run every15 minutes; manual refresh is cooled down. Credentials and raw provider failures stay out of public responses. An unavailable carrier must retain the last known order state and must not block normal checkout or alter prices.
