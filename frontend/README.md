# Trading Platform - Frontend (React 19)

## Overview

This is the **web frontend** for the Trading Platform. It communicates exclusively with microservices REST APIs.

### Key Principle
**All business logic resides in the microservices, not in this frontend.**

## Architecture

```
┌─────────────────────────────┐
│   React 19 Web Frontend     │
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

- **API-First Architecture**: All data from microservices
- **Real-Time Updates**: Live market data and trade execution
- **Responsive Design**: Works on desktop and mobile
- **Professional UI**: Dark theme optimized for trading
- **No Business Logic**: Pure presentation layer

## Getting Started

### Prerequisites
- Node.js 18+
- npm or yarn
- Microservices running (see main project README)

### Installation

```bash
cd frontend
npm install
```

### Development

```bash
npm run dev
```

The frontend will be available at `http://localhost:5173`

### Build

```bash
npm run build
```

### Preview Production Build

```bash
npm run preview
```

## Configuration

Create a `.env` file based on `.env.example`:

```env
VITE_API_GATEWAY_URL=http://localhost:8080
VITE_IDENTITY_SERVICE_URL=http://localhost:9898
VITE_MARKET_SERVICE_URL=http://localhost:9001
VITE_TRADING_SERVICE_URL=http://localhost:9002
VITE_NOTIFICATION_SERVICE_URL=http://localhost:9003
```

## Project Structure

```
frontend/
├── src/
│   ├── components/          # Reusable React components
│   ├── pages/               # Page components
│   ├── services/
│   │   └── api.ts           # API client (calls microservices)
│   ├── hooks/               # Custom React hooks
│   ├── types/               # TypeScript types
│   ├── App.tsx              # Main app component
│   └── main.tsx             # Entry point
├── public/                  # Static assets
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## API Client Usage

The `api.ts` service provides methods for calling microservices:

```typescript
import { apiClient } from './services/api';

// Authentication
await apiClient.login(email, password);
await apiClient.logout();

// Market Data
const marketData = await apiClient.getMarketData('BTC/USDT');
const price = await apiClient.getLivePrice('BTC/USDT');
const chart = await apiClient.getChartData('BTC/USDT', '1h');

// Trading
await apiClient.placeOrder(orderData);
const positions = await apiClient.getOpenPositions();
const trades = await apiClient.getTradeHistory();

// Alerts
await apiClient.createAlert(alertData);
const alerts = await apiClient.getAlerts();
```

## Development Guidelines

1. **No Business Logic**: Keep all logic in microservices
2. **API Calls Only**: Use `apiClient` for all backend communication
3. **Error Handling**: Handle API errors gracefully
4. **Loading States**: Show loading indicators during API calls
5. **Type Safety**: Use TypeScript for all components

## Building Components

Example component structure:

```typescript
import { useEffect, useState } from 'react';
import { apiClient } from '../services/api';

export function Dashboard() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const response = await apiClient.getMarketData('BTC/USDT');
      setData(response.data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return <div>{/* Render data */}</div>;
}
```

## Troubleshooting

### CORS Errors
- Ensure API Gateway is running
- Check CORS configuration in API Gateway
- Verify API URLs in `.env`

### 401 Unauthorized
- Check authentication token
- Ensure user is logged in
- Verify JWT token expiration

### Slow Performance
- Check network latency
- Monitor API response times
- Consider implementing caching

## Deployment

### Build for Production
```bash
npm run build
```

### Deploy to Server
```bash
# Copy dist/ folder to your web server
# Configure web server to serve index.html for all routes
```

### Environment Variables
Set environment variables on your deployment platform:
- `VITE_API_GATEWAY_URL`
- `VITE_IDENTITY_SERVICE_URL`
- `VITE_MARKET_SERVICE_URL`
- `VITE_TRADING_SERVICE_URL`
- `VITE_NOTIFICATION_SERVICE_URL`

## Contributing

When adding new features:
1. Create corresponding API client methods in `services/api.ts`
2. Create React components in `components/` or `pages/`
3. Use TypeScript for type safety
4. Handle loading and error states
5. Update this README with new features

## Performance Tips

1. **Code Splitting**: Use React.lazy() for route-based splitting
2. **Memoization**: Use useMemo() and useCallback() for expensive operations
3. **API Caching**: Implement caching for frequently accessed data
4. **Image Optimization**: Optimize images before serving
5. **Bundle Analysis**: Use vite-plugin-visualizer to analyze bundle size

## Security

1. **HTTPS Only**: Always use HTTPS in production
2. **Token Storage**: Store JWT tokens securely (HttpOnly cookies preferred)
3. **CORS**: Configure CORS properly on API Gateway
4. **Input Validation**: Validate all user inputs
5. **XSS Prevention**: Sanitize user-generated content

## Future Enhancements

- WebSocket support for real-time updates
- Offline mode with service workers
- Dark/Light theme toggle
- Multi-language support
- Advanced charting with TradingView Lightweight Charts
- Mobile app (React Native)

## License

Same as the main Trading Platform project
