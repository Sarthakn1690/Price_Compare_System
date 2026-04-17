import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import {
  getDashboard,
  deleteRecentSearch,
  clearRecentSearches,
  removeFromWatchlistApi,
} from '../services/api';

/* ─────────────────────────────────────────────────────────────────────────
   Helpers
───────────────────────────────────────────────────────────────────────── */

function formatINR(price) {
  if (price == null) return '—';
  const num = Number(price);
  if (Number.isNaN(num) || num <= 0) return '—';
  return `₹${num.toLocaleString('en-IN')}`;
}

function relativeTime(ts) {
  if (!ts) return '';
  const diff = Date.now() - new Date(ts).getTime();
  const mins = Math.floor(diff / 60000);
  const hrs = Math.floor(mins / 60);
  const days = Math.floor(hrs / 24);
  if (mins < 2) return 'just now';
  if (mins < 60) return `${mins} minutes ago`;
  if (hrs < 24) return `${hrs} hour${hrs > 1 ? 's' : ''} ago`;
  if (days === 1) return 'Yesterday';
  return `${days} days ago`;
}

const PLATFORM_COLORS = {
  Amazon: '#FF9900',
  Flipkart: '#2874F0',
  Meesho: '#F43397',
  Croma: '#00A200',
  Reliance: '#1A237E',
  Default: '#b8ff3c',
};

function platformColor(name) {
  return PLATFORM_COLORS[name] || PLATFORM_COLORS.Default;
}

/* ─────────────────────────────────────────────────────────────────────────
   Skeleton
───────────────────────────────────────────────────────────────────────── */

function SkeletonBlock({ className = '' }) {
  return <div className={`animate-pulse rounded-xl bg-white/5 ${className}`} />;
}

/* ─────────────────────────────────────────────────────────────────────────
   Stat Card
───────────────────────────────────────────────────────────────────────── */

function StatCard({ icon, label, value, delay = 0 }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.4 }}
      className="relative overflow-hidden rounded-xl border border-white/10 bg-white/5 p-5 backdrop-blur-sm"
    >
      {/* Subtle glow */}
      <div className="pointer-events-none absolute -top-8 -right-8 h-24 w-24 rounded-full bg-accent/10 blur-2xl" />
      <span className="absolute right-4 top-4 text-2xl">{icon}</span>
      <p className="text-3xl font-bold text-white">{value ?? '—'}</p>
      <p className="mt-1 text-sm text-white/60">{label}</p>
    </motion.div>
  );
}

/* ─────────────────────────────────────────────────────────────────────────
   Custom tooltip for the Recharts line chart
───────────────────────────────────────────────────────────────────────── */

function PriceTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-white/10 bg-surface-elevated/95 p-3 text-xs shadow-xl backdrop-blur-sm">
      <p className="mb-1.5 font-medium text-white/60">{label}</p>
      {payload.map((entry) => (
        <div key={entry.dataKey} className="flex items-center gap-2">
          <span className="inline-block h-2 w-2 rounded-full" style={{ background: entry.color }} />
          <span className="text-white/80">{entry.name}:</span>
          <span className="font-mono text-white">{formatINR(entry.value)}</span>
        </div>
      ))}
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────────────────
   Dashboard page
───────────────────────────────────────────────────────────────────────── */

