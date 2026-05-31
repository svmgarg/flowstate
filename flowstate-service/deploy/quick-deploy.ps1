#!/usr/bin/env pwsh
# FlowState - Quick rebuild and deploy (after code changes)
# Usage: .\deploy\quick-deploy.ps1

$ErrorActionPreference = "Stop"

Write-Host "`n=== FlowState Quick Deploy ===" -ForegroundColor Cyan

# Build
Write-Host "`n>> Building..." -ForegroundColor Cyan
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
Push-Location (Split-Path -Parent $PSScriptRoot)
.\mvnw.cmd clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed!" -ForegroundColor Red; Pop-Location; exit 1 }
Write-Host "   Build OK" -ForegroundColor Green

# Deploy
Write-Host "`n>> Deploying to Azure..." -ForegroundColor Cyan
az webapp deploy `
    --resource-group "FlowState" `
    --name "flow-state" `
    --src-path "target\flowstate-service-1.0.0.jar" `
    --type jar `
    --output none

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n=== Deployed! ===" -ForegroundColor Green
    Write-Host "https://flow-state-a2f7h7bjckgsdza6.southeastasia-01.azurewebsites.net" -ForegroundColor Yellow
} else {
    Write-Host "Deploy failed!" -ForegroundColor Red
    exit 1
}
Pop-Location
