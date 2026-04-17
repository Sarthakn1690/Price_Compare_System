import { useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';

/* ── Platform colors — covers all 10 supported Indian e-commerce sites ── */

const PLATFORM_COLORS = {
  Amazon:          '#f59e0b',
  Flipkart:        '#3b82f6',
  Myntra:          '#ec4899',
  Ajio:            '#64748b',
  Nykaa:           '#a855f7',
  Meesho:          '#f472b6',
  Snapdeal:        '#ef4444',
  Croma:           '#06b6d4',
  TataCliq:        '#8b5cf6',
  RelianceDigital: '#2563eb',
};

// Case-insensitive lookup
function getColor(platform) {
  if (!platform) return '#b8ff3c';
  // Try exact match first
  if (PLATFORM_COLORS[platform]) return PLATFORM_COLORS[platform];
  // Try case-insensitive
  const lower = platform.toLowerCase();
  for (const [key, val] of Object.entries(PLATFORM_COLORS)) {
    if (key.toLowerCase() === lower) return val;
  }
  return '#b8ff3c'; // accent fallback
}

/* ── Display name normalization ─────────────────────────────────────────── */

const DISPLAY_NAMES = {
  amazon: 'Amazon', flipkart: 'Flipkart', myntra: 'Myntra', ajio: 'Ajio',
  nykaa: 'Nykaa', meesho: 'Meesho', snapdeal: 'Snapdeal', croma: 'Croma',
  tatacliq: 'TataCliq', reliancedigital: 'Reliance Digital',
};

function displayName(platform) {
  if (!platform) return 'Unknown';
  const key = platform.toLowerCase().replace(/[\s_-]/g, '');
  return DISPLAY_NAMES[key] || platform;
}

/* ── Format helpers ─────────────────────────────────────────────────────── */

function formatDateShort(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' });
}

function formatINR(value) {
  if (value == null) return '';
  const num = Number(value);
  if (Number.isNaN(num)) return '';
  return `₹${num.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
}

/* ── Custom tooltip ─────────────────────────────────────────────────────── */

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-white/20 bg-surface-elevated p-3 shadow-xl backdrop-blur-sm">
      <p className="mb-2 font-mono text-xs text-white/70">{formatDateShort(label)}</p>
      {payload.map((entry) => (
        <p key={entry.dataKey} className="flex items-center gap-2 font-mono text-sm" style={{ color: entry.color }}>
          <span
            className="inline-block h-2.5 w-2.5 rounded-full"
            style={{ backgroundColor: entry.color }}
          />
          {displayName(entry.name)}: {formatINR(entry.value)}
        </p>
      ))}
    </div>
  );
}

/* ── Main chart component ───────────────────────────────────────────────── */

export default function PriceHistoryChart({ historyByPlatform, days = 14 }) {
  const { data, platforms } = useMemo(() => {
    if (!historyByPlatform || typeof historyByPlatform !== 'object') {
      return { data: [], platforms: [] };
    }

    const platforms = Object.keys(historyByPlatform);
    const byDate = {};

    platforms.forEach((platform) => {
      const points = historyByPlatform[platform] || [];
      points.forEach((point) => {
        // Normalize the date to just YYYY-MM-DD for grouping
        const rawDate = typeof point.date === 'string' ? point.date : point.date?.toString?.() ?? '';
        const dateKey = rawDate.substring(0, 10); // "2026-04-15"

        if (!byDate[dateKey]) {
          byDate[dateKey] = { date: rawDate };
        }
        byDate[dateKey][platform] = point.price;
      });
    });

    const data = Object.values(byDate).sort(
      (a, b) => new Date(a.date) - new Date(b.date),
    );

    return { data, platforms };
  }, [historyByPlatform]);

  /* ── Empty state ── */
  if (data.length === 0) {
    return (
      <div className="flex min-h-[280px] flex-col items-center justify-center gap-3 rounded-xl border border-white/10 bg-surface-elevated/50 p-8 text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-white/5 text-xl">
          📈
        </div>
        <p className="text-sm font-medium text-white/60">
          Price history will appear here once we have tracking data
        </p>
        <p className="text-xs text-white/40">
          Prices are tracked automatically every 6 hours. Check back soon!
        </p>
      </div>
    );
  }

  /* ── Chart ── */
  return (
    <div className="h-[320px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />

          <XAxis
            dataKey="date"
            tickFormatter={formatDateShort}
            stroke="rgba(255,255,255,0.4)"
            tick={{ fontSize: 11, fill: 'rgba(255,255,255,0.5)' }}
            tickLine={{ stroke: 'rgba(255,255,255,0.1)' }}
            axisLine={{ stroke: 'rgba(255,255,255,0.1)' }}
          />

          <YAxis
            tickFormatter={formatINR}
            stroke="rgba(255,255,255,0.4)"
            tick={{ fontSize: 11, fill: 'rgba(255,255,255,0.5)' }}
            tickLine={{ stroke: 'rgba(255,255,255,0.1)' }}
            axisLine={{ stroke: 'rgba(255,255,255,0.1)' }}
            width={70}
          />

          <Tooltip
            content={<CustomTooltip />}
            cursor={{ stroke: 'rgba(255,255,255,0.15)', strokeWidth: 1 }}
          />

          <Legend
            formatter={(value) => displayName(value)}
            wrapperStyle={{ fontSize: 12, color: 'rgba(255,255,255,0.7)', paddingTop: '8px' }}
            iconType="circle"
            iconSize={8}
          />

          {platforms.map((platform) => (
            <Line
              key={platform}
              type="monotone"
              dataKey={platform}
              name={platform}
              stroke={getColor(platform)}
              strokeWidth={2}
              dot={{ r: 3, fill: getColor(platform), strokeWidth: 0 }}
              activeDot={{ r: 5, fill: getColor(platform), stroke: '#fff', strokeWidth: 2 }}
              connectNulls
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
