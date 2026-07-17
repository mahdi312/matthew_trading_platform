# Build all Maven modules (contracts + infra + services).
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root

Test-MtpCommand java
Test-MtpCommand mvn

Write-Host "Building all backend modules..."
mvn clean install -DskipTests
Write-Host "Done. Artifacts are under each module's target/ folder."
