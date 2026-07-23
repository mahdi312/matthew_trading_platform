#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

ROOT="$(mtp_root)"
cd "$ROOT"
mtp_load_dotenv
mtp_local_dev_defaults
mtp_use_java25
mtp_require mvn

echo "Starting backend services in background (logs under scripts/logs/)..."
echo "Prerequisite: ./scripts/local-infra-up.sh"
echo "Kafka bootstrap: $KAFKA_BOOTSTRAP_SERVERS"

for entry in "${MTP_SERVICES[@]}"; do
  IFS='|' read -r name relpath port <<< "$entry"
  mtp_start_service_bg "$name" "$ROOT/$relpath" "$port"
  if [[ "$name" == "discovery-service" || "$name" == "config-service" || "$name" == "gateway-service" ]]; then
    sleep 8
  else
    sleep 2
  fi
done

echo ""
echo "Eureka:  http://localhost:8761"
echo "Gateway: http://localhost:8080"
echo "Stop:    ./scripts/local-backend-stop.sh"
