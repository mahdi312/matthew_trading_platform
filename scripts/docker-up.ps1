# Build and start the full stack with Docker Compose.
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Import-MtpDotEnv
Test-MtpCommand docker

Write-Host "Building and starting full Docker Compose stack..."
docker compose up -d --build

Write-Host ""
Write-Host "Stack URLs:"
Write-Host "  Frontend   http://localhost:4200"
Write-Host "  Gateway    http://localhost:8080"
Write-Host "  Eureka     http://localhost:8761"
Write-Host "  pgAdmin    http://localhost:5050"
Write-Host ""
Write-Host "Logs:  docker compose logs -f gateway-service"
Write-Host "Stop:  scripts\docker-down.ps1"
