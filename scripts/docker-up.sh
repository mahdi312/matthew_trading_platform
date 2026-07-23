#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)"
mtp_load_dotenv
mtp_local_dev_defaults
mtp_require docker

mtp_docker_compose up -d --build
echo ""
echo "Frontend: http://localhost:4200"
echo "Gateway:  http://localhost:8080"
echo "Kafka:    localhost:9092"
echo "Postgres: localhost:5432"
echo "Stop:     ./scripts/docker-down.sh"
