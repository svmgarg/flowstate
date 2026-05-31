#!/usr/bin/env pwsh
# FlowState - Azure App Service Deployment Script (Az PowerShell Module)
# Usage: .\deploy\deploy.ps1 [-Build] [-Deploy] [-Setup] [-All] [-Status]

param(
    [switch]$Setup,    # First-time Azure resource setup
    [switch]$Build,    # Build the JAR
    [switch]$Deploy,   # Deploy JAR to Azure
    [switch]$All,      # Do everything
    [switch]$Status    # Check app status
)

$ErrorActionPreference = "Continue"

# === Configuration ===
$APP_NAME = "flow-state"
$RESOURCE_GROUP = "FlowState"
$LOCATION = "southeastasia"
$PLAN_NAME = "FlowState-ASP"
$JAR_PATH = "target\flowstate-service-1.0.0.jar"
$PROJECT_ROOT = Split-Path -Parent $PSScriptRoot
$APP_URL = "https://flow-state-a2f7h7bjckgsdza6.southeastasia-01.azurewebsites.net"

# === Output Helpers ===
function Write-Banner {
    Write-Host ""
    Write-Host "  ⚡ FlowState Deploy" -ForegroundColor Cyan
    Write-Host "  ─────────────────────────────────────" -ForegroundColor DarkGray
}
function Write-Step($msg) { Write-Host "  ● $msg" -ForegroundColor White }
function Write-Ok($msg) { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Skip($msg) { Write-Host "  → $msg (already exists)" -ForegroundColor DarkYellow }
function Write-Err($msg) { Write-Host "  ✗ $msg" -ForegroundColor Red }
function Write-Info($msg) { Write-Host "    $msg" -ForegroundColor Gray }

# === Verify Azure Connection ===
function Test-AzConnection {
    $context = Get-AzContext
    if (-not $context) {
        Write-Step "Authenticating..."
        Connect-AzAccount | Out-Null
        $context = Get-AzContext
    }
    Write-Ok "Azure: $($context.Account.Id) → $($context.Subscription.Name)"
}

# === Setup: Create Azure Resources ===
function Invoke-Setup {
    Write-Host ""
    Write-Host "  SETUP" -ForegroundColor Cyan
    Write-Host "  ─────" -ForegroundColor DarkGray

    # Resource Group
    Write-Step "Resource Group: $RESOURCE_GROUP"
    $rg = Get-AzResourceGroup -Name $RESOURCE_GROUP -ErrorAction SilentlyContinue
    if ($rg) {
        Write-Skip "Resource group ($($rg.Location))"
    } else {
        New-AzResourceGroup -Name $RESOURCE_GROUP -Location $LOCATION -Force | Out-Null
        Write-Ok "Created in $LOCATION"
    }

    # App Service Plan
    Write-Step "App Service Plan: $PLAN_NAME"
    $plan = Get-AzAppServicePlan -ResourceGroupName $RESOURCE_GROUP -Name $PLAN_NAME -ErrorAction SilentlyContinue
    if ($plan) {
        Write-Skip "Plan ($($plan.Sku.Tier)/$($plan.Sku.Name), Linux)"
    } else {
        New-AzAppServicePlan -ResourceGroupName $RESOURCE_GROUP -Name $PLAN_NAME `
            -Location $LOCATION -Tier "Free" -Linux | Out-Null
        Write-Ok "Created (Free/F1, Linux)"
    }

    # Web App
    Write-Step "Web App: $APP_NAME"
    $app = Get-AzWebApp -ResourceGroupName $RESOURCE_GROUP -Name $APP_NAME -ErrorAction SilentlyContinue
    if ($app) {
        Write-Skip "Web App ($($app.State))"
    } else {
        New-AzWebApp -ResourceGroupName $RESOURCE_GROUP -Name $APP_NAME `
            -AppServicePlan $PLAN_NAME | Out-Null
        Write-Ok "Created"
    }

    # App Settings
    Write-Step "App Settings"
    $settings = @{ "WEBSITES_PORT" = "8080"; "MANAGEMENT_PORT" = "8081" }
    Set-AzWebApp -ResourceGroupName $RESOURCE_GROUP -Name $APP_NAME `
        -AppSettings $settings -ErrorAction SilentlyContinue | Out-Null
    Write-Ok "WEBSITES_PORT=8080, MANAGEMENT_PORT=8081"

    Write-Host ""
    Write-Host "  ─────────────────────────────────────" -ForegroundColor DarkGray
    Write-Ok "Setup complete"
    Write-Info $APP_URL
}

# === Build ===
function Invoke-Build {
    Write-Host ""
    Write-Host "  BUILD" -ForegroundColor Cyan
    Write-Host "  ─────" -ForegroundColor DarkGray

    Push-Location $PROJECT_ROOT
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"

    Write-Step "Compiling + packaging (skipTests)..."
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    .\mvnw.cmd clean package -DskipTests -q 2>&1 | Out-Null

    if ($LASTEXITCODE -ne 0) {
        Write-Err "Build failed! Run without -q for details."
        Pop-Location
        exit 1
    }
    $sw.Stop()

    $jarFile = Join-Path $PROJECT_ROOT $JAR_PATH
    if (Test-Path $jarFile) {
        $size = [math]::Round((Get-Item $jarFile).Length / 1MB, 1)
        Write-Ok "flowstate-service-1.0.0.jar ($size MB) in $([math]::Round($sw.Elapsed.TotalSeconds))s"
    } else {
        Write-Err "JAR not found at expected path"
        Pop-Location
        exit 1
    }
    Pop-Location
}

# === Deploy ===
function Invoke-Deploy {
    Write-Host ""
    Write-Host "  DEPLOY" -ForegroundColor Cyan
    Write-Host "  ──────" -ForegroundColor DarkGray

    $jarFile = Join-Path $PROJECT_ROOT $JAR_PATH
    if (-not (Test-Path $jarFile)) {
        Write-Err "No JAR found. Run with -Build first."
        exit 1
    }

    $size = [math]::Round((Get-Item $jarFile).Length / 1MB, 1)
    Write-Step "Uploading JAR ($size MB) → Azure..."
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    Publish-AzWebApp -ResourceGroupName $RESOURCE_GROUP -Name $APP_NAME `
        -ArchivePath $jarFile -Force | Out-Null
    $sw.Stop()

    if ($?) {
        Write-Ok "Deployed in $([math]::Round($sw.Elapsed.TotalSeconds))s"
        Write-Host ""
        Write-Host "  ═══════════════════════════════════════" -ForegroundColor Green
        Write-Host "  ⚡ FlowState is LIVE" -ForegroundColor Green
        Write-Host "  ═══════════════════════════════════════" -ForegroundColor Green
        Write-Host ""
        Write-Info "App:    $APP_URL"
        Write-Info "Health: $APP_URL/v1/health"
        Write-Info "UI:     $APP_URL/flowstate.html"
        Write-Host ""
    } else {
        Write-Err "Deployment failed!"
        exit 1
    }
}

# === Status ===
function Get-AppStatus {
    Write-Host ""
    Write-Host "  STATUS" -ForegroundColor Cyan
    Write-Host "  ──────" -ForegroundColor DarkGray

    $app = Get-AzWebApp -ResourceGroupName $RESOURCE_GROUP -Name $APP_NAME -ErrorAction SilentlyContinue
    if (-not $app) {
        Write-Err "App not found. Run with -Setup first."
        return
    }

    $stateColor = if ($app.State -eq "Running") { "Green" } else { "Yellow" }
    Write-Host "  State:    $($app.State)" -ForegroundColor $stateColor
    Write-Info "Location: $($app.Location)"
    Write-Info "Runtime:  $($app.SiteConfig.LinuxFxVersion)"
    Write-Info "URL:      $APP_URL"

    Write-Host ""
    Write-Step "Health check..."
    try {
        $response = Invoke-RestMethod -Uri "$APP_URL/v1/health" -TimeoutSec 15
        Write-Ok "Status: $($response.status) | Redis: $($response.redis)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code) {
            Write-Err "HTTP $code — app may still be starting (takes ~30s)"
        } else {
            Write-Err "Unreachable — $($_.Exception.Message)"
        }
    }
}

# === Main ===
Write-Banner
Test-AzConnection

if ($All) {
    Invoke-Setup
    Invoke-Build
    Invoke-Deploy
} elseif ($Setup -or $Build -or $Deploy -or $Status) {
    if ($Setup) { Invoke-Setup }
    if ($Build) { Invoke-Build }
    if ($Deploy) { Invoke-Deploy }
    if ($Status) { Get-AppStatus }
} else {
    Write-Host ""
    Write-Host "  Usage:" -ForegroundColor White
    Write-Host "    .\deploy\deploy.ps1 -Setup     Create Azure resources (first time)" -ForegroundColor Gray
    Write-Host "    .\deploy\deploy.ps1 -Build     Build JAR locally" -ForegroundColor Gray
    Write-Host "    .\deploy\deploy.ps1 -Deploy    Push JAR to Azure" -ForegroundColor Gray
    Write-Host "    .\deploy\deploy.ps1 -Status    Check app health" -ForegroundColor Gray
    Write-Host "    .\deploy\deploy.ps1 -All       Setup + Build + Deploy" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  Quick:" -ForegroundColor White
    Write-Host "    .\deploy\deploy.ps1 -Build -Deploy    (after code changes)" -ForegroundColor Gray
    Write-Host ""
}
