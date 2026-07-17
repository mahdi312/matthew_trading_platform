# Start JavaFX desktop client (thin client → Gateway on localhost:8080).
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
$Desktop = Join-Path $Root "desktop"
Set-Location $Desktop

Test-MtpCommand java
Test-MtpCommand mvn

Import-MtpDotEnv
$env:GATEWAY_BASE_URL = if ($env:GATEWAY_BASE_URL) { $env:GATEWAY_BASE_URL } else { "http://localhost:8080" }

Write-Host "Starting desktop client (Gateway: $env:GATEWAY_BASE_URL)"
Write-Host "Prerequisite: backend + gateway running (scripts\local-backend-start.ps1)"
mvn -q javafx:run
