# Stop local infrastructure containers.
. "$PSScriptRoot\_lib\common.ps1"

Test-MtpCommand docker
$Root = Get-MtpRoot
Write-Host "Stopping infrastructure containers..."
Push-Location $Root
try {
    # Tolerate services that are already stopped / not defined.
    docker compose stop postgres redis zookeeper kafka pgadmin 2>$null
}
finally {
    Pop-Location
}
Write-Host "Done. Data volumes are preserved. Use 'docker compose down -v' to wipe volumes."
