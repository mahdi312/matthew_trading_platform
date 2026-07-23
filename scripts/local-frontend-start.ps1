# Start Angular dev server (ng serve).
. (Join-Path $PSScriptRoot "_lib/common.ps1")

$Root = Get-MtpRoot
$Frontend = Join-Path $Root "frontend"
if (-not (Test-Path $Frontend)) { throw "Frontend module not found: $Frontend" }
Set-Location $Frontend

Test-MtpCommand node
Test-MtpCommand npm

if (-not (Test-Path "node_modules")) {
    Write-Host "Installing frontend dependencies..."
    npm install
    Assert-MtpLastExitCode "npm install"
}

Write-Host "Starting Angular on http://localhost:4200 (gateway: http://localhost:8080)"
npm start
Assert-MtpLastExitCode "npm start"
