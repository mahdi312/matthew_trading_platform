# Matthew Trading Platform: Microservices Migration Guide

This document outlines the phased migration of the `matthew_trading_platform` from a monolithic JavaFX/Spring application to a modern, scalable microservices architecture.

---

## 🏗️ Architecture Overview

- **Backend**: Spring Boot 3.x Microservices
- **Frontend**: Angular 17 (Responsive Web)
- **Desktop**: JavaFX Client (Win/Mac/Linux)
- **Infrastructure**: Spring Cloud (Gateway, Eureka), Kafka, Docker, Kubernetes
- **Security**: Spring Security OAuth2 (Google, TradingView, Brokers)
- **Performance**: Caffeine Cache, Multi-threading, Transactional Integrity

---

## 🛠️ Step-by-Step Migration Plan

### Step 0: Project Restructuring & Infrastructure
**Goal**: Set up the multi-module project structure and core infrastructure.

1.  **Restructure Root**: Create directories for `/services`, `/frontend`, `/desktop`, and `/infra`.
2.  **Service Discovery**: Implement `discovery-service` using Spring Cloud Netflix Eureka.
3.  **API Gateway**: Implement `gateway-service` using Spring Cloud Gateway to handle routing and rate limiting.
4.  **Shared Library**: Create a `common-lib` for shared DTOs, exceptions, and utility classes to reduce code duplication.

> **Agent Prompt**: "Initialize a Spring Cloud microservices project structure. Create a Eureka Discovery Server and a Spring Cloud Gateway. Set up a root `pom.xml` managing dependencies for all services."

### Step 1: Centralized Security (OAuth2 + JWT)
**Goal**: Implement a unified security service handling multiple login providers.

1.  **Auth Service**: Create an `identity-service` handling JWT generation and validation.
2.  **OAuth2 Integration**: Configure Spring Security to support Google, TradingView, and Broker OAuth2 logins.
3.  **Simple Security**: Maintain standard username/password login as a fallback.
4.  **Global Filter**: Implement a security filter in the Gateway to validate JWTs before routing.

> **Agent Prompt**: "Implement a Spring Boot Identity Service with OAuth2 support. Integrate Google and TradingView login. Use JWT for stateless authentication and provide a public key endpoint for other services to validate tokens."

### Step 2: Market Data Abstraction Layer
**Goal**: Create a unified interface for multiple market data providers.

1.  **Abstraction Layer**: Define `MarketDataService` interface with methods for `getOHLCV`, `getLivePrice`, and `getTickerInfo`.
2.  **Provider Implementations**: Implement services for specific brokers/providers (e.g., BitUnix, TwelveData) following the abstraction.
3.  **Live Charting Service**: Implement a WebSocket-based service in `market-service` to stream OHLCV data to clients.

> **Agent Prompt**: "Create a Market Data Microservice. Define a generic `OHLCVProvider` interface. Implement a concrete version for BitUnix using their REST/WebSocket API. Ensure the service can handle multiple providers concurrently."

### Step 3: Trading Microservice & Broker Integration
**Goal**: Handle order execution, portfolio management, and broker-specific logic.

1.  **Unified Trade API**: Create an abstraction for Spot and Futures trading across different brokers.
2.  **Thread Safety**: Ensure order execution logic is thread-safe using concurrent collections and proper synchronization.
3.  **Transactional Integrity**: Use `@Transactional` for all trade-related database operations to prevent data inconsistency.
4.  **Live Trading Tab**: Design endpoints specifically for the "Live Trading" UI component.

> **Agent Prompt**: "Build a Trading Microservice. Implement a thread-safe order execution engine. Add support for Spot and Futures trading. Use `@Transactional` to ensure atomicity for trade records and balance updates."

### Step 4: Messaging & Notifications (Kafka)
**Goal**: Implement an asynchronous notification system.

1.  **Kafka Setup**: Configure Kafka clusters for inter-service communication.
2.  **Notification Service**: Create a `notification-service` that consumes events from Kafka.
3.  **Multi-Channel**: Implement handlers for Push Notifications, Emails (SMTP), and Telegram Bot messages.

> **Agent Prompt**: "Implement a Kafka-based Notification Service. Create producers in the Trading and Market services. The Notification service should send Emails and Telegram messages based on received events."

### Step 5: Cross-Cutting Concerns (Logging & Caching)
**Goal**: Ensure high performance and observability.

1.  **Centralized Logging**: Implement Logback/ELK stack configuration for unified log collection.
2.  **Caffeine Cache**: Integrate Caffeine for high-performance local caching of market data and user settings.
3.  **Monitoring**: Add Spring Boot Actuator to all services for health checks and metrics.

> **Agent Prompt**: "Configure Caffeine Cache for the Market Service to store recent OHLCV data. Set up a global exception handler and structured JSON logging for all microservices."

### Step 6: Frontend - Angular 17 Migration
**Goal**: Build a modern, responsive web dashboard.

1.  **Module Architecture**: Create standalone components for Dashboard, Trading, and Portfolio.
2.  **Chart Integration**: Use ECharts or a similar library to implement the Live Charting tab.
3.  **State Management**: Use RxJS for managing real-time data streams from WebSockets.

> **Agent Prompt**: "Initialize an Angular 17 project with standalone components. Create a 'Trading View' component that connects to the Market Service WebSocket and displays a real-time candlestick chart."

### Step 7: Desktop Client - JavaFX Refactoring
**Goal**: Transform the monolithic JavaFX app into a microservices client.

1.  **API Client**: Replace local service calls with HTTP/REST calls to the API Gateway.
2.  **WebSocket Client**: Implement a STOMP/WebSocket client for real-time updates.
3.  **Packaging**: Use JPackage to create native installers for Win/Mac/Linux.

> **Agent Prompt**: "Refactor the existing JavaFX application to remove direct database access. Implement a REST client to fetch data from the API Gateway and a WebSocket client for live price updates."

### Step 8: DevOps - Docker & Kubernetes
**Goal**: Containerize and deploy the application.

1.  **Dockerization**: Write optimized Dockerfiles for each microservice and the frontend.
2.  **Kubernetes Manifests**: Create Deployments, Services, and Ingress resources for K8s.
3.  **CI/CD**: Set up GitHub Actions for automated building and deployment.

> **Agent Prompt**: "Create a `docker-compose.yml` to run the entire stack (Eureka, Gateway, Identity, Market, Trading, Kafka, Postgres). Then, generate Kubernetes deployment YAMLs for each service."

---

## 📈 General Rules for Token Efficiency

| Rule | Description |
| :--- | :--- |
| **Atomic Tasks** | One task per prompt to prevent redundant code generation. |
| **Reference Context** | Always say "Use the pattern in `X.java`" to avoid re-explaining logic. |
| **No Explanations** | Use "Provide code only" to save output tokens. |
| **Strict Constraints** | Use "DO NOT change logic" to prevent unwanted refactoring. |
| **Shared Models** | Use the `common-lib` to avoid redefining DTOs in every prompt. |

---

## 📋 Suggested Execution Order

1.  **Infrastructure (P0)**: Eureka, Gateway, Common-Lib.
2.  **Security (P0)**: Identity Service + OAuth2.
3.  **Domain Core (P1)**: Market Data Service + Trading Service.
4.  **Messaging (P1)**: Kafka + Notification Service.
5.  **Frontend/Client (P1)**: Angular Dashboard + JavaFX Refactoring.
6.  **DevOps (P2)**: Docker & K8s deployment.
