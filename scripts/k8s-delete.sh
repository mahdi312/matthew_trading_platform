#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/common.sh
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)"
mtp_require kubectl
kubectl delete namespace mtp --ignore-not-found
echo "Namespace mtp deleted."
