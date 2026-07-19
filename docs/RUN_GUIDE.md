# Matthew Trading Platform — Run Guide

How to run **backend** (10 Spring Boot modules), **frontend** (Angular), and **desktop** (JavaFX) in three environments.

One-click scripts live in [`scripts/`](../scripts/). On Windows use **PowerShell** (`.ps1`); on macOS/Linux/Git Bash use **bash** (`.sh`).

---

## Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| Java | **25** (backend), **21** (desktop) | Maven services / JavaFX |
| Maven | 3.9+ | Backend + desktop builds |
| Node.js | 20+ LTS (22 recommended) | Angular 21 frontend |
| Docker + Compose | recent | Infra + full stack |
| kubectl | optional | Kubernetes |

```powershell
java -version
mvn -version
node -version
docker compose version
```

### Secrets

```powershell
cd webapp
copy .env.example .env    # PowerShell
# cp .env.example .env    # bash
```

Edit `.env` with real API keys (BitUnix, OpenAI, SMTP, Telegram, etc.). Never commit `.env`.

---

## Quick reference — one-click scripts

| Goal | Windows (PowerShell) | Bash |
|------|----------------------|------|
| Build all backend JARs | `.\scripts\build-all.ps1` | `./scripts/build-all.sh` |
| Local infra only | `.\scripts\local-infra-up.ps1` | `./scripts/local-infra-up.sh` |
| Local backend (Maven) | `.\scripts\local-backend-start.ps1` | `./scripts/local-backend-start.sh` |
| Stop local backend | `.\scripts\local-backend-stop.ps1` | `./scripts/local-backend-stop.sh` |
| Local frontend | `.\scripts\local-frontend-start.ps1` | `./scripts/local-frontend-start.sh` |
| Local desktop | `.\scripts\local-desktop-start.ps1` | `./scripts/local-desktop-start.sh` |
| **Local all-in-one** (infra + backend) | `.\scripts\local-full-start.ps1` | `./scripts/local-full-start.sh` |
| **Docker full stack** | `.\scripts\docker-up.ps1` | `./scripts/docker-up.sh` |
| Stop Docker stack | `.\scripts\docker-down.ps1` | `./scripts/docker-down.sh` |
| **Kubernetes deploy** | `.\scripts\k8s-deploy.ps1` | `./scripts/k8s-deploy.sh` |
| Delete K8s namespace | `.\scripts\k8s-delete.ps1` | `./scripts/k8s-delete.sh` |

Run all scripts from the **`webapp/`** directory.

---

## Architecture & ports

| Component | Port | Notes |
|-----------|------|-------|
| discovery-service (Eureka) | 8761 | Must start first |
| config-service | 8888 | Serves `infra/config-repo/` |
| gateway-service | 8080 | **Single API entry** for clients |
| identity-service | 8081 | Auth / JWT |
| market-service | 8082 | Market data, charting, WS relay |
| trading-service | 8083 | Orders, Kafka events |
| notification-service | 8084 | Email / Telegram |
| reference-data-service | 8085 | News, fundamentals |
| alert-service | 8087 | Price alerts |
| ai-service | 8089 | AI analysis |
| Angular (dev) | 4200 | `ng serve` |
| Angular (Docker/K8s) | 4200 / 80 | nginx container |
| Postgres | 5432 | DBs: `mtp_identity`, `mtp_market`, `mtp_trading`, `mtp_alert` |
| Redis | 6379 | L2 cache |
| Kafka | 9092 | Events (host; container still uses 9092/29092) |

**Clients never call microservices directly** — only the Gateway (`http://localhost:8080`).

### OpenAPI / Swagger UI

Each REST microservice exposes **springdoc** Swagger UI and OpenAPI JSON (Bearer JWT authorized in the UI). Use these for exploring and trying endpoints during local development.

| Service | Swagger UI | OpenAPI JSON |
|---------|------------|--------------|
| identity-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| market-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| trading-service | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |
| reference-data-service | http://localhost:8085/swagger-ui.html | http://localhost:8085/v3/api-docs |
| alert-service | http://localhost:8087/swagger-ui.html | http://localhost:8087/v3/api-docs |
| ai-service | http://localhost:8089/swagger-ui.html | http://localhost:8089/v3/api-docs |

**Tips**
- In Swagger UI, click **Authorize** and paste a JWT from `POST /api/auth/login` (e.g. via Gateway or identity Swagger).
- Prefer the **Via Gateway** server entry in each spec when testing the same paths the SPA uses (`http://localhost:8080`).
- `notification-service` has no REST API (Kafka only) — no Swagger UI.
- Controllers are annotated with `@Tag` / `@Operation`; specs stay in sync with the code.

---

## 1. Local development (hybrid: Docker infra + Maven/Node)

Best for day-to-day coding: infrastructure in Docker, services in your IDE or Maven.

### Step 1 — Build once

```powershell
.\scripts\build-all.ps1
```

