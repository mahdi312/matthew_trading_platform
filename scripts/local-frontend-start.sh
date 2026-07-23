#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)/frontend"
mtp_require node npm

[[ -d node_modules ]] || npm install
echo "Starting Angular on http://localhost:4200"
npm start
