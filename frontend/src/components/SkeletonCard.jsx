import { motion } from 'framer-motion';

export default function SkeletonCard() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="flex flex-col overflow-hidden rounded-xl border border-white/10 bg-surface/50 p-4"
    >
      <div className="flex items-center gap-2">
        {/* Placeholder for Product Image */}
        <div className="h-10 w-10 shrink-0 animate-pulse rounded-lg bg-white/10" />
        <div className="flex-1 space-y-2">
          {/* Placeholder for Platform Name */}
          <div className="h-4 w-24 animate-pulse rounded bg-white/10" />
          {/* Placeholder for Source Badge */}
          <div className="h-2 w-12 animate-pulse rounded bg-white/5" />
        </div>
      </div>
      
      <div className="mt-3 flex-1 space-y-2">
        {/* Placeholder for Price */}
        <div className="h-8 w-32 animate-pulse rounded bg-white/10" />
      </div>

      <div className="mt-3">
        {/* Placeholder for Button */}
        <div className="h-9 w-full animate-pulse rounded-lg bg-white/5" />
      </div>
    </motion.div>
  );
}
