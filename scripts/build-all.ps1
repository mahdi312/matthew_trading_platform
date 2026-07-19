# Build all Maven modules (contracts + infra + services). Requires JDK 25+.
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Import-MtpDotEnv

Initialize-MtpJava25
Test-MtpCommand mvn

Write-Host "Building all backend modules..."
Invoke-MtpMaven clean install "-DskipTests"
Write-Host "Done. Artifacts are under each module's target/ folder."
