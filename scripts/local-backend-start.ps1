# Start all Spring Boot services in separate PowerShell windows (local dev).
. "$PSScriptRoot\_lib\common.ps1"

$Root = Get-MtpRoot
Set-Location $Root
Import-MtpDotEnv
Initialize-MtpLocalDevDefaults
Initialize-MtpJava25
Test-MtpCommand mvn

Write-Host "Starting backend services (each in its own window)..."
Write-Host "Prerequisite: run scripts\local-infra-up.ps1 first."
Write-Host "Kafka bootstrap: $env:KAFKA_BOOTSTRAP_SERVERS"
Write-Host ""

$services = Get-MtpServices | Sort-Object { $_.Order }
foreach ($svc in $services) {
    Start-MtpServiceWindow -Name $svc.Name -Path $svc.Path -Port $svc.Port
    if ($svc.Order -le 3) { Start-Sleep -Seconds 8 }
    else { Start-Sleep -Seconds 2 }
}

Write-Host ""
Write-Host "Verify Eureka: http://localhost:8761"
Write-Host "API Gateway:   http://localhost:8080"
Write-Host "Stop all:      scripts\local-backend-stop.ps1"
