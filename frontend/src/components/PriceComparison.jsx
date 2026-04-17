import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import SkeletonCard from './SkeletonCard';

function isValidUrl(url) {
  if (!url || typeof url !== 'string') return false;
  const trimmed = url.trim();
  return trimmed.startsWith('http://') || trimmed.startsWith('https://');
}

function formatINR(price) {
  if (price == null) return '—';
  const num = Number(price);
  if (Number.isNaN(num) || num <= 0) return '—';
  return `₹${num.toLocaleString('en-IN')}`;
}

export default function PriceComparison({ prices, bestPrice, productImage, status }) {
  const sorted = useMemo(() => {
    return (prices || []).slice().sort((a, b) => {
      const aFallback = a.source === 'fallback';
      const bFallback = b.source === 'fallback';
      if (aFallback && !bFallback) return 1;
      if (!aFallback && bFallback) return -1;
      return (a.price || 0) - (b.price || 0);
    });
  }, [prices]);

  if (status === 'PENDING') {
    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array(6).fill(0).map((_, i) => <SkeletonCard key={i} />)}
      </div>
    );
  }

  if (!sorted.length) return null;

  const bestPriceValue = bestPrice?.price ?? sorted.find((p) => p.source !== 'fallback' && p.price > 0)?.price;

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {sorted.map((item, i) => {
        const isFallback = item.source === 'fallback';
        const isBest =
          !isFallback &&
          item.price > 0 &&
          bestPriceValue != null &&
          Number(item.price) === Number(bestPriceValue);

        const hasValidUrl = isValidUrl(item.productUrl);

        return (
          <motion.div
            key={item.id ?? `${item.platform}-${i}`}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06 }}
            className={`group relative flex flex-col overflow-hidden rounded-xl border bg-surface-elevated p-4 backdrop-blur-sm transition-all duration-200
              ${isBest ? 'border-green-500/40 ring-1 ring-green-500/20' : 'border-white/10'}
              focus-within:outline-none focus-within:ring-2 focus-within:ring-accent`}
          >
            {isBest && (
              <span className="absolute right-2 top-2 z-10 flex items-center gap-1 rounded-full bg-green-500 px-2.5 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-white">
                <span className="inline-block h-1.5 w-1.5 rounded-full bg-white animate-pulse" />
                Best Price
              </span>
            )}

            {/* Platform header */}
            <div className="flex items-center gap-2">
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-1.5 font-display text-base uppercase tracking-wide text-white">
                  {item.platform || "Unknown"}
                </p>
                <div className="mt-0.5 text-[10px] font-medium tracking-wide">
                  {item.source === 'live' && <span className="text-emerald-400">● Live</span>}
                  {item.source === 'google-derived' && <span className="text-blue-400">● Found</span>}
                  {item.source === 'cached' && <span className="text-amber-400">● Cached</span>}
                  {isFallback && <span className="text-red-400">● N/A</span>}
                </div>
              </div>
            </div>

            {/* Price */}
            <div className="mt-3 flex-1">
              <p className="font-mono text-2xl font-semibold text-white">
                {isFallback || !item.price || item.price <= 0 ? (
                  <span className="text-base text-white/40">Not Available</span>
                ) : (
                  formatINR(item.price)
                )}
              </p>
              {/* DEBUG — remove after testing */}
              <p className="mt-1 text-xs text-yellow-400 truncate">{item.productUrl || "NO URL FROM BACKEND"}</p>
            </div>

            {/* Button */}
            <div className="mt-4">
              {hasValidUrl ? (
                <a
                  href={item.productUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={`flex w-full items-center justify-center rounded-lg px-4 py-2 text-sm font-semibold transition-all duration-200
                    ${isBest
                      ? 'bg-green-500 text-white hover:bg-green-400 shadow-md shadow-green-500/20'
                      : 'bg-accent text-surface hover:bg-accent-muted'
                    }`}
                  onClick={(e) => {
                    e.stopPropagation();
                    console.log('Opening store URL:', item.productUrl);
                  }}
                >
                  View Deal
                </a>
              ) : (
                <button
                  disabled
                  className="flex w-full cursor-not-allowed items-center justify-center rounded-lg bg-white/5 px-4 py-2 text-sm font-semibold text-white/30 opacity-50"
                >
                  Unavailable
                </button>
              )}
            </div>

            <div className="mt-3">
              {!isFallback && item.productUrl ? (
                <a
                  href={item.productUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  className="block w-full rounded-lg bg-white/10 py-2 text-center text-xs font-medium text-white hover:bg-white/20 transition-colors"
                >
                  Buy on {item.platform} ↗
                </a>
              ) : (
                <span className="block w-full rounded-lg bg-white/5 py-2 text-center text-xs text-white/30 cursor-not-allowed">
                  Not Available
                </span>
              )}
            </div>
          </motion.div>
        );
      })}
    </div>
  );
}