# FlowState Deployment

## Prerequisites
- [Azure CLI](https://aka.ms/installazurecli) installed
- Azure subscription with credits activated (my.visualstudio.com)
- Java 17 installed locally

## First-Time Setup

```powershell
.\deploy\deploy.ps1 -All
```

This will:
1. Create Resource Group (`flowstate-rg`) in Central India
2. Create App Service Plan (Linux, B1 tier)
3. Create Web App with Java 17 runtime
4. Configure environment variables (Redis, ports)
5. Build the JAR
6. Deploy to Azure

## After Code Changes

```powershell
.\deploy\quick-deploy.ps1
```

Or separately:
```powershell
.\deploy\deploy.ps1 -Build
.\deploy\deploy.ps1 -Deploy
```

## Check Status

```powershell
.\deploy\deploy.ps1 -Status
```

## URLs

| Endpoint | URL |
|----------|-----|
| Dashboard | https://flow-state-a2f7h7bjckgsdza6.southeastasia-01.azurewebsites.net/ |
| Health | https://flowstate-api.azurewebsites.net/v1/health |
| API | https://flowstate-api.azurewebsites.net/v1/memory/{key} |

## Environment Variables (Azure)

| Variable | Value | Purpose |
|----------|-------|---------|
| SERVER_PORT | 8080 | Main app port (Azure expects 8080) |
| MANAGEMENT_PORT | 9091 | Actuator port |
| REDIS_HOST | plough-pampas-industry-12379.db.redis.io | Redis Cloud |
| REDIS_PORT | 15199 | Redis port |
| REDIS_USERNAME | default | Redis user |
| REDIS_PASSWORD | *** | Redis password |
| WEBSITES_PORT | 8080 | Tells Azure which port to route to |

> **Note:** Azure App Service routes external port 443 (HTTPS) to your app's internal port 8080.
> Locally you use 9090, but Azure expects 8080 (set via SERVER_PORT env var).

## Architecture

```
User → HTTPS (443) → Azure App Service → :8080 (FlowState JAR)
                                              ↓
                                         Redis Cloud (external)
```

## Cost
- App Service B1: ~$13/month (covered by MSFT employee credits)
- Redis Cloud: Free tier (30MB)
- **Total: $0 out of pocket**
