# Start Postgres, Redis, ZooKeeper, and Kafka via Docker Compose.
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Import-MtpDotEnv
Initialize-MtpLocalDevDefaults
Test-MtpCommand docker

Write-Host "Starting local infrastructure (postgres, redis, zookeeper, kafka)..."
Invoke-MtpDockerCompose -ComposeArgs @('up', '-d', 'postgres', 'redis', 'zookeeper', 'kafka')

foreach ($svc in @("postgres", "redis", "zookeeper", "kafka")) {
    if (Wait-MtpDockerHealthy $svc 180) {
        Write-Host "  $svc is healthy"
    } else {
        Write-Warning "$svc did not report healthy within timeout — check: docker compose logs $svc"
    }
}

Write-Host ""
Write-Host "Infrastructure URLs:"
Write-Host "  Postgres  localhost:5432  (DBs: mtp_identity, mtp_market, mtp_trading, mtp_alert)"
Write-Host "  Redis     localhost:6379"
Write-Host "  Kafka     localhost:9092"
Write-Host "  ZooKeeper localhost:2181"
Write-Host "  pgAdmin   http://localhost:5050  (if enabled in compose)"
