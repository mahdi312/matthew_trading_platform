# Code Migration Plan: From Monolithic src/ to Microservices

## Overview
This document maps existing code in `src/` to appropriate microservices, eliminating duplication and establishing clear separation of concerns.

## Current Structure Analysis
- **src/main/java**: 289 Java files (monolithic application)
- **desktop/**: 289 duplicate Java files (removed)
- **Goal**: Distribute logic across microservices, keep desktop-client lightweight

## Migration Mapping

### 1. Identity Service (Port 9898)
**Responsibility**: Authentication, Authorization, User Management

**Code to Move**:
- `controller/LoginController.java` → REST endpoints
- `controller/RegisterController.java` → REST endpoints
- `controller/ProfileSettingsController.java` → User profile endpoints
- `controller/AdminUserManagementController.java` → Admin endpoints
- `service/UserService.java` → Core business logic
- `service/AuthenticationService.java` → Auth logic
- `model/AppUser.java` → User entity
- `repository/UserRepository.java` → User data access

**New Endpoints**:
```
POST   /api/auth/login
POST   /api/auth/register
POST   /api/auth/logout
GET    /api/users/{id}
PUT    /api/users/{id}
GET    /api/users/{id}/profile
POST   /api/admin/users
```

---

### 2. Market Service (Port 9001)
**Responsibility**: Market Data, Price Information, Technical Indicators

**Code to Move**:
- `controller/ChartController.java` → Chart data endpoints
- `service/MarketDataService.java` → Market data aggregation
- `service/PriceService.java` → Price calculations
- `service/IndicatorService.java` → Technical indicators
- `service/AlphaVantageService.java` → External API integration
- `model/CandleData.java` → OHLCV data models
- `model/TechnicalIndicator.java` → Indicator models
- `repository/MarketDataRepository.java` → Market data persistence

**New Endpoints**:
```
GET    /api/market/data/{symbol}
GET    /api/market/price/{symbol}
GET    /api/market/chart/{symbol}?period=1h
GET    /api/market/indicators/{symbol}
GET    /api/market/ohlcv/{symbol}
```

---

### 3. Trading Service (Port 9002)
**Responsibility**: Order Execution, Trade Management, Position Tracking

**Code to Move**:
- `controller/TradeEntryController.java` → Order endpoints
- `service/TradeService.java` → Trade execution
- `service/OrderService.java` → Order management
- `service/PositionService.java` → Position tracking
- `service/RiskManagementService.java` → Risk calculations
- `model/Trade.java` → Trade entity
- `model/Order.java` → Order entity
- `model/Position.java` → Position entity
- `repository/TradeRepository.java` → Trade data access
- `repository/OrderRepository.java` → Order data access

**New Endpoints**:
```
POST   /api/orders
GET    /api/orders/{id}
PUT    /api/orders/{id}
DELETE /api/orders/{id}
GET    /api/positions
GET    /api/trades
GET    /api/trades/{id}
GET    /api/pnl
```

---

### 4. Notification Service (Port 9003)
**Responsibility**: Alerts, Notifications, Event Handling

**Code to Move**:
- `controller/AlertManagerController.java` → Alert endpoints
- `service/AlertService.java` → Alert management
- `service/NotificationService.java` → Notification delivery
- `service/EmailService.java` → Email notifications
- `model/Alert.java` → Alert entity
- `repository/AlertRepository.java` → Alert persistence

**New Endpoints**:
```
POST   /api/alerts
GET    /api/alerts
PUT    /api/alerts/{id}
DELETE /api/alerts/{id}
POST   /api/notifications/email
POST   /api/notifications/telegram
```

---

### 5. Common Library (common-lib)
**Responsibility**: Shared Models, DTOs, Utilities

**Code to Move**:
- `model/` → DTOs and entities (shared)
- `util/` → Utility classes
- `exception/` → Custom exceptions
- `config/` → Common configuration

**Shared Classes**:
- `AppUser.java`
- `Trade.java`
- `Order.java`
- `Position.java`
- `Alert.java`
- `CandleData.java`
- `ApiResponse.java`
- `ErrorResponse.java`

---

### 6. Desktop Client (desktop-client)
**Responsibility**: UI Layer Only

**Code to Keep Lightweight**:
- `DesktopApplication.java` → Main entry point
- `service/ApiClientService.java` → REST API client
- `controller/` → JavaFX UI controllers (NO business logic)
- `config/` → Spring configuration

**Code to REMOVE**:
- All business logic services
- All repositories
- All complex calculations
- All external API integrations

---

## Migration Sequence

### Phase 1: Prepare Common Library
1. Create `common-lib` module
2. Move shared models and DTOs
3. Move utility classes
4. Move custom exceptions

### Phase 2: Refactor Identity Service
1. Move user-related code
2. Implement REST endpoints
3. Add OAuth2 support
4. Add JWT token generation

### Phase 3: Refactor Market Service
1. Move market data code
2. Implement chart endpoints
3. Add caching layer
4. Add WebSocket support

### Phase 4: Refactor Trading Service
1. Move trade execution code
2. Implement order endpoints
3. Add transaction management
4. Add risk management

### Phase 5: Refactor Notification Service
1. Move alert code
2. Implement notification endpoints
3. Add Kafka consumer
4. Add multi-channel support

### Phase 6: Clean Desktop Client
1. Remove all business logic
2. Keep only UI components
3. Implement API client calls
4. Add error handling

---

## Code Removal Checklist

**From Desktop Client**:
- [ ] Remove all service classes
- [ ] Remove all repository classes
- [ ] Remove all business logic
- [ ] Remove database configuration
- [ ] Remove external API clients
- [ ] Keep only UI controllers and API client

**From src/ (after migration)**:
- [ ] Remove all code moved to microservices
- [ ] Keep only shared models in common-lib
- [ ] Remove duplicate code

---

## Benefits of This Refactoring

1. **Eliminated Duplication**: 289 duplicate files removed
2. **Clear Separation**: Each service has single responsibility
3. **Scalability**: Services can scale independently
4. **Maintainability**: Easier to understand and modify
5. **Testability**: Services can be tested in isolation
6. **Reusability**: Desktop and web clients share same APIs
7. **Lightweight Client**: Desktop app is just UI, no business logic

---

## Verification Checklist

After migration, verify:
- [ ] All microservices start successfully
- [ ] Desktop client connects to API gateway
- [ ] All REST endpoints respond correctly
- [ ] No code duplication between services
- [ ] Common library is properly shared
- [ ] Desktop client has no business logic
- [ ] All tests pass
- [ ] Documentation is updated

---

## Timeline Estimate

- Phase 1 (Common Lib): 1-2 hours
- Phase 2 (Identity): 2-3 hours
- Phase 3 (Market): 3-4 hours
- Phase 4 (Trading): 3-4 hours
- Phase 5 (Notification): 1-2 hours
- Phase 6 (Desktop Cleanup): 1-2 hours
- **Total**: 11-17 hours

---

## Next Steps

1. Create common-lib module
2. Start with Phase 1: Move shared models
3. Proceed sequentially through phases
4. Test each phase before moving to next
5. Update documentation as you go
