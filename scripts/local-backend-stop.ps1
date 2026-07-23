# Stop Java processes listening on MTP service ports.
. (Join-Path $PSScriptRoot "_lib/common.ps1")

Write-Host "Stopping local backend services..."
foreach ($svc in Get-MtpServices) {
    Stop-MtpPort -Port $svc.Port
}
Write-Host "Done."
