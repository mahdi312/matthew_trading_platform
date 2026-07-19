#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)/desktop"
mtp_load_dotenv
export GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
mtp_use_java21
mtp_require mvn

echo "Starting desktop client (Gateway: $GATEWAY_BASE_URL)"
mvn -q javafx:run
