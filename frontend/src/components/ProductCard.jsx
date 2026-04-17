import { Link } from 'react-router-dom';
import { formatPrice, platformDisplayName } from '../utils/helpers';
import { motion } from 'framer-motion';
import { useState } from 'react';

export default function ProductCard({ product, index = 0 }) {
  const bestPrice = product.bestPrice || product.prices?.[0];
  const imageUrl = product.imageUrl || '';
  const [imgError, setImgError] = useState(false);

  return (
    <motion.article
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.06 }}
    >
      <Link
        to={`/product/${product.id}`}
        className="group block overflow-hidden rounded-xl border border-white/10 bg-surface-card p-4 backdrop-blur-card transition hover:border-accent/30 focus:outline-none focus:ring-2 focus:ring-accent"
      >
        <div className="flex gap-4">
          <div className="h-24 w-24 shrink-0 overflow-hidden rounded-lg bg-surface-elevated">
            {imageUrl && !imgError ? (
              <img
                src={imageUrl}
                alt=""
                className="h-full w-full object-cover transition group-hover:scale-105 bg-white/5"
                onError={() => setImgError(true)}
              />
            ) : (
              <div className="flex h-full w-full flex-col items-center justify-center bg-white/5 text-white/40">
                <span className="text-2xl mb-1">🛍️</span>
                <span className="text-xs font-bold tracking-wider">{product?.name?.substring(0, 2).toUpperCase()}</span>
              </div>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="font-display text-sm uppercase tracking-wide text-white line-clamp-2 group-hover:text-accent">
              {product.name}
            </h3>
            {product.brand && (
              <p className="mt-0.5 text-xs text-white/60">{product.brand}</p>
            )}
            {bestPrice && (
              <p className="mt-2 font-mono text-lg text-accent">
                {formatPrice(bestPrice.price)} on {platformDisplayName(bestPrice.platform)}
              </p>
            )}
          </div>
        </div>
      </Link>
    </motion.article>
  );
}
