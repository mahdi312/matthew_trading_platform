# Build Docker images tagged mtp/* and deploy Kubernetes manifests.
. (Join-Path $PSScriptRoot "_lib/common.ps1")

$Root = Get-MtpRoot
Set-Location $Root
Import-MtpDotEnv
Test-MtpCommand docker
Test-MtpCommand kubectl

$images = @(
    @{ Name = "discovery-service";      Dockerfile = "infra/discovery-service/Dockerfile";         Context = "." }
    @{ Name = "config-service";         Dockerfile = "infra/config-service/Dockerfile";            Context = "." }
    @{ Name = "gateway-service";        Dockerfile = "infra/gateway-service/Dockerfile";           Context = "." }
    @{ Name = "identity-service";       Dockerfile = "services/identity-service/Dockerfile";       Context = "." }
    @{ Name = "market-service";         Dockerfile = "services/market-service/Dockerfile";         Context = "." }
    @{ Name = "trading-service";        Dockerfile = "services/trading-service/Dockerfile";        Context = "." }
    @{ Name = "notification-service";   Dockerfile = "services/notification-service/Dockerfile";   Context = "." }
    @{ Name = "reference-data-service"; Dockerfile = "services/reference-data-service/Dockerfile"; Context = "." }
    @{ Name = "ai-service";             Dockerfile = "services/ai-service/Dockerfile";             Context = "." }
    @{ Name = "alert-service";          Dockerfile = "services/alert-service/Dockerfile";          Context = "." }
    @{ Name = "frontend";               Dockerfile = "frontend/Dockerfile";                        Context = "frontend" }
)

Write-Host "Building Kubernetes images (tag: mtp/<service>:latest)..."
foreach ($img in $images) {
    $tag = "mtp/$($img.Name):latest"
    Write-Host "  docker build -t $tag -f $($img.Dockerfile) $($img.Context)"
    docker build -t $tag -f $img.Dockerfile $img.Context
    Assert-MtpLastExitCode "docker build $tag"
}

if (Get-Command kind -ErrorAction SilentlyContinue) {
    $cluster = kind get clusters 2>$null | Select-Object -First 1
    if ($cluster) {
        Write-Host "Loading images into kind cluster '$cluster'..."
        foreach ($img in $images) {
            kind load docker-image "mtp/$($img.Name):latest" --name $cluster
            Assert-MtpLastExitCode "kind load mtp/$($img.Name):latest"
        }
    }
}

Write-Host "Applying Kubernetes manifests..."
$manifests = @(
    "k8s/namespace.yaml"
    "k8s/configmaps/"
    "k8s/secrets/"
    "k8s/postgres/"
    "k8s/redis/"
    "k8s/zookeeper/"
    "k8s/kafka/"
    "k8s/discovery-service/"
    "k8s/config-service/"
    "k8s/gateway-service/"
    "k8s/identity-service/"
    "k8s/market-service/"
    "k8s/trading-service/"
    "k8s/notification-service/"
    "k8s/reference-data-service/"
    "k8s/ai-service/"
    "k8s/alert-service/"
    "k8s/frontend/"
    "k8s/ingress.yaml"
)
foreach ($m in $manifests) {
    if (-not (Test-Path $m)) {
        Write-Warning "Skipping missing manifest: $m"
        continue
    }
    kubectl apply -f $m
    Assert-MtpLastExitCode "kubectl apply -f $m"
}

Write-Host ""
Write-Host "Deployment started. Add to hosts file:  127.0.0.1 mtp.local"
Write-Host "Then open: http://mtp.local  (requires nginx Ingress Controller)"
Write-Host "Status:    kubectl get pods -n mtp"
