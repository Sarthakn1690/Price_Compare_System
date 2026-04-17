import { useState, useEffect } from 'react';
import { getAnalyticsSummary, getScraperHealth } from '../services/api';
import { motion } from 'framer-motion';

export default function Analytics() {
  const [summary, setSummary] = useState(null);
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        const [sumRes, healthRes] = await Promise.all([getAnalyticsSummary(), getScraperHealth()]);
        setSummary(sumRes);
        setHealth(healthRes);
      } catch (err) {
        console.error('Failed to load analytics', err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  if (loading) return null;

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-8">
        <h1 className="font-display text-2xl uppercase tracking-tight text-white">System Analytics</h1>
        <p className="mt-2 text-white/60">Global stats and scraper health monitoring.</p>
      </header>

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: 'Total Products', value: summary?.totalProducts },
          { label: 'Watchlist Items', value: summary?.totalWatchlist },
          { label: 'Supported Platforms', value: summary?.supportedPlatforms },
          { label: 'System Status', value: summary?.systemStatus, color: 'text-success' },
        ].map((stat, i) => (
          <motion.div
            key={stat.label}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
            className="rounded-xl border border-white/10 bg-surface p-6"
          >
            <p className="text-xs font-medium uppercase tracking-wider text-white/40">{stat.label}</p>
            <p className={`mt-2 text-2xl font-bold ${stat.color || 'text-white'}`}>{stat.value}</p>
          </motion.div>
        ))}
      </div>

      <section className="mt-12">
        <h2 className="font-display text-lg uppercase tracking-wide text-white mb-6">Scraper Connectivity</h2>
        <div className="rounded-xl border border-white/10 bg-surface p-6">
          <div className="flex items-center gap-4 mb-6">
             <div className={`h-3 w-3 rounded-full ${health?.pythonServiceAvailable ? 'bg-success' : 'bg-danger'}`} />
             <span className="text-sm font-medium text-white">
                Python Playwright Service: {health?.pythonServiceAvailable ? 'Operational' : 'Unavailable'}
             </span>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
            {['amazon', 'flipkart', 'myntra', 'meesho', 'ajio', 'nykaa', 'snapdeal', 'croma'].map(p => (
               <div key={p} className="flex items-center justify-between rounded-lg bg-white/5 p-3">
                  <span className="text-xs capitalize text-white/70">{p}</span>
                  <span className="text-[10px] font-bold text-success uppercase">Active</span>
               </div>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
