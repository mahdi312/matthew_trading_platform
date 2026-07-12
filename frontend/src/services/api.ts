import axios, { AxiosInstance } from 'axios';

/**
 * API Service for Frontend
 * 
 * This service handles all communication with microservices
 * All business logic is on the backend - this is just an API client
 */

const API_GATEWAY_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';
const IDENTITY_URL = import.meta.env.VITE_IDENTITY_SERVICE_URL || 'http://localhost:9898';
const MARKET_URL = import.meta.env.VITE_MARKET_SERVICE_URL || 'http://localhost:9001';
const TRADING_URL = import.meta.env.VITE_TRADING_SERVICE_URL || 'http://localhost:9002';
const NOTIFICATION_URL = import.meta.env.VITE_NOTIFICATION_SERVICE_URL || 'http://localhost:9003';

class ApiClient {
  private client: AxiosInstance;
  private authToken: string | null = null;

  constructor() {
    this.client = axios.create({
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Add request interceptor to include auth token
    this.client.interceptors.request.use((config) => {
      if (this.authToken) {
        config.headers.Authorization = `Bearer ${this.authToken}`;
      }
      return config;
    });

    // Add response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          // Handle unauthorized - redirect to login
          this.authToken = null;
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );
  }

  setAuthToken(token: string) {
    this.authToken = token;
  }

  clearAuthToken() {
    this.authToken = null;
  }

  // ==================== Identity Service ====================
  async login(email: string, password: string) {
    return this.client.post(`${IDENTITY_URL}/api/auth/login`, {
      email,
      password,
    });
  }

  async register(email: string, password: string, name: string) {
    return this.client.post(`${IDENTITY_URL}/api/auth/register`, {
      email,
      password,
      name,
    });
  }

  async logout() {
    return this.client.post(`${IDENTITY_URL}/api/auth/logout`);
  }

  async getUserProfile(userId: string) {
    return this.client.get(`${IDENTITY_URL}/api/users/${userId}`);
  }

  async updateUserProfile(userId: string, data: any) {
    return this.client.put(`${IDENTITY_URL}/api/users/${userId}`, data);
  }

  // ==================== Market Service ====================
  async getMarketData(symbol: string) {
    return this.client.get(`${MARKET_URL}/api/market/data/${symbol}`);
  }

  async getLivePrice(symbol: string) {
    return this.client.get(`${MARKET_URL}/api/market/price/${symbol}`);
  }

  async getChartData(symbol: string, period: string = '1h') {
    return this.client.get(`${MARKET_URL}/api/market/chart/${symbol}`, {
      params: { period },
    });
  }

  async getIndicators(symbol: string) {
    return this.client.get(`${MARKET_URL}/api/market/indicators/${symbol}`);
  }

  async getOHLCV(symbol: string) {
    return this.client.get(`${MARKET_URL}/api/market/ohlcv/${symbol}`);
  }

  // ==================== Trading Service ====================
  async placeOrder(orderData: any) {
    return this.client.post(`${TRADING_URL}/api/orders`, orderData);
  }

  async getOrder(orderId: string) {
    return this.client.get(`${TRADING_URL}/api/orders/${orderId}`);
  }

  async updateOrder(orderId: string, data: any) {
    return this.client.put(`${TRADING_URL}/api/orders/${orderId}`, data);
  }

  async cancelOrder(orderId: string) {
    return this.client.delete(`${TRADING_URL}/api/orders/${orderId}`);
  }

  async getOpenPositions() {
    return this.client.get(`${TRADING_URL}/api/positions`);
  }

  async getTradeHistory() {
    return this.client.get(`${TRADING_URL}/api/trades`);
  }

  async getTrade(tradeId: string) {
    return this.client.get(`${TRADING_URL}/api/trades/${tradeId}`);
  }

  async getPnL() {
    return this.client.get(`${TRADING_URL}/api/pnl`);
  }

  // ==================== Notification Service ====================
  async createAlert(alertData: any) {
    return this.client.post(`${NOTIFICATION_URL}/api/alerts`, alertData);
  }

  async getAlerts() {
    return this.client.get(`${NOTIFICATION_URL}/api/alerts`);
  }

  async updateAlert(alertId: string, data: any) {
    return this.client.put(`${NOTIFICATION_URL}/api/alerts/${alertId}`, data);
  }

  async deleteAlert(alertId: string) {
    return this.client.delete(`${NOTIFICATION_URL}/api/alerts/${alertId}`);
  }

  async sendEmailNotification(data: any) {
    return this.client.post(`${NOTIFICATION_URL}/api/notifications/email`, data);
  }

  async sendTelegramNotification(data: any) {
    return this.client.post(`${NOTIFICATION_URL}/api/notifications/telegram`, data);
  }
}

export const apiClient = new ApiClient();
