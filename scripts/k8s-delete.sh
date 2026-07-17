#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)"
kubectl delete namespace mtp --ignore-not-found
echo "Namespace mtp deleted."