export default function Dashboard() {
  const navigate = useNavigate();
  const isLoggedIn = !!localStorage.getItem('price-comp-token');

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [data, setData] = useState(null);

  // For the price trend chart
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [historyData, setHistoryData] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);

  /* ── Fetch dashboard data ────────────────────────────────────────── */
  const fetchDashboard = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getDashboard();
      setData(result);
      // Auto-select first watchlist item for the chart
      if (result.watchlist?.length > 0 && !selectedProductId) {
        setSelectedProductId(result.watchlist[0].productId);
      }
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401 || status === 403) {
        setError('auth');
      } else {
        setError('offline');
      }
    } finally {
      setLoading(false);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!isLoggedIn) {
      setError('auth');
      setLoading(false);
      return;
    }
    fetchDashboard();
  }, [fetchDashboard, isLoggedIn]);

  /* ── Fetch price history when selected product changes ───────────── */
  useEffect(() => {
    if (!selectedProductId) return;
    setHistoryLoading(true);
    setHistoryData(null);
    import('../services/api').then(({ getHistory }) => {
      getHistory(selectedProductId, 30)
        .then((res) => setHistoryData(res))
        .catch(() => setHistoryData(null))
        .finally(() => setHistoryLoading(false));
    });
  }, [selectedProductId]);

  /* ── Remove from watchlist (optimistic) ─────────────────────────── */
  const handleRemoveWatchlist = async (productId) => {
    setData((prev) => ({
      ...prev,
      watchlist: prev.watchlist.filter((w) => w.productId !== productId),
      watchlistCount: prev.watchlistCount - 1,
    }));
    try {
      await removeFromWatchlistApi(productId);
    } catch {
      // silently ignore — user can refresh
    }
  };

  /* ── Delete a single recent search ──────────────────────────────── */
  const handleDeleteSearch = async (id) => {
    setData((prev) => ({
      ...prev,
      recentSearches: prev.recentSearches.filter((s) => s.id !== id),
    }));
    try {
      await deleteRecentSearch(id);
    } catch {
      // ignore
    }
  };

  /* ── Clear all recent searches ───────────────────────────────────── */
  const handleClearAll = async () => {
    setData((prev) => ({ ...prev, recentSearches: [] }));
    try {
      await clearRecentSearches();
    } catch {
      // ignore
    }
  };

  /* ── Build chart data from history response ──────────────────────── */
  function buildChartLines(historyRes) {
    if (!historyRes?.entries?.length) return { lines: [], chartData: [] };

    const platforms = [...new Set(historyRes.entries.map((e) => e.platform))];
    const byDate = {};

    historyRes.entries.forEach((e) => {
      const date = new Date(e.recordedAt || e.timestamp || e.date);
      const label = date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
      if (!byDate[label]) byDate[label] = { date: label };
      byDate[label][e.platform] = Number(e.price);
    });

    const chartData = Object.values(byDate).sort((a, b) => {
      const da = new Date(a.date);
      const db = new Date(b.date);
      return da - db;
    });

    return { lines: platforms, chartData };
  }

  /* ════════════════════════════════════════════════════════════════════
     Error states
  ════════════════════════════════════════════════════════════════════ */

  if (!loading && error === 'auth') {
    return (
      <main className="mx-auto flex min-h-[60vh] max-w-lg items-center justify-center px-4 py-16">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="w-full rounded-2xl border border-white/10 bg-white/5 p-10 text-center backdrop-blur-sm"
        >
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-accent/10 text-3xl">
            🔒
          </div>
          <h2 className="font-display text-xl uppercase tracking-tight text-white">
            Sign in to view your Dashboard
          </h2>
          <p className="mt-2 text-sm text-white/60">
            Track products, set price alerts and view your search history all in one place.
          </p>
          <Link
            to="/auth"
            className="mt-6 inline-block rounded-lg bg-accent px-6 py-2.5 font-display text-sm uppercase tracking-wide text-surface transition hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-accent"
          >
            Sign in / Create Account
          </Link>
        </motion.div>
      </main>
    );
  }

  if (!loading && error === 'offline') {
    return (
      <main className="mx-auto flex min-h-[60vh] max-w-lg items-center justify-center px-4 py-16">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="w-full rounded-2xl border border-white/10 bg-white/5 p-10 text-center backdrop-blur-sm"
        >
          <div className="mb-4 text-4xl">⚠️</div>
          <h2 className="font-display text-xl uppercase tracking-tight text-white">
            Could not load Dashboard
          </h2>
          <p className="mt-2 text-sm text-white/60">
            Make sure the backend is running on port 9090, then try again.
          </p>
          <button
            onClick={fetchDashboard}
            className="mt-6 inline-block rounded-lg bg-accent px-6 py-2.5 font-display text-sm uppercase tracking-wide text-surface transition hover:opacity-90"
          >
            Retry
          </button>
        </motion.div>
      </main>
    );
  }

  /* ════════════════════════════════════════════════════════════════════
     Loading skeleton
  ════════════════════════════════════════════════════════════════════ */

  if (loading) {
    return (
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        {/* Header skeleton */}
        <div className="flex items-center justify-between">
          <SkeletonBlock className="h-8 w-48" />
          <SkeletonBlock className="h-9 w-24" />
        </div>
        {/* Stat cards skeleton */}
        <div className="mt-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => <SkeletonBlock key={i} className="h-28" />)}
        </div>
        {/* Watchlist skeleton */}
        <SkeletonBlock className="mt-10 h-6 w-40" />
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[...Array(3)].map((_, i) => <SkeletonBlock key={i} className="h-36" />)}
        </div>
        {/* Searches skeleton */}
        <SkeletonBlock className="mt-10 h-6 w-40" />
        <div className="mt-4 flex flex-col gap-3">
          {[...Array(4)].map((_, i) => <SkeletonBlock key={i} className="h-16" />)}
        </div>
      </main>
    );
  }

  /* ════════════════════════════════════════════════════════════════════
     Main render
  ════════════════════════════════════════════════════════════════════ */

  const { stats = {}, watchlist = [], recentSearches = [] } = data || {};
  const { lines: chartLines, chartData } = buildChartLines(historyData);

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">

      {/* ── Page header ───────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <motion.h1
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            className="font-display text-2xl uppercase tracking-tight text-white sm:text-3xl"
          >
            Dashboard
          </motion.h1>
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
            className="mt-1 text-sm text-white/50"
          >
            {data?.user && <>Signed in as <span className="text-accent">{data.user}</span></>}
          </motion.p>
        </div>
        <motion.button
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.15 }}
          onClick={fetchDashboard}
          className="flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium text-white/70 transition hover:bg-white/10 hover:text-white"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Refresh
        </motion.button>
      </div>

      {/* ══════════════════════════════════════════════════════════════════
           SECTION A — Stats Bar
      ═══════════════════════════════════════════════════════════════════ */}
      <section aria-label="Overview statistics" className="mt-8">
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard icon="🔖" label="Products Tracked" value={stats.totalSaved ?? 0} delay={0} />
          <StatCard icon="🔍" label="Recent Searches" value={stats.totalSearches ?? 0} delay={0.06} />
          <StatCard icon="🔔" label="Active Alerts" value={stats.activeAlerts ?? 0} delay={0.12} />
          <StatCard icon="💰" label="Avg Savings" value={`${stats.avgSavingsPercent ?? 0}%`} delay={0.18} />
        </div>
      </section>

      {/* ══════════════════════════════════════════════════════════════════
           SECTION B — Watchlist Grid
      ═══════════════════════════════════════════════════════════════════ */}
      <section aria-label="Your watchlist" className="mt-12">
        <h2 className="font-display text-xl uppercase tracking-tight text-white">
          Your Watchlist
        </h2>

        {watchlist.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="mt-6 rounded-xl border border-white/10 bg-white/5 p-10 text-center"
          >
            <div className="mx-auto mb-3 text-4xl">🛍️</div>
            <p className="text-white/60">No products saved yet.</p>
            <Link
              to="/"
              className="mt-5 inline-block rounded-lg bg-accent px-5 py-2 text-sm font-medium text-surface transition hover:opacity-90"
            >
              Start Comparing
            </Link>
          </motion.div>
        ) : (
          <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <AnimatePresence>
              {watchlist.map((item, i) => (
                <motion.li
                  key={item.productId}
                  layout
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  transition={{ delay: i * 0.05 }}
                  className="group list-none overflow-hidden rounded-xl border border-white/10 bg-white/5 p-4 backdrop-blur-sm transition hover:border-accent/30"
                >
                  <div className="flex gap-3">
                    {/* Image */}
                    <div className="h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-white/5">
                      {item.imageUrl ? (
                        <img
                          src={item.imageUrl}
                          alt={item.productName}
                          className="h-full w-full object-cover transition group-hover:scale-105"
                          loading="lazy"
                          onError={(e) => {
                            e.target.style.display = 'none';
                            e.target.nextElementSibling?.classList.remove('hidden');
                          }}
                        />
                      ) : null}
                      <div className={`flex h-full w-full items-center justify-center text-2xl ${item.imageUrl ? 'hidden' : ''}`}>
                        🛍️
                      </div>
                    </div>

                    {/* Info */}
                    <div className="min-w-0 flex-1">
                      <h3 className="line-clamp-2 text-sm font-medium text-white">
                        {item.productName}
                      </h3>
                      {item.bestPrice != null && (
                        <p className="mt-1 font-mono text-base font-semibold text-accent">
                          {formatINR(item.bestPrice)}
                        </p>
                      )}
                      {item.bestPlatform && (
                        <span
                          className="mt-1 inline-block rounded px-1.5 py-0.5 text-[10px] font-medium text-surface"
                          style={{ background: platformColor(item.bestPlatform) }}
                        >
                          {item.bestPlatform}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="mt-4 flex gap-2">
                    <Link
                      to={`/product/${item.productId}`}
                      className="flex-1 rounded-lg border border-white/10 bg-white/5 py-1.5 text-center text-xs font-medium text-white/70 transition hover:bg-white/10 hover:text-white"
                    >
                      View Details
                    </Link>
                    <button
                      type="button"
                      onClick={() => handleRemoveWatchlist(item.productId)}
                      className="rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-medium text-white/60 transition hover:border-danger/40 hover:bg-danger/10 hover:text-danger"
                    >
                      Remove
                    </button>
                  </div>
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        )}
      </section>

      {/* ══════════════════════════════════════════════════════════════════
           SECTION C — Recent Searches
      ═══════════════════════════════════════════════════════════════════ */}
      <section aria-label="Recent searches" className="mt-12">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-xl uppercase tracking-tight text-white">
            Recent Searches
          </h2>
          {recentSearches.length > 0 && (
            <button
              onClick={handleClearAll}
              className="text-xs font-medium text-white/40 transition hover:text-danger"
            >
              Clear All
            </button>
          )}
        </div>

        {recentSearches.length === 0 ? (
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="mt-6 rounded-xl border border-white/10 bg-white/5 p-8 text-center text-sm text-white/50"
          >
            No recent searches. Search for a product to get started.
          </motion.p>
        ) : (
          <ul className="mt-4 flex flex-col gap-2">
            <AnimatePresence>
              {recentSearches.map((s, i) => (
                <motion.li
                  key={s.id}
                  layout
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, height: 0, marginBottom: 0 }}
                  transition={{ delay: i * 0.03 }}
                  className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 p-3 backdrop-blur-sm"
                >
                  {/* Thumbnail */}
                  <div className="h-12 w-12 shrink-0 overflow-hidden rounded-lg bg-white/5">
                    {s.imageUrl ? (
                      <img
                        src={s.imageUrl}
                        alt={s.productName}
                        className="h-full w-full object-cover"
                        loading="lazy"
                        onError={(e) => { e.target.style.display = 'none'; }}
                      />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center text-xl text-white/20">🔍</div>
                    )}
                  </div>

                  {/* Name + time */}
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-white">
                      {s.productName || s.query}
                    </p>
                    <p className="mt-0.5 text-xs text-white/40">{relativeTime(s.searchedAt)}</p>
                  </div>

                  {/* Price + platform */}
                  <div className="shrink-0 text-right">
                    {s.bestPriceFound != null && (
                      <p className="font-mono text-sm font-semibold text-accent">
                        {formatINR(s.bestPriceFound)}
                      </p>
                    )}
                    {s.bestPlatform && (
                      <span
                        className="inline-block rounded px-1.5 py-0.5 text-[10px] font-medium text-surface"
                        style={{ background: platformColor(s.bestPlatform) }}
                      >
                        {s.bestPlatform}
                      </span>
                    )}
                  </div>

                  {/* Delete button */}
                  <button
                    type="button"
                    onClick={() => handleDeleteSearch(s.id)}
                    aria-label="Remove this search"
                    className="ml-1 shrink-0 rounded-lg p-1.5 text-white/30 transition hover:bg-white/10 hover:text-white/80"
                  >
                    <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        )}
      </section>

      {/* ══════════════════════════════════════════════════════════════════
           SECTION D — Price Trend Graph
      ═══════════════════════════════════════════════════════════════════ */}
      <section aria-label="Price trend graph" className="mt-12 mb-16">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="font-display text-xl uppercase tracking-tight text-white">
            Price Trend
          </h2>

          {watchlist.length > 0 && (
            <select
              id="price-trend-product-select"
              value={selectedProductId ?? ''}
              onChange={(e) => setSelectedProductId(Number(e.target.value))}
              className="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white/80 backdrop-blur-sm focus:outline-none focus:ring-2 focus:ring-accent"
            >
              {watchlist.map((w) => (
                <option key={w.productId} value={w.productId} className="bg-surface text-white">
                  {w.productName?.substring(0, 60)}
                </option>
              ))}
            </select>
          )}
        </div>

        <div className="mt-5 rounded-xl border border-white/10 bg-white/5 p-4 backdrop-blur-sm sm:p-6">
          {watchlist.length === 0 ? (
            <div className="flex h-48 items-center justify-center text-center">
              <p className="max-w-xs text-sm text-white/40">
                Add products to your watchlist to track price trends here.
              </p>
            </div>
          ) : historyLoading ? (
            <div className="flex h-64 items-center justify-center">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-white/20 border-t-accent" />
            </div>
          ) : chartData.length === 0 ? (
            <div className="flex h-48 items-center justify-center">
              <p className="text-sm text-white/40">No price history recorded yet for this product.</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={320}>
              <LineChart data={chartData} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.07)" />
                <XAxis
                  dataKey="date"
                  tick={{ fill: 'rgba(255,255,255,0.45)', fontSize: 11 }}
                  axisLine={{ stroke: 'rgba(255,255,255,0.1)' }}
                  tickLine={false}
                />
                <YAxis
                  tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}k`}
                  tick={{ fill: 'rgba(255,255,255,0.45)', fontSize: 11 }}
                  axisLine={{ stroke: 'rgba(255,255,255,0.1)' }}
                  tickLine={false}
                  width={60}
                />
                <Tooltip content={<PriceTooltip />} />
                <Legend
                  wrapperStyle={{ fontSize: 12, paddingTop: 12, color: 'rgba(255,255,255,0.6)' }}
                />
                {chartLines.map((platform) => (
                  <Line
                    key={platform}
                    type="monotone"
                    dataKey={platform}
                    name={platform}
                    stroke={platformColor(platform)}
                    strokeWidth={2}
                    dot={{ r: 3, fill: platformColor(platform) }}
                    activeDot={{ r: 5 }}
                    connectNulls
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </main>
  );
}
