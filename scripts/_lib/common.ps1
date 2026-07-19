# Shared helpers for MTP PowerShell run scripts.
$ErrorActionPreference = "Stop"

function Get-MtpRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

function Assert-MtpLastExitCode {
    param([Parameter(Mandatory)][string]$Action)
    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE"
    }
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
            $line = $_.Trim()
            if (-not $line -or $line.StartsWith("#")) { return }
            $eq = $line.IndexOf("=")
            if ($eq -lt 1) { return }
            $name = $line.Substring(0, $eq).Trim()
            $value = $line.Substring($eq + 1).Trim().Trim('"').Trim("'")
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Initialize-MtpLocalDevDefaults {
    # Host-mapped Kafka port (Windows Hyper-V often reserves 9081-9180).
    if (-not $env:KAFKA_BOOTSTRAP_SERVERS) {
        $env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
    }
}

function Test-MtpCommand {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found on PATH: $Name"
    }
}

function Get-MtpPreferredShell {
    $pwsh = Get-Command pwsh -ErrorAction SilentlyContinue
    if ($pwsh) { return $pwsh.Source }
    $ps = Get-Command powershell -ErrorAction SilentlyContinue
    if ($ps) { return $ps.Source }
    throw "Neither pwsh nor powershell found on PATH"
}

function Get-MtpJavaMajorVersion {
    param([string]$JavaHome)
    $javaExe = if ($JavaHome) { Join-Path $JavaHome "bin\java.exe" } else { "java" }
    if ($JavaHome -and -not (Test-Path $javaExe)) { return $null }
    $out = & $javaExe -version 2>&1 | Out-String
    if ($out -match 'version "(\d+)') { return [int]$Matches[1] }
    return $null
}

function Find-MtpJdkHome {
    param([int]$MinMajor = 25)
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:MTP_JAVA_HOME) { [void]$candidates.Add($env:MTP_JAVA_HOME) }
    if ($env:JAVA_HOME) { [void]$candidates.Add($env:JAVA_HOME) }

    $patterns = @(
        "D:\java\jdk-$MinMajor"
        "D:\java\jdk-$MinMajor*"
        "C:\Program Files\Microsoft\jdk-$MinMajor*"
        "C:\Program Files\Eclipse Adoptium\jdk-$MinMajor*"
        "C:\Program Files\Temurin\jdk-$MinMajor*"
        "C:\Program Files\Java\jdk-$MinMajor*"
        "C:\Program Files\Zulu\zulu-$MinMajor*"
        "C:\Program Files\BellSoft\LibericaJDK-$MinMajor*"
        "$env:USERPROFILE\.jdks\jdk-$MinMajor*"
        "$env:USERPROFILE\.jdks\temurin-$MinMajor*"
    )
    if ($MinMajor -eq 21) {
        $patterns += @(
            "D:\java\jdk-21"
            "D:\java\jdk-21.0.11"
            "D:\java\.jdk-21.0.11.intellij"
        )
    }
    foreach ($pattern in $patterns) {
        Get-Item -Path $pattern -ErrorAction SilentlyContinue | ForEach-Object {
            [void]$candidates.Add($_.FullName)
        }
    }
    foreach ($jdkHome in $candidates) {
        if (-not $jdkHome) { continue }
        if (-not (Test-Path (Join-Path $jdkHome "bin\java.exe"))) { continue }
        $major = Get-MtpJavaMajorVersion -JavaHome $jdkHome
        if ($null -ne $major -and $major -ge $MinMajor) { return $jdkHome }
    }
    return $null
}

function Initialize-MtpJava {
    param(
        [int]$MinMajor = 25,
        [string]$Purpose = "microservices"
    )
    $jdk = Find-MtpJdkHome -MinMajor $MinMajor
    if (-not $jdk) {
        throw @"
JDK $MinMajor+ is required for $Purpose.
Install a matching JDK, then either:
  - set JAVA_HOME (or MTP_JAVA_HOME) to that JDK, or
  - install under D:\java\jdk-$MinMajor
"@
    }
    $env:JAVA_HOME = $jdk
    $jdkBin = Join-Path $jdk "bin"
    $parts = @($jdkBin) + @(
        $env:Path -split ';' |
            Where-Object { $_ -and $_ -ne $jdkBin -and $_ -notmatch 'Oracle\\Java\\javapath|java8path' }
    )
    $env:Path = ($parts -join ';')
    $major = Get-MtpJavaMajorVersion -JavaHome $jdk
    Write-Host "Using JDK $major at $jdk"
}

function Initialize-MtpJava25 {
    Initialize-MtpJava -MinMajor 25 -Purpose "microservices (java.version=25)"
}

