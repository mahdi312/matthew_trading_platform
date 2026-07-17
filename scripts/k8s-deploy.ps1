# Build Docker images tagged mtp/* and deploy Kubernetes manifests.
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Import-MtpDotEnv
Test-MtpCommand docker
Test-MtpCommand kubectl

$images = @(
    "discovery-service:infra/discovery-service/Dockerfile"
    "config-service:infra/config-service/Dockerfile"
    "gateway-service:infra/gateway-service/Dockerfile"
    "identity-service:services/identity-service/Dockerfile"
    "market-service:services/market-service/Dockerfile"
    "trading-service:services/trading-service/Dockerfile"
    "notification-service:services/notification-service/Dockerfile"
    "reference-data-service:services/reference-data-service/Dockerfile"
    "ai-service:services/ai-service/Dockerfile"
    "alert-service:services/alert-service/Dockerfile"
    "frontend:frontend/Dockerfile:frontend"
)

Write-Host "Building Kubernetes images (tag: mtp/<service>:latest)..."
foreach ($entry in $images) {
    $parts = $entry -split ":"
    $name = $parts[0]
    $dockerfile = $parts[1]
    $context = if ($parts.Length -gt 2) { $parts[2] } else { "." }
    $tag = "mtp/${name}:latest"
    Write-Host "  docker build -t $tag -f $dockerfile $context"
    docker build -t $tag -f $dockerfile $context
}

# Load into kind if available
if (Get-Command kind -ErrorAction SilentlyContinue) {
    $cluster = kind get clusters 2>$null | Select-Object -First 1
    if ($cluster) {
        Write-Host "Loading images into kind cluster '$cluster'..."
        foreach ($entry in $images) {
            $name = ($entry -split ":")[0]
            kind load docker-image "mtp/${name}:latest" --name $cluster
        }
    }
}

Write-Host "Applying Kubernetes manifests..."
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

Write-Host ""
Write-Host "Deployment started. Add to hosts file:  127.0.0.1 mtp.local"
Write-Host "Then open: http://mtp.local  (requires nginx Ingress Controller)"
Write-Host "Status:    kubectl get pods -n mtp"
