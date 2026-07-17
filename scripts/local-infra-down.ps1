# Stop local infrastructure containers.
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Test-MtpCommand docker

Write-Host "Stopping infrastructure containers..."
docker compose stop postgres redis zookeeper kafka pgadmin 2>$null
Write-Host "Done. Data volumes are preserved. Use 'docker compose down -v' to wipe volumes."
