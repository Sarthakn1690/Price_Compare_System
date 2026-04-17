import { motion } from 'framer-motion';

const PLATFORMS = [
  { id: 'amazon', name: 'Amazon India', icon: '🛒', cat: 'General' },
  { id: 'flipkart', name: 'Flipkart', icon: '📦', cat: 'General' },
  { id: 'myntra', name: 'Myntra', icon: '👗', cat: 'Fashion' },
  { id: 'ajio', name: 'Ajio', icon: '👟', cat: 'Fashion' },
  { id: 'nykaa', name: 'Nykaa', icon: '💄', cat: 'Beauty' },
  { id: 'meesho', name: 'Meesho', icon: '🛍️', cat: 'Affordable' },
  { id: 'snapdeal', name: 'Snapdeal', icon: '⚡', cat: 'General' },
  { id: 'croma', name: 'Croma', icon: '💻', cat: 'Electronics' },
];

export default function Platforms() {
  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-12 text-center">
        <h1 className="font-display text-3xl uppercase tracking-tight text-white sm:text-4xl">Supported Platforms</h1>
        <p className="mt-4 text-white/60">We support 8 major e-commerce destinations in India.</p>
      </header>

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {PLATFORMS.map((p, i) => (
          <motion.div
            key={p.id}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: i * 0.05 }}
            className="flex flex-col items-center rounded-2xl border border-white/10 bg-surface p-8 text-center transition-transform hover:scale-[1.02]"
          >
            <span className="text-4xl">{p.icon}</span>
            <h3 className="mt-4 text-lg font-bold text-white">{p.name}</h3>
            <span className="mt-2 text-xs font-semibold uppercase tracking-widest text-accent/70">{p.cat}</span>
            <div className="mt-6 flex items-center gap-2 text-[10px] font-bold uppercase text-success">
               <div className="h-1.5 w-1.5 rounded-full bg-success" />
               Scraper Active
            </div>
          </motion.div>
        ))}
      </div>
    </main>
  );
}
