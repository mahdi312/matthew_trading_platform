#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)"
mtp_load_dotenv
mtp_require docker

docker compose up -d --build
echo ""
echo "Frontend: http://localhost:4200"
echo "Gateway:  http://localhost:8080"
echo "Stop:     ./scripts/docker-down.sh"
