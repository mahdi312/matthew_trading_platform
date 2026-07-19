# MTP run scripts

One-click helpers for local, Docker, and Kubernetes environments.

**Java:** microservices require **JDK 25+**. `build-all` and `local-backend-start` auto-detect `JAVA_HOME` (`MTP_JAVA_HOME`, existing `JAVA_HOME` if ≥25, or `D:\java\jdk-25` / Temurin paths). Desktop uses **JDK 21**. Service windows launch with `pwsh -NoProfile` (falls back to `powershell`) so a broken profile (e.g. missing `DockerCompletion`) cannot break startups.

**Ports (Windows):** Postgres host **5432**, Kafka host **9092** (avoids Hyper-V reserved ranges). Compose-internal ports are unchanged.

**Run from repo root:**

```powershell
# Windows
.\scripts\local-full-start.ps1
.\scripts\local-frontend-start.ps1
```

```bash
# macOS / Linux / Git Bash
chmod +x scripts/*.sh scripts/_lib/common.sh
./scripts/local-full-start.sh
./scripts/local-frontend-start.sh
```

See [`docs/RUN_GUIDE.md`](../docs/RUN_GUIDE.md) for full instructions.

## Script index

| Script | Description |
|--------|-------------|
| `build-all` | `mvn clean install -DskipTests` (auto JDK 25+, fails on Maven errors) |
| `local-infra-up` | Postgres, Redis, ZooKeeper, Kafka (Compose) |
| `local-infra-down` | Stop infra containers |
| `local-backend-start` | All Spring Boot services (windows on Windows, background on bash) |
| `local-backend-stop` | Stop services by port / PID |
| `local-frontend-start` | `npm start` in `frontend/` |
| `local-desktop-start` | `mvn javafx:run` in `desktop/` |
| `local-full-start` | infra + backend |
| `docker-up` | Full Compose stack (`--build`) |
| `docker-down` | `docker compose down` |
| `k8s-deploy` | Build `mtp/*` images + `kubectl apply` |
| `k8s-delete` | Delete `mtp` namespace |

Logs from bash background services: `scripts/logs/<service>.log`
