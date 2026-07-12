# Trading Platform - Desktop Client (JavaFX)

## Overview

This is a **lightweight JavaFX desktop client** for the Trading Platform. It serves as a thin UI layer that communicates exclusively with REST APIs provided by the microservices backend.

### Key Principle
**All business logic resides in the microservices, not in this client.**

## Architecture

```
┌─────────────────────────────┐
│   JavaFX Desktop Client     │
│   (This Application)        │
└──────────────┬──────────────┘
               │
               ↓ REST API Calls
┌──────────────────────────────────┐
│   API Gateway (Port 8080)        │
└──────────────┬───────────────────┘
               │
    ┌──────────┼──────────┬──────────┐
    ↓          ↓          ↓          ↓
Identity    Market    Trading    Notification
Service     Service   Service    Service
```

## Features

- **Lightweight**: Only UI components, no business logic
- **API-First**: All data comes from REST APIs
- **Cross-Platform**: Runs on Windows, macOS, Linux
- **Real-Time Updates**: WebSocket support for live data
- **Responsive**: Smooth UI interactions with proper threading

## Running the Application

### Prerequisites
- Java 21+
- Maven 3.8+
- Microservices running (see main project README)

### Build
```bash
mvn clean package
```

### Run
```bash
mvn javafx:run
```

Or run the JAR:
```bash
java -jar target/desktop-client-1.0.0-SNAPSHOT.jar
```

## Configuration

Edit `src/main/resources/application.yml` to configure API endpoints:

```yaml
api:
  gateway:
    url: http://localhost:8080
  identity:
    url: http://localhost:9898
  market:
    url: http://localhost:9001
  trading:
    url: http://localhost:9002
```

## Project Structure

```
desktop-client/
├── src/main/java/com/matthew/desktop/
│   ├── DesktopApplication.java      # Main entry point
│   ├── config/
│   │   └── DesktopClientConfig.java # Spring configuration
│   ├── controller/                  # JavaFX controllers
│   ├── service/
│   │   └── ApiClientService.java    # REST API client
│   └── model/                       # DTOs and models
├── src/main/resources/
│   ├── application.yml              # Configuration
│   └── fxml/                        # JavaFX UI definitions
└── pom.xml                          # Maven configuration
```

## API Client Usage

The `ApiClientService` provides methods for calling microservices:

```java
@Autowired
private ApiClientService apiClient;

// Get market data
Object marketData = apiClient.getMarketData("BTC/USDT");

// Get live price
Object price = apiClient.getLivePrice("BTC/USDT");

// Place order
Object order = apiClient.placeOrder(orderRequest);

// Get open positions
Object positions = apiClient.getOpenPositions(userId);
```

## Development Guidelines

1. **No Business Logic**: Keep all logic in microservices
2. **API Calls Only**: Use `ApiClientService` for all backend communication
3. **Error Handling**: Handle API errors gracefully with user feedback
4. **Threading**: Use JavaFX Platform.runLater() for UI updates from background threads
5. **Logging**: Use SLF4J for logging

## Building Controllers

Example controller structure:

```java
@Controller
public class DashboardController {
    @Autowired
    private ApiClientService apiClient;

    @FXML
    private void initialize() {
        // Load data from API
        loadMarketData();
    }

    private void loadMarketData() {
        // Call API and update UI
    }
}
```

## Troubleshooting

### Connection Refused
- Ensure microservices are running
- Check API URLs in `application.yml`
- Verify firewall settings

### Slow Performance
- Check network latency to microservices
- Monitor API response times
- Consider caching frequently accessed data

### UI Freezing
- Ensure API calls are made on background threads
- Use `Platform.runLater()` for UI updates

## Future Enhancements

- WebSocket support for real-time updates
- Local caching of market data
- Offline mode with cached data
- Dark/Light theme support
- Keyboard shortcuts
- Multi-window support

## Contributing

When adding new features:
1. Create corresponding API client methods in `ApiClientService`
2. Create JavaFX controllers in `controller/` package
3. Add FXML files in `resources/fxml/`
4. Update this README with new features

## License

Same as the main Trading Platform project
