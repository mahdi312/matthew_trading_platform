#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/local-infra-up.sh"
sleep 5
"$SCRIPT_DIR/local-backend-start.sh"

echo ""
echo "Next: ./scripts/local-frontend-start.sh  or  ./scripts/local-desktop-start.sh"
