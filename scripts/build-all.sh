#!/usr/bin/env bash
# Build all Maven modules (contracts + infra + services). Requires JDK 25+.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

ROOT="$(mtp_root)"
cd "$ROOT"
mtp_load_dotenv
mtp_use_java25
mtp_require mvn

echo "Building all backend modules..."
mvn clean install -DskipTests
echo "Done."
