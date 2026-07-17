#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib/common.sh"

echo "Stopping backend services..."
for entry in "${MTP_SERVICES[@]}"; do
  IFS='|' read -r name _ port <<< "$entry"
  mtp_stop_service_bg "$name"
  mtp_stop_port "$port"
done
echo "Done."
