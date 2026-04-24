import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { searchProduct } from '../services/api';
import { useApp } from '../context/AppContext';
import SearchBar from '../components/SearchBar';
import ProductDetails from '../components/ProductDetails';
import { motion, AnimatePresence } from 'framer-motion';

/* ── Sample products for quick demo ─────────────────────────────────────── */

const SAMPLE_PRODUCTS = [
  {
    name: 'iPhone 15',
    emoji: '📱',
    category: 'Smartphones',
    gradient: 'from-blue-500/20 to-indigo-600/20 border-blue-500/30',
  },
  {
    name: 'Samsung 55 inch TV',
    emoji: '📺',
    category: 'Electronics',
    gradient: 'from-violet-500/20 to-purple-600/20 border-violet-500/30',
  },
  {
    name: 'Nike Air Max shoes',
    emoji: '👟',
    category: 'Footwear',
    gradient: 'from-orange-500/20 to-red-600/20 border-orange-500/30',
  },
  {
    name: 'Sony WH-1000XM5 headphones',
    emoji: '🎧',
    category: 'Audio',
    gradient: 'from-emerald-500/20 to-teal-600/20 border-emerald-500/30',
  },
  {
    name: 'Lakme lipstick',
    emoji: '💄',
    category: 'Beauty',
    gradient: 'from-pink-500/20 to-rose-600/20 border-pink-500/30',
  },
  {
    name: 'Levi\'s jeans',
    emoji: '👖',
    category: 'Fashion',
    gradient: 'from-amber-500/20 to-yellow-600/20 border-amber-500/30',
  },
];

/* ── Loading overlay ────────────────────────────────────────────────────── */

function SearchingOverlay() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="mt-12 flex flex-col items-center gap-4 rounded-xl border border-white/10 bg-surface-elevated/50 p-12"
    >
      <div className="relative">
        <div className="h-14 w-14 animate-spin rounded-full border-4 border-white/10 border-t-accent" />
        <div className="absolute inset-0 flex items-center justify-center text-xl">🔍</div>
      </div>
      <p className="font-display text-sm uppercase tracking-wider text-white/80">
        Searching across platforms…
      </p>
      <p className="text-xs text-white/40">
        Checking Amazon, Flipkart, Myntra, Croma, and 6 more stores
      </p>
      <div className="mt-2 flex gap-1">
        {[0, 1, 2].map((i) => (
          <motion.div
            key={i}
            className="h-1.5 w-1.5 rounded-full bg-accent"
            animate={{ opacity: [0.3, 1, 0.3] }}
            transition={{ duration: 1.2, repeat: Infinity, delay: i * 0.2 }}
          />
        ))}
      </div>
    </motion.div>
  );
}

/* ── Main Home page ─────────────────────────────────────────────────────── */

export default function Home() {
  const navigate = useNavigate();
  const { showToast, addToWatchlist, isInWatchlist } = useApp();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [product, setProduct] = useState(null);


  const handleSearch = useCallback(async (query) => {
    setError(null);
    setProduct(null);
    setLoading(true);
    try {
      const data = await searchProduct(query);

      // If the response has an ID, we can navigate to the full product page
      if (data?.id) {
        setProduct(data);
        showToast(`Found prices from ${data.prices?.filter(p => p.source !== 'fallback').length || 0} platforms`, 'success');

        // Scroll to results
        setTimeout(() => {
          document.getElementById('result')?.scrollIntoView({ behavior: 'smooth' });
        }, 200);
      } else {
        setProduct(data);
        showToast('Product loaded. View comparison below.', 'success');
      }
    } catch (err) {
      const message = err.response?.data?.error || err.response?.data?.message || err.message || 'Search failed';
      setError(message);
      showToast('Product not found. Try a different search term.', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  const handleSampleClick = useCallback((productName) => {
    // Fill search bar and auto-search
    handleSearch(productName);
  }, [handleSearch]);

  const handleAddToWatchlist = useCallback(() => {
    if (!product) return;
    addToWatchlist({ id: product.id, name: product.name, imageUrl: product.imageUrl, bestPrice: product.bestPrice });
    showToast('Added to watchlist', 'success');
  }, [product, addToWatchlist, showToast]);

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* ── Hero section ── */}
      <motion.section
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="flex flex-col items-center gap-6 text-center"
      >
        <motion.h1
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="font-display text-3xl uppercase tracking-tight text-white sm:text-4xl md:text-5xl"
        >
          Compare prices<span className="text-accent"></span>
        </motion.h1>
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.2 }}
          className="max-w-xl text-white/70"
        >
          Search for any product by name to compare prices across Amazon, Flipkart, Myntra,
          Ajio, Nykaa, Meesho, Snapdeal, Croma, TataCliq, and Reliance Digital.
        </motion.p>
        <SearchBar onSearch={handleSearch} loading={loading} error={error} />
      </motion.section>

      {/* ── Loading state ── */}
      <AnimatePresence>
        {loading && !product && <SearchingOverlay />}
      </AnimatePresence>

      {/* ── Search results ── */}
      <AnimatePresence>
        {product && (
          <motion.section
            id="result"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="mt-12"
          >
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-xl uppercase tracking-wide text-white">Result</h2>
              {product.id && (
                <button
                  type="button"
                  onClick={() => navigate(`/product/${product.id}`)}
                  className="flex items-center gap-1 text-sm font-medium text-accent transition hover:underline focus:outline-none focus:ring-2 focus:ring-accent"
                >
                  View full details
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                  </svg>
                </button>
              )}
            </div>
            <ProductDetails
              product={product}
              history={null}
              recommendation={null}
              onAddToWatchlist={handleAddToWatchlist}
              isInWatchlist={isInWatchlist(product?.id)}
            />
          </motion.section>
        )}
      </AnimatePresence>

      {/* ── Sample products grid (shown when no search result is displayed) ── */}
      {!product && !loading && (
        <motion.section
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="mt-16"
        >
          <h2 className="mb-1 text-center font-display text-lg uppercase tracking-wide text-white/80">
            Popular searches
          </h2>
          <p className="mb-6 text-center text-sm text-white/40">
            Click any product to instantly compare prices
          </p>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {SAMPLE_PRODUCTS.map((sample, i) => (
              <motion.button
                key={sample.name}
                type="button"
                onClick={() => handleSampleClick(sample.name)}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.5 + i * 0.08 }}
                whileHover={{ scale: 1.03, y: -2 }}
                whileTap={{ scale: 0.98 }}
                className={`group flex items-center gap-4 rounded-xl border bg-gradient-to-br ${sample.gradient} p-4 text-left backdrop-blur-sm transition-all hover:shadow-lg hover:shadow-accent/10 hover:border-white/30 focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface`}
              >
                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-white/10 text-2xl transition-transform group-hover:scale-110">
                  {sample.emoji}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-display text-sm uppercase tracking-wide text-white group-hover:text-accent transition-colors">
                    {sample.name}
                  </p>
                  <p className="mt-0.5 text-xs text-white/40">{sample.category}</p>
                </div>
                <svg className="h-4 w-4 shrink-0 text-white/30 transition-all group-hover:text-accent group-hover:translate-x-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </motion.button>
            ))}
          </div>
        </motion.section>
      )}
    </main>
  );
}
