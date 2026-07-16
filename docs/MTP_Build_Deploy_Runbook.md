# TradingPlatformApp V2.0.8 — Build → Deploy Runbook

Verified directly against your uploaded source tree (not assumed). Three parts:
1. Compile → deploy instructions (local, Docker, Kubernetes)
2. Database + Angular instructions per environment
3. Real issues found in this snapshot, with ready-to-paste agent prompts

---

## Part 1 — Compile to Deploy

### Architecture recap (from root pom + docker-compose + .cursorrules)
- **11 Maven modules**: `infra/discovery-service` (Eureka), `infra/gateway-service` (Spring Cloud Gateway, JWT-validating), `infra/config-service` (Spring Cloud Config, native profile), `shared/contracts`, and 7 business services (`identity`, `market`, `trading`, `notification`, `reference-data`, `ai`, `alert`).
- **Infra**: PostgreSQL (one DB per service), Redis (L2 cache for market + reference-data), Kafka+ZooKeeper (cross-service events).
- **Frontend**: Angular 17 standalone app, talks only to the Gateway (`http://localhost:8080` in dev), never directly to a microservice.
- **Desktop**: separate JavaFX module (`webapp/desktop`), thin client only.

### Step 0 — Prerequisites
```bash
java -version      # Java 25 required (root pom targets Spring Boot 4.1 / Java 25)
mvn -version       # Maven 3.9+
node -version      # Node 18+ for Angular 17
docker --version
docker compose version
kubectl version --client   # only needed for the k8s stage
```

### Step 1 — Build all backend modules
From `webapp/` (the multi-module root):
```bash
cd webapp
mvn clean install -DskipTests
```
This builds `shared/contracts` first (everything depends on its DTOs), then each service. Run with `-DskipTests` the first time to isolate compile issues from test issues.

### Step 2 — Start infrastructure only (fastest local loop)
Before touching Docker Compose for the whole stack, you can run just the infra pieces and everything else from your IDE — this is explicitly documented in your own `docker-compose.yml` header comments:
```bash
docker run -d -p 6379:6379 --name mtp-redis redis:7-alpine
docker compose up -d zookeeper kafka
docker run -d -p 5432:5432 \
  -e POSTGRES_USER=mtp -e POSTGRES_PASSWORD=mtp_secret -e POSTGRES_DB=mtp \
  --name mtp-postgres postgres:16-alpine
```

