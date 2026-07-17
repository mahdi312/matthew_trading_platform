#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib/common.sh"

ROOT="$(mtp_root)"
cd "$ROOT"
mtp_load_dotenv
mtp_require docker

echo "Starting local infrastructure..."
docker compose up -d postgres redis zookeeper kafka

for svc in postgres redis kafka; do
  if mtp_wait_compose_healthy "$svc" 120; then
    echo "  $svc is healthy"
  else
    echo "  WARNING: $svc not healthy — check: docker compose logs $svc"
  fi
done

echo ""
echo "Infrastructure URLs:"
echo "  Postgres  localhost:5432"
echo "  Redis     localhost:6379"
echo "  Kafka     localhost:9092"
