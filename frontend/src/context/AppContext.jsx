import { createContext, useContext, useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { login as apiLogin, register as apiRegister } from '../services/api';
import api from '../services/api';

const AppContext = createContext(null);

export function AppProvider({ children }) {
  const [toast, setToast] = useState(null);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const toastTimer = useRef(null);

  const [auth, setAuth] = useState(() => {
    try {
      const token = localStorage.getItem('price-comp-token');
      const email = localStorage.getItem('price-comp-email');
      const plan = localStorage.getItem('price-comp-plan');
      return token ? { token, email, plan } : null;
    } catch {
      return null;
    }
  });

  const [watchlist, setWatchlist] = useState([]);

  /* ── Toast ─────────────────────────────────────────────────────────── */

  const showToast = useCallback((message, type = 'info') => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToast({ message, type });
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  /* ── Backend Health Check & Offline State ─────────────────────────── */
  const [backendOffline, setBackendOffline] = useState(false);

  useEffect(() => {
    const checkHealth = async () => {
      try {
        await api.get('/health', { timeout: 5000 });
        setBackendOffline(false);
      } catch (err) {
        setBackendOffline(true);
      }
    };
    checkHealth();
  }, []);

  /* ── Custom Event Listener for Global Toasts ───────────────────────── */
  useEffect(() => {
    const handleAppToast = (e) => {
      if (e.detail?.message) {
        showToast(e.detail.message, e.detail.type || 'info');
      }
    };
    window.addEventListener('app-toast', handleAppToast);
    return () => window.removeEventListener('app-toast', handleAppToast);
  }, [showToast]);

  /* ── Fetch watchlist from backend ──────────────────────────────────── */

  const fetchWatchlist = useCallback(async () => {
    const token = localStorage.getItem('price-comp-token');
    if (!token) {
      setWatchlist([]);
      return;
    }

    try {
      setWatchlistLoading(true);
      const res = await api.get('/watchlist');
      const items = (res.data?.items || []).map((item) => ({
        id: item.productId,
        name: item.name,
        imageUrl: item.imageUrl,
        brand: item.brand || '',
        category: item.category || '',
        bestPrice: item.bestPrice != null ? { price: item.bestPrice, platform: item.bestPlatform } : null,
        addedAt: item.addedAt,
      }));
      setWatchlist(items);
    } catch (err) {
      console.error('Failed to fetch watchlist:', err);
      // If 401/403 don't wipe — might be token expiry, user can re-login
      if (err?.response?.status === 401 || err?.response?.status === 403) {
        setWatchlist([]);
      }
    } finally {
      setWatchlistLoading(false);
    }
  }, []);

  /* ── Load watchlist on mount if logged in ──────────────────────────── */

  useEffect(() => {
    if (auth?.token && !backendOffline) {
      fetchWatchlist();
    } else {
      setWatchlist([]);
    }
  }, [auth?.token, fetchWatchlist, backendOffline]);

  /* ── Add to watchlist (persist to backend) ─────────────────────────── */

  const addToWatchlist = useCallback(async (product) => {
    if (!auth?.token) {
      showToast('Please log in to save watchlist', 'warning');
      return;
    }

    // Optimistic local update
    setWatchlist((prev) => {
      if (prev.some((p) => p.id === product.id)) return prev;
      return [...prev, product];
    });

    try {
      await api.post(`/watchlist/${product.id}`);
      showToast(`Added "${product.name}" to watchlist`, 'success');
    } catch (err) {
      if (err?.response?.status === 409) {
        showToast('Already in your watchlist', 'info');
      } else {
        console.error('Failed to add to watchlist:', err);
        showToast('Failed to add to watchlist', 'error');
        // Rollback optimistic update
        setWatchlist((prev) => prev.filter((p) => p.id !== product.id));
      }
    }
  }, [auth?.token, showToast]);

  /* ── Remove from watchlist (persist to backend) ────────────────────── */

  const removeFromWatchlist = useCallback(async (productId) => {
    if (!auth?.token) {
      showToast('Please log in to manage watchlist', 'warning');
      return;
    }

    // Optimistic local removal
    let removed = null;
    setWatchlist((prev) => {
      removed = prev.find((p) => p.id === productId);
      return prev.filter((p) => p.id !== productId);
    });

    try {
      await api.delete(`/watchlist/${productId}`);
      showToast('Removed from watchlist', 'info');
    } catch (err) {
      console.error('Failed to remove from watchlist:', err);
      showToast('Failed to remove from watchlist', 'error');
      // Rollback
      if (removed) {
        setWatchlist((prev) => [...prev, removed]);
      }
    }
  }, [auth?.token, showToast]);

  /* ── Check if product is in watchlist ───────────────────────────────── */

  const isInWatchlist = useCallback(
    (productId) => watchlist.some((p) => p.id === productId),
    [watchlist],
  );

  /* ── Auth: login ───────────────────────────────────────────────────── */

  const login = useCallback(async (email, password) => {
    const data = await apiLogin(email, password);
    localStorage.setItem('price-comp-token', data.token);
    localStorage.setItem('price-comp-email', data.email);
    localStorage.setItem('price-comp-plan', data.plan);
    setAuth({ token: data.token, email: data.email, plan: data.plan });
    // Watchlist will be fetched by the useEffect watching auth.token
    return data;
  }, []);

  /* ── Auth: register ────────────────────────────────────────────────── */

  const register = useCallback(async (email, password) => {
    const data = await apiRegister(email, password);
    localStorage.setItem('price-comp-token', data.token);
    localStorage.setItem('price-comp-email', data.email);
    localStorage.setItem('price-comp-plan', data.plan);
    setAuth({ token: data.token, email: data.email, plan: data.plan });
    // Watchlist will be fetched by the useEffect watching auth.token
    return data;
  }, []);

  /* ── Auth: logout ──────────────────────────────────────────────────── */

  const logout = useCallback(() => {
    try {
      localStorage.removeItem('price-comp-token');
      localStorage.removeItem('price-comp-email');
      localStorage.removeItem('price-comp-plan');
    } catch {
      // ignore
    }
    setAuth(null);
    setWatchlist([]); // Clear watchlist on logout
  }, []);

  /* ── Context value ─────────────────────────────────────────────────── */

  const value = useMemo(() => ({
    toast,
    showToast,
    auth,
    login,
    register,
    logout,
    watchlist,
    watchlistLoading,
    backendOffline,
    addToWatchlist,
    removeFromWatchlist,
    isInWatchlist,
    fetchWatchlist,
  }), [toast, showToast, auth, login, register, logout, watchlist, watchlistLoading, backendOffline, addToWatchlist, removeFromWatchlist, isInWatchlist, fetchWatchlist]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}
