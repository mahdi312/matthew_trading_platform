# Matthew Trading Platform - Architecture Documentation

## System Architecture

### Microservices Design Pattern

The trading platform follows a **distributed microservices architecture** with the following principles:

1. **Service Independence**: Each service has its own database and can be deployed independently
2. **API Gateway Pattern**: All client requests route through a single gateway
3. **Service Discovery**: Eureka automatically registers and discovers services
4. **Event-Driven Communication**: Kafka enables asynchronous inter-service communication
5. **Resilience**: Circuit breakers and retry logic prevent cascading failures

### Core Services

#### 1. Identity Service (Port 9898)
**Responsibility**: User authentication and authorization

- Spring Security OAuth2 provider
- JWT token generation and validation
- Support for multiple OAuth providers (Google, TradingView, Brokers)
- User profile management
- Role-based access control (RBAC)

**Key Classes**:
- `SecurityConfig`: OAuth2 and JWT configuration
- `JwtUtil`: Token generation and validation
- `AuthController`: Authentication endpoints

#### 2. Market Service (Port 9001)
**Responsibility**: Real-time market data and price information

- Abstract market data provider interface
- Broker-specific implementations (BitUnix, Binance, Kraken)
- Caffeine cache for high-frequency data access
- WebSocket support for live price streaming
- Technical indicator calculations

**Key Components**:
- `MarketDataService`: Abstraction interface
- `BitUnixMarketDataProvider`: BitUnix implementation
- `PriceCache`: Caffeine-based caching layer
- `ChartDataController`: REST endpoints for chart data

#### 3. Trading Service (Port 9002)
**Responsibility**: Order execution and trade management

- Order placement (Market, Limit, Stop Loss)
- Position management (Spot & Futures)
- Trade history and reporting
- Risk management (position sizing, leverage)
- Thread-safe order execution engine

**Key Features**:
- `@EnableTransactionManagement` for ACID compliance
- Distributed locks for concurrent order handling
- Order validation and pre-flight checks
- Trade settlement and P&L calculation

#### 4. Notification Service (Port 9003)
**Responsibility**: Event-driven notifications

- Kafka consumer for trade events
- Multi-channel delivery (Email, Telegram, Push)
- Notification templates and scheduling
- Delivery retry logic
- Notification history

**Supported Channels**:
- Email (SMTP)
- Telegram Bot API
- Push notifications (Firebase)
- SMS (Twilio)

#### 5. API Gateway (Port 8080)
**Responsibility**: Request routing and cross-cutting concerns

- Route requests to appropriate services
- Rate limiting and throttling
- Request/response transformation
- Authentication enforcement
- API versioning support

#### 6. Discovery Service (Port 8761)
**Responsibility**: Service registry and discovery

- Eureka server for service registration
- Health check monitoring
- Service availability tracking
- Client-side load balancing

---

## Data Flow Diagrams

### User Login Flow
```
Client → Gateway → Identity Service → OAuth Provider
                        ↓
                   JWT Token Generated
                        ↓
                   Client Receives Token
```

### Trade Execution Flow
```
Client → Gateway → Trading Service → Broker API
                        ↓
                   Order Validation
                        ↓
                   Database Transaction
                        ↓
                   Kafka Event Published
                        ↓
                   Notification Service
                        ↓
                   User Notification
```

### Market Data Flow
```
Broker API → Market Service → Caffeine Cache
                  ↓
            WebSocket Stream
                  ↓
            Frontend Dashboard
```

---

## Technology Decisions

### Why Spring Cloud?
- **Service Discovery**: Eureka provides automatic service registration
- **API Gateway**: Spring Cloud Gateway handles routing and cross-cutting concerns
- **Resilience**: Built-in circuit breaker and retry patterns
- **Ecosystem**: Integrates seamlessly with Spring Boot

### Why Kafka?
- **Scalability**: Handles high-throughput event streaming
- **Reliability**: Persistent message queue with replication
- **Decoupling**: Services don't need to know about each other
- **Event Sourcing**: Enables audit trail and replay capabilities

### Why Caffeine Cache?
- **Performance**: In-memory caching for sub-millisecond access
- **Efficiency**: Automatic expiration and eviction policies
- **Thread-Safe**: Built-in concurrency support
- **Monitoring**: Detailed cache statistics and metrics

### Why PostgreSQL?
- **ACID Compliance**: Ensures data consistency for financial transactions
- **Scalability**: Handles high-volume trading data
- **Reliability**: Proven track record in production systems
- **JSON Support**: Native support for flexible data structures

---

## Deployment Architecture

### Docker Compose (Development)
All services run in containers with shared networking:
- Service-to-service communication via service names
- Volume mounting for development code changes
- Environment variable configuration

### Kubernetes (Production)
- Namespace isolation (`trading-platform`)
- Service discovery via DNS
- StatefulSets for databases
- Deployments for stateless services
- Persistent volumes for data

---

## Security Architecture

### Authentication Flow
1. User initiates OAuth2 login
2. Identity Service redirects to OAuth provider
3. Provider returns authorization code
4. Identity Service exchanges code for access token
5. JWT token generated and returned to client
6. Client includes JWT in subsequent requests

### Authorization
- Role-based access control (RBAC)
- Service-to-service authentication via JWT
- API Gateway enforces token validation
- Fine-grained permissions at service level

### Data Protection
- TLS/SSL for all network communication
- Encrypted password storage (bcrypt)
- Secrets management via environment variables
- Database connection pooling with SSL

---

## Performance Considerations

### Caching Strategy
- **L1 Cache**: Caffeine in-memory cache (Market Service)
- **L2 Cache**: Redis (optional, for distributed caching)
- **Cache Invalidation**: TTL-based expiration

### Database Optimization
- Connection pooling (HikariCP)
- Query optimization and indexing
- Batch operations for bulk inserts
- Read replicas for scaling

### API Rate Limiting
- Per-user rate limits via Gateway
- Burst allowance for legitimate spikes
- Exponential backoff for retries

---

## Monitoring & Observability

### Metrics
- Prometheus metrics exposed on `/actuator/prometheus`
- Service-level metrics (request latency, error rates)
- Business metrics (trades executed, P&L)

### Logging
- Centralized logging via ELK stack
- Structured logging with correlation IDs
- Log aggregation across services

### Tracing
- Distributed tracing via Jaeger
- Request flow visualization
- Performance bottleneck identification

---

## Scaling Strategy

### Horizontal Scaling
- Stateless services scale horizontally
- Load balancer distributes traffic
- Service discovery handles dynamic registration

### Vertical Scaling
- Database connection pool tuning
- JVM heap size optimization
- Cache size configuration

### Database Scaling
- Read replicas for query scaling
- Sharding for data partitioning
- Archive old data to separate storage

---

## Disaster Recovery

### Backup Strategy
- Daily database backups
- Kafka topic replication (3x)
- Configuration version control (Git)

### Recovery Procedures
1. Restore database from backup
2. Restart services in dependency order
3. Verify service health
4. Run data consistency checks

---

## Future Enhancements

1. **Circuit Breakers**: Add Resilience4j for fault tolerance
2. **Distributed Tracing**: Implement Jaeger for request tracing
3. **API Documentation**: Add Swagger/OpenAPI specifications
4. **Testing**: Comprehensive unit and integration tests
5. **CI/CD**: GitHub Actions for automated deployment
6. **Monitoring Dashboard**: Grafana for metrics visualization
7. **Cost Optimization**: Resource limits and autoscaling policies

