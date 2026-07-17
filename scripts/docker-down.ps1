# Stop and remove Docker Compose stack (keeps volumes).
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Test-MtpCommand docker

Write-Host "Stopping Docker Compose stack..."
docker compose down
Write-Host "Done. Add '-v' to 'docker compose down -v' to delete postgres/redis volumes."
