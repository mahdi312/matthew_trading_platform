# Start Postgres (with host port 5432) and run the JavaFX app against it.
# Usage (from repo root):  .\scripts\run-with-postgres.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "Starting PostgreSQL..."
docker compose up -d postgres | Out-Null

$deadline = (Get-Date).AddMinutes(2)
do {
    $status = docker compose ps postgres --format "{{.Status}}" 2>$null
    if ($status -match "healthy") { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

$ports = docker compose ps postgres --format "{{.Ports}}" 2>$null
if ($ports -notmatch "5432->5432") {
    Write-Error @"
Postgres is not published on localhost:5432 (ports: $ports).
Recreate the container:  docker compose up -d postgres --force-recreate
"@
}

Write-Host "Postgres ready ($ports). Launching app with docker profile..."
$env:SPRING_PROFILES_ACTIVE = "docker"
$env:POSTGRES_HOST = "localhost"
$env:POSTGRES_PORT = "5432"
mvn javafx:run
