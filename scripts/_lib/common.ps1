# Shared helpers for MTP PowerShell run scripts.

function Get-MtpRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

function Import-MtpDotEnv {
    param([string]$Root = (Get-MtpRoot))
    $envFile = Join-Path $Root ".env"
    if (-not (Test-Path $envFile)) {
        $example = Join-Path $Root ".env.example"
        if (Test-Path $example) {
            Copy-Item $example $envFile
            Write-Host "Created .env from .env.example — fill in API keys before production use."
        }
    }
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
                $name = $matches[1].Trim()
                $value = $matches[2].Trim().Trim('"').Trim("'")
                Set-Item -Path "Env:$name" -Value $value
            }
        }
    }
}

function Test-MtpCommand {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found on PATH: $Name"
    }
}

function Get-MtpServices {
    $root = Get-MtpRoot
    return @(
        @{ Name = "discovery-service";    Path = Join-Path $root "infra\discovery-service";    Port = 8761; Order = 1 }
        @{ Name = "config-service";       Path = Join-Path $root "infra\config-service";       Port = 8888; Order = 2 }
        @{ Name = "gateway-service";      Path = Join-Path $root "infra\gateway-service";      Port = 8080; Order = 3 }
        @{ Name = "identity-service";     Path = Join-Path $root "services\identity-service";  Port = 8081; Order = 4 }
        @{ Name = "market-service";       Path = Join-Path $root "services\market-service";    Port = 8082; Order = 5 }
        @{ Name = "trading-service";      Path = Join-Path $root "services\trading-service";   Port = 8083; Order = 6 }
        @{ Name = "notification-service"; Path = Join-Path $root "services\notification-service"; Port = 8084; Order = 7 }
        @{ Name = "reference-data-service"; Path = Join-Path $root "services\reference-data-service"; Port = 8085; Order = 8 }
        @{ Name = "ai-service";           Path = Join-Path $root "services\ai-service";          Port = 8089; Order = 9 }
        @{ Name = "alert-service";        Path = Join-Path $root "services\alert-service";       Port = 8087; Order = 10 }
    )
}

function Start-MtpServiceWindow {
    param(
        [string]$Name,
        [string]$Path,
        [int]$Port
    )
    $title = "MTP: $Name (:$Port)"
    $cmd = "Set-Location '$Path'; `$host.UI.RawUI.WindowTitle = '$title'; mvn spring-boot:run"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd | Out-Null
    Write-Host "  -> $Name on port $Port (new window)"
}

function Stop-MtpPort {
    param([int]$Port)
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $connections) {
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        if ($proc -and $proc.ProcessName -match 'java|javaw') {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Write-Host "  stopped $($proc.ProcessName) on port $Port"
        }
    }
}

function Wait-MtpDockerHealthy {
    param(
        [string]$Service,
        [int]$TimeoutSec = 120
    )
    $root = Get-MtpRoot
    Set-Location $root
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $status = docker compose ps $Service --format "{{.Health}}" 2>$null
        if ($status -eq "healthy") { return $true }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    return $false
}
