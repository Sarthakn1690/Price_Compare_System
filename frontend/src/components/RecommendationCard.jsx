import { motion } from 'framer-motion';

const RECOMMENDATION_CONFIG = {
  BUY_NOW: {
    label: 'Buy Now',
    className: 'border-success/50 bg-success/10 text-success',
    icon: '✓',
  },
  WAIT_FOR_BETTER_PRICE: {
    label: 'Wait for Better Price',
    className: 'border-warning/50 bg-warning/10 text-warning',
    icon: '⏳',
  },
  PRICE_INCREASING: {
    label: 'Price Increasing',
    className: 'border-danger/50 bg-danger/10 text-danger',
    icon: '↑',
  },
};

export default function RecommendationCard({ recommendation }) {
  if (!recommendation) return null;

  const type = recommendation.recommendation || 'BUY_NOW';
  const config = RECOMMENDATION_CONFIG[type] || RECOMMENDATION_CONFIG.BUY_NOW;
  const confidence = recommendation.confidenceScore ?? 0;

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      className={`rounded-xl border p-4 backdrop-blur-sm ${config.className}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-2xl" aria-hidden>{config.icon}</span>
        <span className="font-display text-sm uppercase tracking-wide">
          {config.label}
        </span>
      </div>
      <p className="mt-2 text-sm opacity-90">
        {recommendation.explanation}
      </p>
      <div className="mt-3 flex items-center gap-2">
        <span className="font-mono text-xs opacity-75">
          {confidence}% confident
        </span>
        {recommendation.predictedTrend && (
          <span className="text-xs opacity-75">
            · Trend: {recommendation.predictedTrend}
          </span>
        )}
      </div>
    </motion.div>
  );
}
