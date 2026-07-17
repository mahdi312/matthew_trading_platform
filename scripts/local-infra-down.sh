#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)"
mtp_require docker

docker compose stop postgres redis zookeeper kafka pgadmin 2>/dev/null || true
echo "Infrastructure stopped."
