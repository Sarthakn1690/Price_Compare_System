import { useState, useEffect, useCallback, useRef } from 'react';
import { getAlerts, deleteAlert, searchProduct } from '../services/api';
import { useApp } from '../context/AppContext';
import { motion, AnimatePresence } from 'framer-motion';
import { Link } from 'react-router-dom';
import axios from 'axios'; // We can use the configured api.js actually

// Assuming we have an api method to set and toggle alerts. We will add those or inline them.
import api from '../services/api'; 

export default function Alerts() {
  const { showToast, token } = useApp();
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);

  // Form states
  const [searchQuery, setSearchQuery] = useState('');
  const [targetPrice, setTargetPrice] = useState('');
  const [email, setEmail] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [searchResult, setSearchResult] = useState(null);

  const loadAlerts = useCallback(async () => {
    if (!token) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const data = await getAlerts();
      setAlerts(data);
    } catch (err) {
      // Don't show toast if it's just an auth error when reloading layout
      if (err.response?.status !== 401 && err.response?.status !== 403) {
        showToast('Failed to load alerts', 'error');
      }
    } finally {
      setLoading(false);
    }
  }, [showToast, token]);

  useEffect(() => {
    loadAlerts();
  }, [loadAlerts]);

  const handleDelete = async (id) => {
    try {
      await deleteAlert(id);
      setAlerts(prev => prev.filter(a => a.id !== id));
      showToast('Alert deleted', 'success');
    } catch (err) {
      showToast('Failed to delete alert', 'error');
    }
  };

  const handleToggle = async (id) => {
    try {
      const { data } = await api.patch(`/alerts/${id}/toggle`);
      setAlerts(prev => prev.map(a => a.id === id ? data : a));
      showToast(`Alert ${data.active ? 'resumed' : 'paused'}`, 'success');
    } catch (err) {
      showToast('Failed to toggle alert', 'error');
    }
  };

  const handleSearchSubmit = async (e) => {
    e.preventDefault();
    if (searchQuery.trim().length < 2) return;
    setIsSearching(true);
    setSearchResult(null);
    try {
      const data = await searchProduct(searchQuery.trim());
      setSearchResult(data);
      if (data.bestPrice?.price) {
        setTargetPrice(Math.max(1, Math.floor(data.bestPrice.price * 0.9)).toString());
      }
    } catch (err) {
      showToast('Product not found or failed to search', 'error');
    } finally {
      setIsSearching(false);
    }
  };

  const handleCreateAlert = async (e) => {
    e.preventDefault();
    if (!searchResult?.id || !targetPrice) return;
    try {
      const payload = {
        productId: searchResult.id,
        targetPrice: Number(targetPrice),
        ...(email && { email })
      };
      const { data } = await api.post('/alerts', payload);
      setAlerts(prev => [data, ...prev]);
      showToast('Alert created successfully!', 'success');
      
      // Reset form
      setSearchQuery('');
      setSearchResult(null);
      setTargetPrice('');
      setEmail('');
    } catch (err) {
      showToast(err.response?.data?.error || 'Failed to create alert', 'error');
    }
  };

  if (!token) {
    return (
      <main className="mx-auto flex max-w-lg flex-col pt-32 px-4 text-center">
        <h1 className="font-display text-2xl tracking-wide text-white">Log in to set price alerts</h1>
        <p className="mt-2 text-white/50">Keep track of your favorite products and get notified when prices drop.</p>
        <Link to="/login" className="mt-8 rounded-lg bg-accent px-6 py-3 font-medium text-surface">
          Sign In
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-8">
        <h1 className="font-display text-2xl uppercase tracking-tight text-white">Price Alerts</h1>
        <p className="mt-2 text-white/60">Get notified when products drop below your target price.</p>
      </header>

      {/* ── Create Alert Form ── */}
      <section className="mb-12 rounded-xl border border-white/10 bg-surface-elevated/40 p-6">
        <h2 className="mb-4 font-display text-lg tracking-wide text-white/80">Create New Alert</h2>
        
        {!searchResult ? (
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row">
            <input
              type="text"
              placeholder="Search for a product first..."
              required
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="flex-1 rounded-lg border border-white/20 bg-surface px-4 py-2 text-sm text-white focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              disabled={isSearching}
            />
            <button
              type="submit"
              disabled={isSearching || searchQuery.trim().length < 2}
              className="rounded-lg bg-white/10 px-6 py-2 text-sm font-medium text-white transition hover:bg-white/20 disabled:opacity-50"
            >
              {isSearching ? 'Searching...' : 'Find Product'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleCreateAlert} className="space-y-4">
            <div className="flex items-center justify-between rounded-lg bg-surface/50 p-4 border border-white/5">
              <div className="flex items-center gap-4">
                {searchResult.imageUrl && (
                  <img src={searchResult.imageUrl} alt="" className="h-12 w-12 rounded bg-white object-contain p-1" />
                )}
                <div>
                  <p className="text-sm font-bold text-white">{searchResult.name}</p>
                  <p className="text-xs text-white/50">Current Best: ₹{searchResult.bestPrice?.price || 'N/A'}</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSearchResult(null)}
                className="text-xs font-medium text-danger hover:underline"
              >
                Change
              </button>
            </div>

            <div className="flex flex-col gap-4 sm:flex-row">
              <div className="flex-1">
                <label className="mb-1 block text-xs font-medium text-white/60">Target Price (₹)</label>
                <input
                  type="number"
                  required
                  min="1"
                  value={targetPrice}
                  onChange={(e) => setTargetPrice(e.target.value)}
                  className="w-full rounded-lg border border-white/20 bg-surface px-4 py-2 text-sm text-white focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div className="flex-1">
                <label className="mb-1 block text-xs font-medium text-white/60">Notification Email (Optional)</label>
                <input
                  type="email"
                  placeholder="Leave empty to use account email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-lg border border-white/20 bg-surface px-4 py-2 text-sm text-white focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={!targetPrice}
              className="mt-2 w-full sm:w-auto rounded-lg bg-accent px-6 py-2.5 text-sm font-bold uppercase tracking-wide text-surface hover:bg-accent-muted"
            >
              Set Price Alert
            </button>
          </form>
        )}
      </section>

      {/* ── Alert List ── */}
      {loading ? (
        <div className="flex justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
        </div>
      ) : alerts.length === 0 ? (
        <div className="rounded-xl border border-white/10 bg-surface/50 p-12 text-center">
          <p className="text-white/40">You haven't set any price alerts yet.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <AnimatePresence>
            {alerts.map((alert) => (
              <motion.div
                key={alert.id}
                layout
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                className="group relative flex flex-col gap-3 rounded-xl border border-white/10 bg-surface p-4 transition-colors hover:border-white/20"
              >
                <div className="flex items-start gap-4">
                  {alert.imageUrl ? (
                    <img src={alert.imageUrl} alt="" className="h-16 w-16 shrink-0 rounded-lg bg-white object-contain p-1" />
                  ) : (
                    <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-white/5 text-2xl">🛍️</div>
                  )}
                  
                  <div className="flex-1 overflow-hidden">
                    <Link to={`/product/${alert.productId}`} className="truncate text-sm font-medium text-white hover:text-accent hover:underline block">
                      {alert.productName}
                    </Link>
                    
                    <div className="mt-2 grid grid-cols-2 gap-x-2 gap-y-1 text-xs">
                      <span className="text-white/50">Target Price:</span>
                      <span className="font-bold text-white">₹{alert.targetPrice?.toLocaleString('en-IN') || '0'}</span>
                      
                      <span className="text-white/50">Current Best:</span>
                      <span className={`font-bold ${
                        alert.currentBestPrice && alert.currentBestPrice <= alert.targetPrice
                          ? 'text-green-500' 
                          : 'text-white/80'
                      }`}>
                        ₹{alert.currentBestPrice?.toLocaleString('en-IN') || 'N/A'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-2 flex items-center justify-between border-t border-white/10 pt-3">
                  <div className="flex items-center gap-2">
                    <div className={`h-2.5 w-2.5 rounded-full ${
                      alert.status === 'TRIGGERED' ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]' :
                      alert.status === 'WAITING' ? 'bg-amber-400 animate-pulse' :
                      'bg-white/30'
                    }`} />
                    <span className="text-xs font-bold tracking-wider text-white/70">
                      {alert.status}
                    </span>
                  </div>
                  
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => handleToggle(alert.id)}
                      className="rounded p-1.5 text-white/50 hover:bg-white/10 hover:text-white transition-colors"
                      title={alert.active ? 'Pause alert' : 'Resume alert'}
                    >
                      {alert.active ? (
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                      ) : (
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                      )}
                    </button>
                    <button
                      onClick={() => handleDelete(alert.id)}
                      className="rounded p-1.5 text-white/50 hover:bg-danger/10 hover:text-danger transition-colors"
                      title="Delete alert"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
      )}
    </main>
  );
}
