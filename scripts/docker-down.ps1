# Stop and remove Docker Compose stack (keeps volumes).
. "$PSScriptRoot\_lib\common.ps1"

Test-MtpCommand docker
Write-Host "Stopping Docker Compose stack..."
Invoke-MtpDockerCompose -ComposeArgs @('down')
Write-Host "Done. Add '-v' to wipe volumes: docker compose down -v"
