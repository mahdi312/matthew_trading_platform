# Common Library

## Overview

Shared models, DTOs, exceptions, and utilities used across all microservices and clients.

## Purpose

This library eliminates code duplication by providing:
- **Shared DTOs**: API request/response models
- **Shared Entities**: Domain models used by multiple services
- **Shared Exceptions**: Custom exceptions for error handling
- **Shared Utilities**: Common utility functions

## Structure

```
common-lib/
├── src/main/java/com/matthew/common/
│   ├── model/           # Shared domain models
│   ├── dto/             # Data Transfer Objects
│   ├── exception/       # Custom exceptions
│   └── util/            # Utility classes
└── pom.xml
```

## Usage

### In Microservices

Add dependency to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.matthew</groupId>
    <artifactId>common-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Example Usage

```java
import com.matthew.common.dto.ApiResponse;
import com.matthew.common.model.Trade;
import com.matthew.common.exception.ApiException;

// Using shared DTO
@GetMapping("/api/trades/{id}")
public ApiResponse<Trade> getTrade(@PathVariable String id) {
    try {
        Trade trade = tradeService.findById(id);
        return ApiResponse.success(trade);
    } catch (Exception e) {
        return ApiResponse.error("TRADE_NOT_FOUND", "Trade not found");
    }
}

// Using shared model
Trade trade = Trade.builder()
    .id("123")
    .symbol("BTC/USDT")
    .side("BUY")
    .quantity(BigDecimal.ONE)
    .build();

// Using shared exception
throw new ApiException("Invalid order", "INVALID_ORDER", 400);
```

## Adding New Shared Classes

When adding new shared classes:

1. Determine if it's truly shared across multiple services
2. Add to appropriate package (model, dto, exception, util)
3. Use Lombok annotations to reduce boilerplate
4. Add JavaDoc comments
5. Update this README
6. Rebuild common-lib: `mvn clean install`

## Guidelines

- **Keep it lightweight**: Only include truly shared code
- **No service-specific logic**: This library should have no dependencies on specific services
- **Backward compatible**: Changes should not break existing services
- **Well-documented**: Add JavaDoc to all public classes and methods
- **Version control**: Increment version when making breaking changes

## Shared Models

### Trade
Used by Trading Service and clients to represent a trade

### User
Used by Identity Service for user information

### Order
Used by Trading Service for order management

### Alert
Used by Notification Service for alert management

## Shared DTOs

### ApiResponse<T>
Standard wrapper for all API responses

### ErrorResponse
Standard error response format

## Shared Exceptions

### ApiException
Base exception for all API errors

### ValidationException
For validation errors

### AuthenticationException
For authentication failures

### AuthorizationException
For authorization failures

## Building

```bash
cd common-lib
mvn clean install
```

This will install the library to your local Maven repository, making it available to other modules.

## Publishing

To publish to a remote repository:

```bash
mvn clean deploy
```

(Requires proper Maven settings.xml configuration)

## Maintenance

- Review shared code regularly for unused classes
- Keep dependencies minimal
- Document all public APIs
- Maintain backward compatibility when possible
