#!/usr/bin/env bash
# Install only after the one-time cookie/Flyway/HTTPS cutover in DEPLOY.md.
set -Eeuo pipefail
TAG="${1:?Usage: bash ~/redeploy.sh vX.Y.Z}"
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]] || { echo 'Invalid release tag' >&2; exit 2; }
APP_DIR="${MERCATO_APP_DIR:-/opt/mercato}"
SERVICE="${MERCATO_SERVICE:-mercato}"
SITE="${MERCATO_SITE:-https://mercato-in.netlify.app}"
[[ "$SITE" == https://* && "$SITE" != *'?'* && "$SITE" != *'#'* ]] || { echo 'Canonical HTTPS storefront required' >&2; exit 2; }
[[ -f "$APP_DIR/config/cookie-release-ready" ]] || { echo 'Complete the one-time database/HTTPS/MFA cutover in DEPLOY.md first.' >&2; exit 2; }
for tool in curl sha256sum flock install python3 cmp; do command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 2; }; done
exec 9>"$APP_DIR/.redeploy.lock"
flock -n 9 || { echo 'Another deployment is running' >&2; exit 2; }
STAGING=$(mktemp -d "$APP_DIR/.release.XXXXXX")
trap 'rm -rf -- "$STAGING"' EXIT
BASE="https://github.com/saikat-ghosh-in/mercato/releases/download/$TAG"
curl --fail --location --show-error --silent --retry 3 --output "$STAGING/mercato-backend.jar" "$BASE/mercato-backend.jar"
curl --fail --location --show-error --silent --retry 3 --output "$STAGING/mercato-backend.jar.sha256" "$BASE/mercato-backend.jar.sha256"
# Do not trust a checksum file to name another path or multiple files.
python3 - "$STAGING/mercato-backend.jar.sha256" <<'PY'
import re,sys
if not re.fullmatch(r'[a-fA-F0-9]{64}  mercato-backend\.jar\n?',open(sys.argv[1]).read()):
    raise SystemExit('Invalid release checksum manifest')
PY
(cd "$STAGING" && sha256sum --check mercato-backend.jar.sha256)
[[ $(stat -c %s "$STAGING/mercato-backend.jar") -gt 1000000 ]] || { echo 'Release JAR is unexpectedly small' >&2; exit 2; }
mkdir -p "$APP_DIR/releases/$TAG"
if [[ -e "$APP_DIR/releases/$TAG/mercato-backend.jar" ]]; then
  cmp -s "$STAGING/mercato-backend.jar" "$APP_DIR/releases/$TAG/mercato-backend.jar" || { echo 'Release content changed under an existing tag; refusing replacement.' >&2; exit 2; }
else
  install -m 640 "$STAGING/mercato-backend.jar" "$APP_DIR/releases/$TAG/mercato-backend.jar"
  install -m 640 "$STAGING/mercato-backend.jar.sha256" "$APP_DIR/releases/$TAG/mercato-backend.jar.sha256"
fi
if [[ -f "$APP_DIR/mercato-backend.jar" ]] && ! cmp -s "$APP_DIR/mercato-backend.jar" "$STAGING/mercato-backend.jar"; then
  PREVIOUS=$(mktemp "$APP_DIR/releases/previous-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.jar")
  cp -p "$APP_DIR/mercato-backend.jar" "$PREVIOUS"
fi
install -m 640 "$STAGING/mercato-backend.jar" "$APP_DIR/.mercato-backend.jar.next"
mv -f "$APP_DIR/.mercato-backend.jar.next" "$APP_DIR/mercato-backend.jar"
sudo -n systemctl restart "$SERVICE"
for attempt in $(seq 1 60); do
  if systemctl is-active --quiet "$SERVICE" && curl --fail --silent --show-error --max-time 5 "$SITE/api/auth/session" > "$STAGING/session.json" 2>/dev/null &&
    python3 - "$STAGING/session.json" <<'PY'
import json,sys
data=json.load(open(sys.argv[1]))
if not isinstance(data.get('authenticated'),bool) or not data.get('contextId'): raise SystemExit(1)
PY
  then
    printf '%s\n' "$TAG" > "$APP_DIR/releases/current-tag"
    echo "Deployed $TAG. Verify sign-in/MFA, bag, checkout and worker activity in Browser."
    exit 0
  fi
  sleep 2
done
echo 'Readiness failed. Inspect journalctl; the previous artifact is retained. No automatic schema/auth rollback was attempted.' >&2
exit 1
