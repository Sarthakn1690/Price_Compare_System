const PLATFORM_PATTERNS = [
  { name: 'amazon', test: (url) => /amazon\./i.test(url) },
  { name: 'flipkart', test: (url) => /flipkart\./i.test(url) },
  { name: 'myntra', test: (url) => /myntra\./i.test(url) },
  { name: 'ajio', test: (url) => /ajio\./i.test(url) },
  { name: 'nykaa', test: (url) => /nykaa\./i.test(url) },
  { name: 'meesho', test: (url) => /meesho\./i.test(url) },
  { name: 'snapdeal', test: (url) => /snapdeal\./i.test(url) },
  { name: 'croma', test: (url) => /croma\./i.test(url) },
];

export function detectPlatformFromUrl(url) {
  if (!url || typeof url !== 'string') return null;
  const normalized = url.trim();
  const found = PLATFORM_PATTERNS.find((p) => p.test(normalized));
  return found ? found.name : null;
}

export function isValidProductUrl(url) {
  if (!url || typeof url !== 'string') return false;
  const trimmed = url.trim();
  try {
    new URL(trimmed);
  } catch {
    return false;
  }
  return PLATFORM_PATTERNS.some((p) => p.test(trimmed));
}

export function formatPrice(price, currency = 'INR') {
  if (price == null) return '—';
  const num = Number(price);
  if (Number.isNaN(num)) return '—';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
    minimumFractionDigits: 0,
  }).format(num);
}

export function formatDate(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export function platformDisplayName(platform) {
  if (!platform) return 'Unknown';
  return platform.charAt(0).toUpperCase() + platform.slice(1).toLowerCase();
}
