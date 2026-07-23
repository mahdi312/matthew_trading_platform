#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

mtp_require docker
echo "Stopping infrastructure containers..."
(
  cd "$(mtp_root)"
  # Tolerate services that are already stopped / not defined.
  docker compose stop postgres redis zookeeper kafka pgadmin 2>/dev/null || true
)
echo "Infrastructure stopped."
