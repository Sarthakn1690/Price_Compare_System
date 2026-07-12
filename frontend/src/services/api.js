import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 300000, // 5 minutes  
});

export let isApiLoading = false;

api.interceptors.request.use((config) => {
  isApiLoading = true;
  try {
    const token = localStorage.getItem('price-comp-token');
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // ignore
  }
  return config;
}, (error) => {
  isApiLoading = false;
  return Promise.reject(error);
});

api.interceptors.response.use(
  (response) => {
    isApiLoading = false;
    return response;
  },
  (error) => {
    isApiLoading = false;

    // Handle Network Errors
    if (!error.response && error.code === 'ERR_NETWORK') {
      const msg = "Cannot connect to server. Make sure the backend is running on port 9090.";
      window.dispatchEvent(new CustomEvent('app-toast', { detail: { message: msg, type: 'error' } }));
    }

    // Handle 401 Unauthorized
    if (error.response?.status === 401) {
      localStorage.removeItem('price-comp-token');
      localStorage.removeItem('price-comp-email');
      localStorage.removeItem('price-comp-plan');
      window.dispatchEvent(new CustomEvent('app-toast', { detail: { message: "Session expired. Please log in again.", type: 'warning' } }));

      // Auto redirect to auth page if not already there
      if (!window.location.pathname.startsWith('/auth')) {
        window.location.href = '/auth';
      }
    }

    return Promise.reject(error);
  }
);

// Auth
export const register = (email, password) =>
  api.post('/auth/register', { email, password }).then((r) => r.data);

export const login = (email, password) =>
  api.post('/auth/login', { email, password }).then((r) => r.data);

// New SerpAPI-based search (GET /api/products/search?query=...)
export const searchProduct = (query) =>
  api.get('/products/search', { params: { query } }).then((r) => r.data);

// Legacy URL-based search (POST /products/search with URL body)
export const searchProductByUrl = (url) =>
  api.post('/products/search', { url }).then((r) => r.data);

export const getProduct = (id) =>
  api.get(`/products/${id}`).then((r) => r.data);

export const getPrices = (id) =>
  api.get(`/products/${id}/prices`).then((r) => r.data);

export const getHistory = (id, days = 14, platform = null) => {
  const params = { days };
  if (platform) params.platform = platform;
  return api.get(`/products/${id}/history`, { params }).then((r) => r.data);
};

export const getRecommendation = (id) =>
  api.get(`/products/${id}/recommendation`).then((r) => r.data);

export const trackProduct = (id) =>
  api.post(`/products/${id}/track`).then((r) => r.data);

// Alerts
export const setAlert = (productId, targetPrice) =>
  api.post('/alerts', { productId, targetPrice }).then((r) => r.data);

export const getAlerts = () => api.get('/alerts').then((r) => r.data);

export const deleteAlert = (id) =>
  api.delete(`/alerts/${id}`).then((r) => r.data);

// Analytics & Health
export const getAnalyticsSummary = () =>
  api.get('/analytics/summary').then((r) => r.data);

export const getScraperHealth = () =>
  api.get('/health/scrapers').then((r) => r.data);

// Watchlist helpers (used by Dashboard too)
export const getWatchlist = () =>
  api.get('/watchlist').then((r) => r.data);

export const addToWatchlistApi = (productId) =>
  api.post(`/watchlist/${productId}`).then((r) => r.data);

export const removeFromWatchlistApi = (productId) =>
  api.delete(`/watchlist/${productId}`).then((r) => r.data);

// Dashboard
export const getDashboard = () =>
  api.get('/dashboard').then((r) => r.data);

export const getRecentSearches = () =>
  api.get('/dashboard/recent-searches').then((r) => r.data);

export const deleteRecentSearch = (id) =>
  api.delete(`/dashboard/recent-searches/${id}`).then((r) => r.data);

export const clearRecentSearches = () =>
  api.delete('/dashboard/recent-searches').then((r) => r.data);

export default api;
