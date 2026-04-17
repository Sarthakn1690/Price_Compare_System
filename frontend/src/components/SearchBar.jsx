import { useState, useCallback } from 'react';
import { motion } from 'framer-motion';

export default function SearchBar({ onSearch, loading, error }) {
  const [query, setQuery] = useState('');
  const valid = query.trim().length >= 2;

  const handleSubmit = useCallback(
    (e) => {
      e.preventDefault();
      if (valid && onSearch) onSearch(query.trim());
    },
    [query, valid, onSearch]
  );

  return (
    <motion.form
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
      onSubmit={handleSubmit}
      className="w-full max-w-2xl"
    >
      <div className="relative flex flex-col gap-2 sm:flex-row">
        <div className="relative flex flex-1 items-center gap-2 rounded-lg border border-white/20 bg-surface-elevated focus-within:border-accent focus-within:ring-1 focus-within:ring-accent">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search for a product (e.g., iPhone 15, Nike shoes…)"
            aria-label="Product search"
            aria-invalid={!!error}
            aria-describedby={error ? 'search-error' : undefined}
            className="min-w-0 flex-1 rounded-lg border-0 bg-transparent px-4 py-3 font-mono text-sm text-white placeholder-white/40 focus:outline-none"
            disabled={loading}
          />
        </div>
        <button
          type="submit"
          disabled={!valid || loading}
          className="rounded-lg bg-accent px-6 py-3 font-display text-sm uppercase tracking-wide text-surface transition hover:bg-accent-muted disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-surface border-t-transparent" />
              Compare
            </span>
          ) : (
            'Compare'
          )}
        </button>
      </div>
      {error && (
        <p id="search-error" className="mt-2 text-sm text-danger" role="alert">
          {error}
        </p>
      )}
    </motion.form>
  );
}