function Initialize-MtpJava21 {
    Initialize-MtpJava -MinMajor 21 -Purpose "desktop (java.version=21)"
}

function Invoke-MtpMaven {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$MavenArgs
    )
    Test-MtpCommand mvn
    & mvn @MavenArgs
    Assert-MtpLastExitCode "Maven ($($MavenArgs -join ' '))"
}

function Get-MtpServices {
    $root = Get-MtpRoot
    return @(
        @{ Name = "discovery-service";      Path = Join-Path $root "infra\discovery-service";         Port = 8761; Order = 1 }
        @{ Name = "config-service";         Path = Join-Path $root "infra\config-service";            Port = 8888; Order = 2 }
        @{ Name = "gateway-service";        Path = Join-Path $root "infra\gateway-service";           Port = 8080; Order = 3 }
        @{ Name = "identity-service";       Path = Join-Path $root "services\identity-service";       Port = 8081; Order = 4 }
        @{ Name = "market-service";         Path = Join-Path $root "services\market-service";         Port = 8082; Order = 5 }
        @{ Name = "trading-service";        Path = Join-Path $root "services\trading-service";        Port = 8083; Order = 6 }
        @{ Name = "notification-service";   Path = Join-Path $root "services\notification-service";   Port = 8084; Order = 7 }
        @{ Name = "reference-data-service"; Path = Join-Path $root "services\reference-data-service"; Port = 8085; Order = 8 }
        @{ Name = "ai-service";             Path = Join-Path $root "services\ai-service";             Port = 8089; Order = 9 }
        @{ Name = "alert-service";          Path = Join-Path $root "services\alert-service";          Port = 8087; Order = 10 }
    )
}

function Start-MtpServiceWindow {
    param(
        [string]$Name,
        [string]$Path,
        [int]$Port
    )
    if (-not $env:JAVA_HOME) {
        throw "JAVA_HOME is not set. Call Initialize-MtpJava25 before starting services."
    }
    if (-not (Test-Path $Path)) {
        throw "Service path not found: $Path"
    }

    $title = "MTP: $Name (:$Port)"
    $shell = Get-MtpPreferredShell
    # Double single-quotes for safe embedding inside -Command strings.
    $safePath = $Path.Replace("'", "''")
    $safeTitle = $title.Replace("'", "''")
    $safeJavaHome = $env:JAVA_HOME.Replace("'", "''")

    # Bake concrete JAVA_HOME into PATH. Do NOT use `"$env:JAVA_HOME\bin"` —
    # nested quotes are stripped when Start-Process passes -Command and break parsing.
    $cmd = @(
        "`$env:JAVA_HOME = '$safeJavaHome'"
        "`$env:Path = '$safeJavaHome" + "\bin;' + `$env:Path"
        "Set-Location -LiteralPath '$safePath'"
        "`$Host.UI.RawUI.WindowTitle = '$safeTitle'"
        "mvn spring-boot:run"
    ) -join "; "

    # -NoProfile avoids broken profile Import-Module noise (e.g. missing DockerCompletion).
    Start-Process -FilePath $shell -WorkingDirectory $Path -ArgumentList @(
        "-NoProfile", "-NoExit", "-Command", $cmd
    ) | Out-Null
    Write-Host "  -> $Name on port $Port ($(Split-Path $shell -Leaf))"
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
    Push-Location $root
    try {
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        do {
            $status = docker compose ps $Service --format "{{.Health}}" 2>$null
            if ($status -eq "healthy") { return $true }
            # Containers without a healthcheck report empty — treat running as OK.
            if (-not $status) {
                $state = docker compose ps $Service --format "{{.State}}" 2>$null
                if ($state -eq "running") { return $true }
            }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        return $false
    }
    finally {
        Pop-Location
    }
}

function Invoke-MtpDockerCompose {
    <#
    .SYNOPSIS
      Run `docker compose` with an explicit argument array.
      Callers MUST pass an array (e.g. @('up','-d','postgres')).
      Do NOT use remaining-args style `Invoke-MtpDockerCompose up -d ...` —
      PowerShell treats -d as a function parameter and drops it, so compose
      attaches to logs instead of running detached.
    #>
    param(
        [Parameter(Mandatory)]
        [string[]]$ComposeArgs
    )
    Test-MtpCommand docker
    $root = Get-MtpRoot
    Push-Location $root
    try {
        & docker compose @ComposeArgs
        Assert-MtpLastExitCode "docker compose $($ComposeArgs -join ' ')"
    }
    finally {
        Pop-Location
    }
}
