#!/usr/bin/env bash
# Shared helpers for MTP bash run scripts.

set -euo pipefail

mtp_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd
}

mtp_load_dotenv() {
  local root
  root="$(mtp_root)"
  if [[ ! -f "$root/.env" && -f "$root/.env.example" ]]; then
    cp "$root/.env.example" "$root/.env"
    echo "Created .env from .env.example — fill in API keys before production use."
  fi
  if [[ -f "$root/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$root/.env"
    set +a
  fi
}

mtp_require() {
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "Required command not found: $cmd" >&2; exit 1; }
  done
}

mtp_logs_dir() {
  local dir
  dir="$(mtp_root)/scripts/logs"
  mkdir -p "$dir"
  echo "$dir"
}

mtp_start_service_bg() {
  local name="$1"
  local path="$2"
  local port="$3"
  local log
  log="$(mtp_logs_dir)/${name}.log"
  (
    cd "$path"
    nohup mvn spring-boot:run -q >"$log" 2>&1 &
    echo $! > "$(mtp_logs_dir)/${name}.pid"
  )
  echo "  -> $name on port $port (log: $log)"
}

mtp_stop_service_bg() {
  local name="$1"
  local pid_file
  pid_file="$(mtp_logs_dir)/${name}.pid"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    kill "$pid" 2>/dev/null || true
    rm -f "$pid_file"
    echo "  stopped $name (pid $pid)"
  fi
}

mtp_stop_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      echo "$pids" | xargs kill -9 2>/dev/null || true
      echo "  stopped listener on port $port"
    fi
  fi
}

mtp_wait_compose_healthy() {
  local service="$1"
  local timeout="${2:-120}"
  local root elapsed=0
  root="$(mtp_root)"
  cd "$root"
  while (( elapsed < timeout )); do
    local status
    status="$(docker compose ps "$service" --format '{{.Health}}' 2>/dev/null || true)"
    if [[ "$status" == "healthy" ]]; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

MTP_SERVICES=(
  "discovery-service|infra/discovery-service|8761"
  "config-service|infra/config-service|8888"
  "gateway-service|infra/gateway-service|8080"
  "identity-service|services/identity-service|8081"
  "market-service|services/market-service|8082"
  "trading-service|services/trading-service|8083"
  "notification-service|services/notification-service|8084"
  "reference-data-service|services/reference-data-service|8085"
  "ai-service|services/ai-service|8089"
  "alert-service|services/alert-service|8087"
)
