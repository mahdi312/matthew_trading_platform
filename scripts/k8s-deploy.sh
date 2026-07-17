#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib/common.sh"

cd "$(mtp_root)"
mtp_load_dotenv
mtp_require docker kubectl

build_image() {
  local name="$1" dockerfile="$2" context="${3:-.}"
  echo "  docker build -t mtp/${name}:latest -f ${dockerfile} ${context}"
  docker build -t "mtp/${name}:latest" -f "$dockerfile" "$context"
}

echo "Building images..."
build_image discovery-service infra/discovery-service/Dockerfile
build_image config-service infra/config-service/Dockerfile
build_image gateway-service infra/gateway-service/Dockerfile
build_image identity-service services/identity-service/Dockerfile
build_image market-service services/market-service/Dockerfile
build_image trading-service services/trading-service/Dockerfile
build_image notification-service services/notification-service/Dockerfile
build_image reference-data-service services/reference-data-service/Dockerfile
build_image ai-service services/ai-service/Dockerfile
build_image alert-service services/alert-service/Dockerfile
build_image frontend frontend/Dockerfile frontend

if command -v kind >/dev/null 2>&1; then
  cluster="$(kind get clusters 2>/dev/null | head -n1 || true)"
  if [[ -n "$cluster" ]]; then
    echo "Loading images into kind cluster '$cluster'..."
    for img in discovery-service config-service gateway-service identity-service \
               market-service trading-service notification-service reference-data-service \
               ai-service alert-service frontend; do
      kind load docker-image "mtp/${img}:latest" --name "$cluster"
    done
  fi
fi

kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/redis/
kubectl apply -f k8s/zookeeper/
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/discovery-service/
kubectl apply -f k8s/config-service/
kubectl apply -f k8s/gateway-service/
kubectl apply -f k8s/identity-service/
kubectl apply -f k8s/market-service/
kubectl apply -f k8s/trading-service/
kubectl apply -f k8s/notification-service/
kubectl apply -f k8s/reference-data-service/
kubectl apply -f k8s/ai-service/
kubectl apply -f k8s/alert-service/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml

echo ""
echo "Add to /etc/hosts: 127.0.0.1 mtp.local"
echo "Open: http://mtp.local"
echo "Status: kubectl get pods -n mtp"
