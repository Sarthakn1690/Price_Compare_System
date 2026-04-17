import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getProduct, getHistory, getRecommendation, trackProduct, setAlert } from '../services/api';
import { useApp } from '../context/AppContext';
import ProductDetails from '../components/ProductDetails';
import { AnimatePresence, motion } from 'framer-motion';

export default function ProductView() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { showToast, addToWatchlist, removeFromWatchlist, isInWatchlist } = useApp();
  const [product, setProduct] = useState(null);
  const [history, setHistory] = useState(null);
  const [recommendation, setRecommendation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isAlertOpen, setIsAlertOpen] = useState(false);
  const [targetPrice, setTargetPrice] = useState('');

  const handleSetAlert = async () => {
    if (!targetPrice || isNaN(targetPrice)) return;
    try {
      await setAlert(id, parseFloat(targetPrice));
      showToast('Price alert set successfully', 'success');
      setIsAlertOpen(false);
      setTargetPrice('');
    } catch (err) {
      showToast('Failed to set alert', 'error');
    }
  };

  const handleWatchlist = () => {
    if (!product) return;
    if (isInWatchlist(id)) {
      removeFromWatchlist(id);
      showToast('Removed from watchlist');
    } else {
      addToWatchlist(product);
      showToast('Added to watchlist', 'success');
    }
  };
  const [error, setError] = useState(null);
  const [historyDays, setHistoryDays] = useState(14);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [productRes, historyRes, recRes] = await Promise.all([
        getProduct(id),
        getHistory(id, historyDays),
        getRecommendation(id),
      ]);
      setProduct(productRes);
      setHistory(historyRes);
      setRecommendation(recRes);
    } catch (err) {
      const message = err.response?.data?.error || err.message || 'Failed to load product';
      setError(message);
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  }, [id, historyDays, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!id || !product || product.comparisonStatus !== 'PENDING') return undefined;
    const timer = setInterval(async () => {
      try {
        const next = await getProduct(id);
        setProduct(next);
        if (next.comparisonStatus && next.comparisonStatus !== 'PENDING') {
          const [historyRes, recRes] = await Promise.all([
            getHistory(id, historyDays),
            getRecommendation(id),
          ]);
          setHistory(historyRes);
          setRecommendation(recRes);
          showToast(`Comparison ${next.comparisonStatus.toLowerCase()}`, 'success');
        }
      } catch {
        // keep polling on transient errors
      }
    }, 4000);
    return () => clearInterval(timer);
  }, [id, product, historyDays, showToast]);

  const handleAddToWatchlist = useCallback(async () => {
    if (!product) return;
    try {
      await trackProduct(product.id);
      addToWatchlist({ id: product.id, name: product.name, imageUrl: product.imageUrl, bestPrice: product.bestPrice });
      showToast('Added to watchlist');
    } catch (err) {
      showToast(err.response?.data?.error || 'Could not add to watchlist', 'error');
    }
  }, [product, addToWatchlist, showToast]);

  if (loading && !product) {
    return (
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="flex min-h-[40vh] items-center justify-center">
          <div className="h-10 w-10 animate-spin rounded-full border-2 border-accent border-t-transparent" aria-hidden />
        </div>
      </main>
    );
  }

  if (error && !product) {
    return (
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="rounded-xl border border-danger/30 bg-danger/10 p-6 text-center"
        >
          <p className="text-danger">{error}</p>
          <button
            type="button"
            onClick={() => navigate('/')}
            className="mt-4 rounded-lg bg-white/10 px-4 py-2 text-sm hover:bg-white/20 focus:outline-none focus:ring-2 focus:ring-accent"
          >
            Back to search
          </button>
        </motion.div>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="mb-6 text-sm text-white/70 hover:text-accent focus:outline-none focus:ring-2 focus:ring-accent"
        >
          ← Back
        </button>
        <div className="mb-6 flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm text-white/50">History:</span>
          {[7, 14].map((d) => (
            <button
              key={d}
              type="button"
              onClick={() => setHistoryDays(d)}
              className={`rounded px-3 py-1 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-accent ${
                historyDays === d ? 'bg-accent text-surface' : 'bg-white/10 text-white/80 hover:bg-white/20'
              }`}
            >
              {d} days
            </button>
          ))}
        </div>
        {product && (
          <>
            {product.comparisonStatus === 'PENDING' && (
              <div className="mb-4 rounded-lg border border-accent/20 bg-accent/10 px-4 py-2 text-sm text-accent">
                Comparing prices in real-time...
              </div>
            )}
            {historyDays !== 14 && (
              <div className="mb-4">
                <button
                  type="button"
                  onClick={() => load()}
                  className="text-sm text-accent hover:underline"
                >
                  Refresh history
                </button>
              </div>
            )}
            <div className="mt-8 flex flex-wrap gap-4">
              <button
                onClick={handleWatchlist}
                className="btn-accent px-8"
              >
                {isInWatchlist(id) ? 'In Watchlist' : 'Add to Watchlist'}
              </button>
              <button
                 onClick={() => setIsAlertOpen(true)}
                 className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-8 py-3 font-semibold text-white transition hover:bg-white/10"
              >
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                Set Price Alert
              </button>
            </div>

            <AnimatePresence>
               {isAlertOpen && (
                 <motion.div
                   initial={{ opacity: 0, scale: 0.9, y: 10 }}
                   animate={{ opacity: 1, scale: 1, y: 0 }}
                   exit={{ opacity: 0, scale: 0.9, y: 10 }}
                   className="mt-6 max-w-sm rounded-2xl border border-accent/20 bg-accent/5 p-6 backdrop-blur-xl"
                 >
                   <h3 className="font-display text-sm uppercase tracking-wider text-accent">Notify me when</h3>
                   <div className="mt-4 flex gap-2">
                      <div className="relative flex-1">
                        <span className="absolute left-4 top-1/2 -translate-y-1/2 text-white/40">₹</span>
                        <input
                          type="number"
                          value={targetPrice}
                          onChange={(e) => setTargetPrice(e.target.value)}
                          placeholder="Target price"
                          className="w-full rounded-xl border border-white/10 bg-surface pl-8 pr-4 py-2 text-white focus:border-accent focus:outline-none"
                        />
                      </div>
                      <button onClick={handleSetAlert} className="btn-accent px-4 py-2 text-xs">Set Alert</button>
                      <button onClick={() => setIsAlertOpen(false)} className="text-white/40 hover:text-white px-2">✕</button>
                   </div>
                   <p className="mt-2 text-[10px] text-white/40 italic">We'll track this product across all platforms for you.</p>
                 </motion.div>
               )}
            </AnimatePresence>
            <ProductDetails
              product={product}
              history={history}
              recommendation={recommendation}
              historyDays={historyDays}
              onAddToWatchlist={handleAddToWatchlist}
              isInWatchlist={isInWatchlist(product.id)}
            />
          </>
        )}
      </motion.div>
    </main>
  );
}
