#!/usr/bin/env bash
# Shared helpers for MTP bash run scripts.

set -euo pipefail

mtp_root() {
  # Command substitution runs in a subshell — caller's cwd is unchanged.
  (cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
}

mtp_assert() {
  local action="$1"
  local code="${2:-$?}"
  if [[ "$code" -ne 0 ]]; then
    echo "$action failed with exit code $code" >&2
    exit "$code"
  fi
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

mtp_local_dev_defaults() {
  # Host-mapped Kafka port (Windows Hyper-V often reserves 9081-9180).
  export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
}

mtp_require() {
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "Required command not found: $cmd" >&2; exit 1; }
  done
}

mtp_java_major() {
  local java_bin="${1:-java}"
  "$java_bin" -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/'
}

mtp_find_jdk_home() {
  local min_major="${1:-25}"
  local candidates=()
  [[ -n "${MTP_JAVA_HOME:-}" ]] && candidates+=("$MTP_JAVA_HOME")
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")

  local dir
  shopt -s nullglob
  for dir in \
    /usr/lib/jvm/temurin-"${min_major}"-jdk* \
    /usr/lib/jvm/java-"${min_major}"-openjdk* \
    /usr/lib/jvm/jdk-"${min_major}"* \
    "$HOME"/.jdks/jdk-"${min_major}"* \
    "$HOME"/.jdks/temurin-"${min_major}"* \
    /opt/homebrew/opt/openjdk@"${min_major}" \
    /usr/local/opt/openjdk@"${min_major}" \
    /d/java/jdk-"${min_major}" \
    /d/java/jdk-"${min_major}"* \
    "/c/Program Files/Microsoft"/jdk-"${min_major}"* \
    "/c/Program Files/Eclipse Adoptium"/jdk-"${min_major}"*
  do
    [[ -d "$dir" ]] && candidates+=("$dir")
  done

  local jvm
  for jvm in /Library/Java/JavaVirtualMachines/*"${min_major}"*.jdk; do
    [[ -d "$jvm/Contents/Home" ]] && candidates+=("$jvm/Contents/Home")
  done
  shopt -u nullglob

  local home java_bin major
  for home in "${candidates[@]}"; do
    java_bin="$home/bin/java"
    [[ -x "$java_bin" ]] || continue
    major="$(mtp_java_major "$java_bin" || true)"
    if [[ -n "$major" && "$major" -ge "$min_major" ]]; then
      printf '%s\n' "$home"
      return 0
    fi
  done
  return 1
}

mtp_use_java() {
  local min_major="${1:-25}"
  local purpose="${2:-microservices}"
  local jdk
  if ! jdk="$(mtp_find_jdk_home "$min_major")"; then
    cat >&2 <<EOF
JDK ${min_major}+ is required for ${purpose}.
Install a matching JDK, then either:
  - set JAVA_HOME (or MTP_JAVA_HOME) to that JDK, or
  - install under a standard path (e.g. /usr/lib/jvm/temurin-${min_major}-jdk, D:\\java\\jdk-${min_major})
EOF
    exit 1
  fi
  export JAVA_HOME="$jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "Using JDK $(mtp_java_major) at $JAVA_HOME"
}

mtp_use_java25() {
  mtp_use_java 25 "microservices (java.version=25)"
}

mtp_use_java21() {
  mtp_use_java 21 "desktop (java.version=21)"
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
  if [[ ! -d "$path" ]]; then
    echo "Service path not found: $path" >&2
    exit 1
  fi
  (
    cd "$path"
    # Inherit JAVA_HOME/PATH/.env from parent; pin JAVA_HOME in the child too.
    export JAVA_HOME="${JAVA_HOME:?JAVA_HOME not set — call mtp_use_java25 first}"
    export PATH="$JAVA_HOME/bin:$PATH"
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
    # Kill Maven wrapper process group if possible
    kill "$pid" 2>/dev/null || true
    # Also try child java processes started by that mvn
    pkill -P "$pid" 2>/dev/null || true
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
      # shellcheck disable=SC2086
      kill -9 $pids 2>/dev/null || true
      echo "  stopped listener on port $port"
    fi
  fi
}

mtp_wait_compose_healthy() {
  local service="$1"
  local timeout="${2:-120}"
  local root elapsed=0 status state
  root="$(mtp_root)"
  (
    cd "$root"
    while (( elapsed < timeout )); do
      status="$(docker compose ps "$service" --format '{{.Health}}' 2>/dev/null || true)"
      if [[ "$status" == "healthy" ]]; then
        exit 0
      fi
      if [[ -z "$status" ]]; then
        state="$(docker compose ps "$service" --format '{{.State}}' 2>/dev/null || true)"
        if [[ "$state" == "running" ]]; then
          exit 0
        fi
      fi
      sleep 2
      elapsed=$((elapsed + 2))
    done
    exit 1
  )
}

mtp_docker_compose() {
  mtp_require docker
  local root
  root="$(mtp_root)"
  (
    cd "$root"
    docker compose "$@"
  )
  mtp_assert "docker compose $*"
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
