#!/bin/sh
set -e

GATEWAY_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
mkdir -p /usr/share/nginx/html/assets
cat > /usr/share/nginx/html/assets/config.json <<EOF
{
  "gatewayBaseUrl": "${GATEWAY_URL}"
}
EOF

exec nginx -g "daemon off;"
