import { Link } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { motion } from 'framer-motion';

/** Format number as ₹ INR */
function formatINR(price) {
  if (price == null) return '—';
  const num = Number(price);
  if (Number.isNaN(num) || num <= 0) return '—';
  return `₹${num.toLocaleString('en-IN')}`;
}

export default function Watchlist() {
  const { watchlist, watchlistLoading, removeFromWatchlist, auth } = useApp();

  /* ── Not logged in ─────────────────────────────────────────────────── */
  if (!auth?.token) {
    return (
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <motion.h1
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="font-display text-2xl uppercase tracking-tight text-white sm:text-3xl"
        >
          Watchlist
        </motion.h1>

        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="mt-12 rounded-xl border border-white/10 bg-surface-elevated/50 p-12 text-center"
        >
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-accent/10 text-3xl">
            🔒
          </div>
          <p className="text-lg font-medium text-white/80">Log in to view your watchlist</p>
          <p className="mt-1 text-sm text-white/50">
            Your tracked products are saved to your account and sync across sessions.
          </p>
          <Link
            to="/auth"
            className="mt-6 inline-block rounded-lg bg-accent px-6 py-2.5 font-display text-sm uppercase tracking-wide text-surface transition hover:bg-accent-muted focus:outline-none focus:ring-2 focus:ring-accent"
          >
            Log in / Sign up
          </Link>
        </motion.div>
      </main>
    );
  }

  /* ── Loading spinner ───────────────────────────────────────────────── */
  if (watchlistLoading) {
    return (
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <motion.h1
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="font-display text-2xl uppercase tracking-tight text-white sm:text-3xl"
        >
          Watchlist
        </motion.h1>
        <div className="mt-16 flex flex-col items-center justify-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-white/20 border-t-accent" />
          <p className="text-sm text-white/60">Loading your watchlist…</p>
        </div>
      </main>
    );
  }

  /* ── Main content ──────────────────────────────────────────────────── */
  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <motion.h1
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        className="font-display text-2xl uppercase tracking-tight text-white sm:text-3xl"
      >
        Watchlist
      </motion.h1>
      <p className="mt-2 text-white/70">
        Products you're tracking. Open any to see latest prices and recommendations.
      </p>

      {watchlist.length === 0 ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="mt-12 rounded-xl border border-white/10 bg-surface-elevated/50 p-12 text-center"
        >
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-white/5 text-3xl">
            📋
          </div>
          <p className="text-white/60">No products in your watchlist yet.</p>
          <p className="mt-1 text-sm text-white/40">
            Search for a product and click "Add to watchlist" to start tracking.
          </p>
          <Link
            to="/"
            className="mt-6 inline-block rounded-lg bg-accent px-6 py-2.5 font-display text-sm uppercase tracking-wide text-surface transition hover:bg-accent-muted focus:outline-none focus:ring-2 focus:ring-accent"
          >
            Compare a product
          </Link>
        </motion.div>
      ) : (
        <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {watchlist.map((item, i) => (
            <motion.li
              key={item.id}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.06 }}
              className="group relative list-none overflow-hidden rounded-xl border border-white/10 bg-surface-card backdrop-blur-card transition hover:border-accent/30"
            >
              <Link
                to={`/product/${item.id}`}
                className="block p-4 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-accent"
              >
                <div className="flex gap-4">
                  {/* Product image */}
                  <div className="h-20 w-20 shrink-0 overflow-hidden rounded-lg bg-surface-elevated">
                    {item.imageUrl ? (
                      <img
                        src={item.imageUrl}
                        alt=""
                        className="h-full w-full object-cover transition group-hover:scale-105"
                        loading="lazy"
                        onError={(e) => {
                          e.target.style.display = 'none';
                          e.target.nextElementSibling?.classList.remove('hidden');
                        }}
                      />
                    ) : null}
                    <div className={`flex h-full w-full items-center justify-center text-2xl text-white/30 ${item.imageUrl ? 'hidden' : ''}`}>
                      🛍️
                    </div>
                  </div>

                  {/* Product info */}
                  <div className="min-w-0 flex-1">
                    <h3 className="font-display text-sm uppercase tracking-wide text-white line-clamp-2 group-hover:text-accent transition-colors">
                      {item.name}
                    </h3>
                    {item.brand && (
                      <p className="mt-0.5 text-xs text-white/50">{item.brand}</p>
                    )}
                    {item.bestPrice ? (
                      <div className="mt-2">
                        <span className="font-mono text-lg text-accent">
                          {formatINR(item.bestPrice.price)}
                        </span>
                        {item.bestPrice.platform && (
                          <span className="ml-1.5 text-xs text-white/50">
                            on {item.bestPrice.platform}
                          </span>
                        )}
                      </div>
                    ) : (
                      <p className="mt-2 text-xs text-white/40">No price data yet</p>
                    )}
                    {item.addedAt && (
                      <p className="mt-1 text-[10px] text-white/30">
                        Added {new Date(item.addedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
                      </p>
                    )}
                  </div>
                </div>

                {/* Hover hint */}
                <div className="mt-3 flex items-center gap-1 text-xs text-white/40 opacity-0 transition-opacity group-hover:opacity-100">
                  <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                  </svg>
                  View details & compare prices
                </div>
              </Link>

              {/* Remove button */}
              <button
                type="button"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  removeFromWatchlist(item.id);
                }}
                className="absolute right-2 top-2 z-10 rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-white/60 backdrop-blur-sm transition hover:bg-red-500/20 hover:text-red-400 focus:outline-none focus:ring-2 focus:ring-accent"
                aria-label={`Remove ${item.name} from watchlist`}
              >
                ✕ Remove
              </button>
            </motion.li>
          ))}
        </ul>
      )}
    </main>
  );
}
