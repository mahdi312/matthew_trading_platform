# One-click local dev: infra + backend service windows.
. "$PSScriptRoot\_lib\common.ps1"

Write-Host "=== MTP local full stack (infra + backend) ==="
& "$PSScriptRoot\local-infra-up.ps1"
Start-Sleep -Seconds 5
& "$PSScriptRoot\local-backend-start.ps1"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  Frontend:  scripts\local-frontend-start.ps1   -> http://localhost:4200"
Write-Host "  Desktop:   scripts\local-desktop-start.ps1    -> JavaFX app"
Write-Host "  Stop:      scripts\local-backend-stop.ps1 && scripts\local-infra-down.ps1"
