#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

ROOT="$(mtp_root)"
cd "$ROOT"
mtp_require java mvn

echo "Building all backend modules..."
mvn clean install -DskipTests
echo "Done."
