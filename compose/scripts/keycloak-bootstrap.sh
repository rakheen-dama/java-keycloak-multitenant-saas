#!/usr/bin/env bash
# keycloak-bootstrap.sh — Bootstrap Keycloak after realm import.
# Creates the platform-admins group (if not exists), creates an initial admin user,
# and adds the user to the group. Fully idempotent.
#
# Usage: bash compose/scripts/keycloak-bootstrap.sh
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="starter"
PADMIN_EMAIL="${PADMIN_EMAIL:-padmin@starter.local}"
PADMIN_PASSWORD="${PADMIN_PASSWORD:-password}"

# Use kcadm.sh from Docker container
KCADM="docker exec starter-keycloak /opt/keycloak/bin/kcadm.sh"

echo "=== Keycloak Bootstrap ==="
echo ""

# ---- Wait for Keycloak to be ready ----
echo "[1/4] Waiting for Keycloak..."
MAX_WAIT=120
ELAPSED=0
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf "${KEYCLOAK_URL}/realms/${REALM}" > /dev/null 2>&1; then
    echo "  Keycloak realm '${REALM}' is ready."
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "  ERROR: Keycloak not ready after ${MAX_WAIT}s"
  exit 1
fi

# ---- Authenticate admin ----
echo "[2/4] Authenticating admin..."
$KCADM config credentials \
  --server "${KEYCLOAK_URL}" \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}"

# ---- Create platform-admins group (idempotent) ----
echo "[3/4] Creating platform-admins group..."

GROUP_ID=$($KCADM get groups -r "${REALM}" --fields id,name \
  | jq -r '.[] | select(.name=="platform-admins") | .id' 2>/dev/null || true)

if [[ -z "$GROUP_ID" ]]; then
  GROUP_ID=$($KCADM create groups -r "${REALM}" -s name="platform-admins" -i 2>/dev/null || true)
  echo "  Created platform-admins group: ${GROUP_ID}"
else
  echo "  platform-admins group already exists: ${GROUP_ID}"
fi

if [[ -z "$GROUP_ID" ]]; then
  echo "  ERROR: Could not create or find platform-admins group"
  exit 1
fi

# ---- Create initial admin user (idempotent) ----
echo "[4/4] Creating platform admin user..."

PADMIN_ID=$($KCADM get "users?email=${PADMIN_EMAIL}&exact=true" -r "${REALM}" \
  | jq -r '.[0].id // empty' 2>/dev/null || true)

if [[ -z "$PADMIN_ID" ]]; then
  PADMIN_ID=$($KCADM create users \
    -r "${REALM}" \
    -s username="${PADMIN_EMAIL}" \
    -s email="${PADMIN_EMAIL}" \
    -s firstName="Platform" \
    -s lastName="Admin" \
    -s enabled=true \
    -s emailVerified=true \
    -i 2>/dev/null || true)
  echo "  Created user: ${PADMIN_EMAIL}"
else
  echo "  User already exists: ${PADMIN_EMAIL}"
fi

if [[ -z "$PADMIN_ID" ]]; then
  echo "  ERROR: Could not create or find platform admin user"
  exit 1
fi

$KCADM set-password -r "${REALM}" --userid "${PADMIN_ID}" \
  --new-password "${PADMIN_PASSWORD}" 2>/dev/null || true

$KCADM update "users/${PADMIN_ID}/groups/${GROUP_ID}" \
  -r "${REALM}" \
  -s realm="${REALM}" \
  -s userId="${PADMIN_ID}" \
  -s groupId="${GROUP_ID}" \
  -n 2>/dev/null || true

echo "  ${PADMIN_EMAIL} / ${PADMIN_PASSWORD} -> platform-admins"

echo ""
echo "=== Keycloak Bootstrap Complete ==="
echo ""
echo "  Group:  platform-admins"
echo "  User:   ${PADMIN_EMAIL} / ${PADMIN_PASSWORD}"
echo ""