### Step 2 — Start infrastructure

```powershell
.\scripts\local-infra-up.ps1
```

Postgres init script (`infra/postgres/init-databases.sql`) creates the four service databases automatically.

### Step 3 — Start backend

**Option A — one script (opens a window per service on Windows):**

```powershell
.\scripts\local-backend-start.ps1
```

**Option B — manual order (IDE or terminals):**

1. `infra/discovery-service` → `:8761`
2. `infra/config-service` → `:8888`
3. `infra/gateway-service` → `:8080`
4. All `services/*` (any order after gateway is up)

Verify: [http://localhost:8761](http://localhost:8761) lists registered services.

### Step 4 — Frontend

```powershell
.\scripts\local-frontend-start.ps1
```

Open [http://localhost:4200](http://localhost:4200). Dev config uses Gateway at `http://localhost:8080`.

Local login (seeded on first `identity-service` start): username **`admin`**, password **`admin1234`**.

### Step 5 — Desktop (JavaFX thin client)

Requires Gateway + backend running:

```powershell
.\scripts\local-desktop-start.ps1
```

Desktop reads `GATEWAY_BASE_URL` (default `http://localhost:8080`) from `.env` or `desktop/src/main/resources/application.properties`.

**All-in-one shortcut:**

```powershell
.\scripts\local-full-start.ps1    # infra + backend windows
.\scripts\local-frontend-start.ps1   # separate terminal
```

### Stop

```powershell
.\scripts\local-backend-stop.ps1
.\scripts\local-infra-down.ps1
```

---

## 2. Docker Compose (full stack)

Everything — infra, all services, frontend — in containers.

```powershell
.\scripts\docker-up.ps1
```

| URL | Purpose |
|-----|---------|
| http://localhost:4200 | Angular (nginx) |
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka dashboard |
| http://localhost:5050 | pgAdmin (optional) |

Frontend gateway URL is injected at container start via `GATEWAY_BASE_URL` (see `frontend/docker-entrypoint.sh`).

**Desktop with Docker backend:** set `GATEWAY_BASE_URL=http://localhost:8080` and run `local-desktop-start` on the host (desktop is not containerized).

```powershell
.\scripts\docker-down.ps1
```

---

## 3. Kubernetes

Targets a cluster with **nginx Ingress** (e.g. Docker Desktop Kubernetes, kind, minikube).

### Prerequisites

- `kubectl` configured for your cluster
- nginx Ingress Controller installed
- For local clusters: **kind** (script auto-loads `mtp/*:latest` images)

### Deploy

```powershell
.\scripts\k8s-deploy.ps1
```

This builds images tagged `mtp/<service>:latest`, applies manifests under `k8s/`, and creates namespace `mtp`.

### Hosts entry

```
127.0.0.1 mtp.local
```

### Access

| URL | Routes to |
|-----|-----------|
| http://mtp.local/ | frontend |
| http://mtp.local/api/ | gateway-service |

Check status:

```powershell
kubectl get pods -n mtp
kubectl logs -n mtp deploy/gateway-service -f
```

Edit secrets before production: `k8s/secrets/mtp-secrets.yaml` (or use sealed secrets / external secret manager).

### Teardown

```powershell
.\scripts\k8s-delete.ps1
```

---

## Component-specific notes

### Backend

- Config: bootstrap `application.yml` in each module + `infra/config-repo/<service>.yml` via config-server.
- Flyway owns schema for `identity`, `market`, `trading`, `alert` services.
- `notification-service` needs `eureka.client.jersey.enabled=false` (Telegram lib brings Jersey on the classpath).

### Frontend

- **Local:** `npm install && npm start` in `frontend/`.
- **Docker/K8s:** runtime `assets/config.json` sets `gatewayBaseUrl` without rebuilding the image.

### Desktop

- Standalone Maven project under `webapp/desktop/` (Java 21, JavaFX 21).
- Thin client: all business logic via Gateway HTTP/WebClient and STOMP.
- Legacy local Postgres mode: `desktop/scripts/run-with-postgres.ps1` (monolith transition only).

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Port already in use | `local-backend-stop.ps1` or kill the process on that port |
| Flyway / schema errors | Ensure Postgres is up; delete DB volume and re-run infra if needed |
| Eureka registration fails | Start discovery → config → gateway before business services |
| Frontend CORS errors | Restart **gateway-service** after CORS changes; origins default to `http://localhost:4200` (`GATEWAY_CORS_ORIGINS`) |
| Docker build slow | First `docker-up` builds all images; subsequent runs use cache |
| K8s ImagePullBackOff | Run `k8s-deploy` on same machine as cluster, or push images to a registry and update `k8s/*/deployment.yaml` image names |

---

## Related docs

- [`MTP_Build_Deploy_Runbook.md`](MTP_Build_Deploy_Runbook.md) — migration issues and build/deploy history
- [`scripts/README.md`](../scripts/README.md) — script index