### Step 3 — Create per-service databases
Your compose file provisions one Postgres instance with a single `mtp` database, but `SPRING_DATASOURCE_URL` for each service points at **separate database names** (`mtp_market`, `mtp_trading`, `mtp_alert`, `mtp_identity`). You must create these manually before first run:
```bash
docker exec -it mtp-postgres psql -U mtp -d mtp -c "CREATE DATABASE mtp_identity;"
docker exec -it mtp-postgres psql -U mtp -d mtp -c "CREATE DATABASE mtp_market;"
docker exec -it mtp-postgres psql -U mtp -d mtp -c "CREATE DATABASE mtp_trading;"
docker exec -it mtp-postgres psql -U mtp -d mtp -c "CREATE DATABASE mtp_alert;"
```
(See **Issue #2** below — this alone won't be enough to get these services running yet.)

### Step 4 — Run backend services locally (in order)
Eureka and Config must be up before anything else registers:
```bash
cd infra/discovery-service && mvn spring-boot:run &     # port 8761
cd infra/config-service    && mvn spring-boot:run &     # port 8888
cd infra/gateway-service   && mvn spring-boot:run &     # port 8080
cd services/identity-service && mvn spring-boot:run &   # port 8081
cd services/market-service    && mvn spring-boot:run &  # port 8082
cd services/trading-service  && mvn spring-boot:run &   # port 8083
cd services/notification-service && mvn spring-boot:run & # port 8084
cd services/reference-data-service && mvn spring-boot:run & # port 8085
cd services/ai-service       && mvn spring-boot:run &   # port 8086
cd services/alert-service    && mvn spring-boot:run &   # port 8087
```
Verify registration: `http://localhost:8761` should list all 7 services + gateway.

### Step 5 — Run Angular locally
```bash
cd frontend
npm install
npm start          # ng serve, defaults to http://localhost:4200
```
`src/environments/environment.ts` (dev) already points `gatewayBaseUrl` at `http://localhost:8080` — no proxy config file exists in this project, so the app relies on CORS being permitted at the Gateway rather than a `proxy.conf.json`. Confirm `GatewaySecurityConfig` / CORS config allows `http://localhost:4200` as an origin before assuming this works out of the box.

### Step 6 — Full stack via Docker Compose
```bash
cd webapp
cp .env.example .env    # see Issue #3 — this file doesn't exist yet, you'll need to create it
docker compose up -d --build
docker compose ps        # confirm all healthy
docker compose logs -f gateway-service
```
**This will not build successfully yet** — see **Issue #1**, every per-service `Dockerfile` referenced in `docker-compose.yml` is missing from the archive.

### Step 7 — Kubernetes
There are currently **no Kubernetes manifests anywhere in this project** (see Issue #4). Once Dockerfiles exist and images are pushed to a registry, the minimum manifest set per service is:
```
k8s/
  postgres/ (StatefulSet + PVC + Service)
  redis/ (Deployment + Service)
  kafka/ + zookeeper/ (StatefulSet + Service, or use Strimzi operator)
  configmaps/ (one per service, replacing docker-compose environment: blocks)
  secrets/ (DB creds, JWT secret, broker API keys — from .env today)
  discovery-service/, gateway-service/, <each business service>/ (Deployment + Service + optional HPA)
  ingress.yaml (routes external traffic to gateway-service only)
```
Given the scope, I'd treat this as its own follow-up task once Docker images build cleanly — trying to write manifests against Dockerfiles that don't exist yet will just mean redoing them.

---

## Part 2 — Database, Angular, Docker, Kubernetes specifics

| Concern | Local (IDE/CLI) | Docker Compose | Kubernetes |
|---|---|---|---|
| Postgres | `docker run` single container, manually create per-service DBs (Step 3) | `postgres` service in compose, same manual DB creation still needed post-`up` | StatefulSet + PVC, DB creation via init job or migration tool |
| Redis | `docker run redis:7-alpine` | `redis` service, AOF persistence configured | Deployment + PVC if persistence needed |
| Kafka | `docker compose up -d zookeeper kafka` | both services, topics auto-created via `KAFKA_CREATE_TOPICS` | StatefulSet or Strimzi `KafkaTopic` CRDs |
| Angular | `ng serve` on 4200, calls Gateway on 8080 directly | Not currently containerized — no frontend service in `docker-compose.yml` (see Issue #5) | Needs its own Deployment (nginx serving the `ng build` output) + Ingress path |
| Config | Each service reads its own `application.yml` directly; Config Server largely unused so far | same | ConfigMaps should eventually replace per-service `application.yml` env blocks |
| Secrets | `.env` file (missing — Issue #3) | `.env` referenced by compose via `${VAR}` syntax | Kubernetes `Secret` objects, mounted as env vars |

---

## Part 3 — Issues found in this snapshot, with agent prompts

I verified each of these directly against the archive contents — these aren't guesses.

### Issue #1 — Every service Dockerfile referenced in `docker-compose.yml` is missing
`docker-compose.yml` references `services/*/Dockerfile` and `infra/*/Dockerfile` for all 10 services/infra modules. **None of these files exist in the project** (only `desktop/Dockerfile` exists, for the unrelated JavaFX client). `docker compose up --build` will fail immediately with "Dockerfile not found" for every service.

**Prompt to give your coding agent:**
> "Create a multi-stage Dockerfile for each Spring Boot module under `services/*` and `infra/discovery-service`, `infra/gateway-service`, `infra/config-service`. Use a Maven/Eclipse Temurin Java 25 builder stage that runs `mvn -pl <module-path> -am package -DskipTests`, then copy the resulting jar into a slim `eclipse-temurin:25-jre-alpine` runtime stage. Match the working directory and `context: .` convention already used in the root `docker-compose.yml` — each Dockerfile is invoked with build context `.` (the `webapp/` root), so COPY paths must be relative to `webapp/`, not to the service's own folder. Expose the port each service already declares in its `application.yml` (e.g. market-service: 8082)."

### Issue #2 — Flyway is configured but has zero migration files; 4 services cannot start
`identity-service`, `market-service`, `trading-service`, and `alert-service` all declare a Flyway dependency in `pom.xml` and set `ddl-auto: validate` with the comment *"Flyway owns the schema now — Hibernate only checks it matches."* But **there are no `src/main/resources/db/migration/*.sql` files anywhere in the project** — every service's `resources/` folder contains only `application.yml`. On startup, Hibernate's schema validation will fail against an empty database because Flyway has no migrations to run.

**Prompt to give your coding agent:**
> "For each of identity-service, market-service, trading-service, and alert-service, inspect the JPA `@Entity` classes and generate a corresponding Flyway baseline migration at `src/main/resources/db/migration/V1__init_schema.sql`, creating tables, columns, constraints, and indexes that match the entity mappings exactly (including `@Column`, `@JoinColumn`, `@OneToMany`/`@ManyToOne` foreign keys, and any `@Table` name overrides). Do not use `ddl-auto: update` as a workaround — the codebase has explicitly committed to Flyway-owned schema."

### Issue #3 — `docker-compose.yml` depends on a `.env` file that doesn't exist
Compose uses `${POSTGRES_USER}`, `${ALPHAVANTAGE_KEY}`, `${BITUNIX_API_KEY}`, `${SMTP_HOST}`, `${OPENAI_API_KEY}`, and a dozen other variables — all with fallback defaults, so `docker compose up` won't crash, but real functionality (market data providers, broker trading, email/Telegram notifications, AI features) will silently no-op without real keys. There's no `.env.example` template anywhere to show which variables are expected.

**Prompt to give your coding agent:**
> "Scan `docker-compose.yml` for every `${VAR}` and `${VAR:-default}` reference and generate a `.env.example` file at the `webapp/` root listing each variable with a placeholder value or empty string, grouped by service with a comment header (Postgres, Kafka is not templated since it has no env vars, market-service providers, trading-service broker keys, notification-service SMTP/Telegram, ai-service). Add `.env` to `.gitignore` if it isn't already there."

### Issue #4 — No Kubernetes manifests exist despite being in the stated devops plan
`.cursorrules` explicitly states *"Docker Compose for local full-stack runs; Kubernetes manifests for deployment"* — there is no `k8s/` directory or any `*.yaml` manifest for Deployments/Services/ConfigMaps anywhere in the project.

**Prompt to give your coding agent:**
> "Once Dockerfiles exist and I've confirmed `docker compose up --build` works end-to-end, generate a `k8s/` directory with a Deployment + Service (+ ConfigMap for non-secret env vars, referencing a Secret for credentials) for each of: discovery-service, config-service, gateway-service, and all 7 business services. Also add StatefulSets for postgres, kafka, and zookeeper (or a Strimzi `Kafka`/`KafkaTopic` CRD set if I confirm the Strimzi operator is installed on the target cluster), a Deployment+Service for redis, and an Ingress routing all external traffic only to gateway-service, matching the 'no direct service calls' rule in .cursorrules."

### Issue #5 — Angular frontend isn't containerized or wired into Docker Compose at all
`docker-compose.yml` has no `frontend` service. The Angular app currently only runs via `ng serve` locally; there's no Dockerfile or nginx config to serve the built `dist/` output in Compose or Kubernetes.

**Prompt to give your coding agent:**
> "Add a multi-stage `frontend/Dockerfile`: a Node 18 builder stage running `npm ci && npm run build`, then an nginx:alpine runtime stage serving the built output with a minimal nginx config that supports Angular's client-side routing (fallback to `index.html`). Add a corresponding `frontend` service to `docker-compose.yml` exposing port 80→4200 (or your preferred host port), with `environment.gatewayBaseUrl` baked in or runtime-injected via an nginx-served `config.json` so the same image works across environments without a rebuild."

### Issue #6 — Config Server (`config-service`) is a placeholder with no actual configs
`infra/config-repo/README.md` states outright: *"This module is currently a scaffold placeholder only — no service configuration has been externalized yet."* Every service currently reads its full config from its own local `application.yml` rather than from the Config Server, so `config-service` isn't actually doing anything useful yet even though it's registered and running.

**Prompt to give your coding agent:**
> "Move the environment-specific portions of each service's `application.yml` (datasource URLs, Kafka bootstrap servers, Redis host, external API keys, JWT secret) into per-service files under `infra/config-repo/` (e.g. `market-service.yml`, `identity-service.yml`), following Spring Cloud Config's native-profile naming convention. Leave only bootstrap-level config (`spring.application.name`, `spring.cloud.config.uri`) in each service's own `application.yml`, so `config-service` becomes the real source of truth as originally planned in `.cursorrules`, rather than a registered-but-unused component."

### Minor / verify-yourself
- No `proxy.conf.json` in the Angular project — dev-mode cross-origin calls to the Gateway rely entirely on server-side CORS config being correct. Worth confirming `GatewaySecurityConfig` explicitly allows `http://localhost:4200`.
- A `docs/MTP_Microservices_Migration_Guide.md` is referenced by comments inside `environment.ts` and `.cursorrules`, but that file isn't present in this archive — it may just not have been included in this particular export; worth double-checking you have it on your side before relying on comments that point to it.

---

## Suggested order of attack
1. Issue #2 (Flyway migrations) — blocks 4 services from even starting locally.
2. Issue #1 (Dockerfiles) — blocks any Compose/K8s work.
3. Issue #3 (.env.example) — quick, unblocks a clean `docker compose up`.
4. Issue #5 (frontend Dockerfile) — needed before full-stack Compose is truly "full stack."
5. Issue #6 (Config Server) — architectural cleanup, not a blocker.
6. Issue #4 (Kubernetes) — do last, once Compose is proven working end-to-end.
