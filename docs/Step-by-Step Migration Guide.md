# Step-by-Step Migration Guide

This guide is designed for AI agents to migrate the `matthew_trading_platform` from a monolithic structure to a scalable microservices architecture.

---

### Step 0: Project Restructuring & Infrastructure Setup
**Goal**: Initialize the microservices workspace and core infrastructure.

**Prompt to Agent**:
> "I want to migrate my monolithic project to a microservices architecture. Please create the following top-level structure:
> 
> 1. `/infra/`: For infrastructure services (Discovery, Gateway).
> 2. `/services/`: For backend microservices (Identity, Market, Trading, Notification).
> 3. `/frontend/`: For the Angular 17 web application.
> 4. `/desktop/`: Preserve the existing JavaFX code here.
> 
> Implement the **Spring Cloud Netflix Eureka** Discovery Service in `/infra/discovery-service` and **Spring Cloud Gateway** in `/infra/gateway-service`. Ensure all services use Java 21 and Spring Boot 3.x. Create a root `pom.xml` to manage shared dependencies and versions."

---

### Step 1: Identity Service & OAuth2 Security
**Goal**: Implement centralized security with multiple login providers.

**Prompt to Agent**:
> "Create an `identity-service` in `/services/identity-service`. It must handle:
> 
> 1. **Spring Security OAuth2**: Support login via Google, TradingView, and standard username/password.
> 2. **JWT Implementation**: Issue stateless JWT tokens upon successful login.
> 3. **Role Management**: Use existing `AppUser` and `RolePermission` entities.
> 4. **API Gateway Integration**: Configure the Gateway to validate JWTs for all downstream services.
> 
> Provide the `SecurityConfig`, `JwtUtil`, and `AuthController` code."

---

### Step 2: Market Data Abstraction & Broker Integration
**Goal**: Build a unified market data service supporting multiple brokers.

**Prompt to Agent**:
> "Create a `market-service` in `/services/market-service`. 
> 
> 1. **Abstraction Layer**: Define a generic `MarketDataService` interface for fetching OHLCV, live prices, and ticker data.
> 2. **BitUnix Implementation**: Implement the service for BitUnix using their official API documentation.
> 3. **Live Charting**: Implement a WebSocket controller to stream real-time OHLCV data to the frontend.
> 4. **Caching**: Integrate **Caffeine Cache** for high-performance storage of recent market data.
> 
> Ensure the service is scalable to add more brokers later."

---

### Step 3: Trading Microservice (Spot & Futures)
**Goal**: Handle trade execution with thread safety and transactional integrity.

**Prompt to Agent**:
> "Create a `trading-service` in `/services/trading-service`. 
> 
> 1. **Unified Trading API**: Implement services for Spot and Futures trading.
> 2. **Thread Safety**: Ensure the order execution engine is thread-safe for high-concurrency trading.
> 3. **Transactional Integrity**: Use `@Transactional` for all database operations (Trade, Portfolio, Balance).
> 4. **Broker Connectivity**: Connect to the BitUnix trading API for order placement.
> 
> Focus on clean abstraction for different trade types and brokers."

---

### Step 4: Messaging & Notifications (Kafka)
**Goal**: Implement asynchronous notifications across multiple channels.

**Prompt to Agent**:
> "Implement a `notification-service` using **Apache Kafka**.
> 
> 1. **Kafka Producers**: Add producers in `trading-service` and `market-service` for trade events and price alerts.
> 2. **Notification Channels**: The service should consume Kafka events and send:
>    - Push Notifications (Web/Mobile)
>    - Emails (SMTP)
>    - Telegram Messages (via Bot API)
> 3. **Logging**: Implement centralized structured logging for all notification events."

---

### Step 5: Angular 17 Frontend - Trading Dashboard
**Goal**: Build the modern web UI with a live trading tab.

**Prompt to Agent**:
> "I'm building an Angular 17 frontend in `/frontend/`. 
> 
> 1. **Standalone Components**: Use standalone architecture for all modules.
> 2. **Live Trading Tab**: Create a component that displays a real-time candlestick chart using **ECharts**.
> 3. **Broker Integration**: Implement a UI for selecting brokers (BitUnix, etc.) and viewing specific OHLCV data.
> 4. **Real-time Data**: Use RxJS to handle WebSocket streams from the `market-service`.
> 
> Use Angular Material for the UI and SCSS for professional theming."

---

### Step 6: JavaFX Desktop Client Refactoring
**Goal**: Convert the monolithic app into a thin client for the microservices.

**Prompt to Agent**:
> "Refactor the existing JavaFX code in `/desktop/`.
> 
> 1. **API Client**: Replace all local database and service calls with REST calls to the API Gateway.
> 2. **WebSocket Client**: Implement a STOMP client to receive real-time price updates.
> 3. **Security**: Update the login screen to support the new OAuth2 flow from the `identity-service`.
> 4. **Multi-threading**: Ensure the UI remains responsive during network operations.
> 
> Maintain the existing UI look and feel while offloading all logic to the backend."

---

### Step 7: DevOps - Docker & Kubernetes
**Goal**: Containerize and deploy the entire microservices stack.

**Prompt to Agent**:
> "Prepare the project for production deployment.
> 
> 1. **Dockerization**: Create a `Dockerfile` for each service and the frontend.
> 2. **Docker Compose**: Create a `docker-compose.yml` to run the full stack (Discovery, Gateway, Kafka, Postgres, and all services).
> 3. **Kubernetes**: Generate K8s deployment manifests, services, and ingress rules for a scalable cluster.
> 4. **Logging & Monitoring**: Include configuration for centralized logging and health checks via Spring Boot Actuator."

---

### Step 8: Final Upgrades & Optimization
**Goal**: Ensure the project uses the latest stable technologies.

**Prompt to Agent**:
> "Perform a final optimization pass on the project.
> 
> 1. **Version Upgrade**: Update to the latest stable versions of Java (target Java 25) and Spring Boot (target 4.1).
> 2. **Code Quality**: Check for thread safety in all critical paths and optimize transactional boundaries.
> 3. **Token Efficiency**: Ensure all code follows the patterns defined in `.cursorrules` for minimal token usage.
> 
> Provide a final build report and deployment guide."

---

### General Rules for Token Efficiency

| Rule | Why |
| :--- | :--- |
| **One task per prompt** | Prevents the agent from generating redundant or hallucinated code. |
| **Reference context** | Use "Follow the pattern in `XService.java`" to save tokens. |
| **Ask for code only** | Skip long explanations unless specifically requested. |
| **Use "DO NOT" constraints** | Prevents unwanted changes to working logic. |
| **Set up `.cursorrules`** | Provides persistent instructions for every interaction. |

---

### Example `.cursorrules` for this Project

```text
You are an expert Java/Spring Cloud/Angular developer.

General Rules:
- Use Java 21+, Spring Boot 3.x, Angular 17.
- Microservices: Use Eureka for discovery and Spring Cloud Gateway.
- Security: OAuth2 with JWT (stateless).
- Database: JPA/Hibernate with PostgreSQL.
- Messaging: Apache Kafka for notifications.
- Caching: Caffeine Cache for performance.
- Concurrency: Ensure thread safety in trading logic.
- Transactions: Use @Transactional for atomic operations.
- UI: Angular Material + ECharts for the web; JavaFX for desktop.
```
