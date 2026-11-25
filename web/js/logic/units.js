// Eenheden omrekenen en hoeveelheden tonen, zoals MealPlannerLogic in de Android-app.

const UNIT_MAP = {
  kilogram: 'g', kg: 'g',
  gram: 'g', gr: 'g', g: 'g',
  liter: 'ml', l: 'ml',
  deciliter: 'ml', dl: 'ml',
  centiliter: 'ml', cl: 'ml',
  milliliter: 'ml', ml: 'ml',
  stuks: 'stuk', stuk: 'stuk', st: 'stuk',
  eetlepel: 'el', eetlepels: 'el', el: 'el',
  theelepel: 'tl', theelepels: 'tl', tl: 'tl', kl: 'tl', koffielepel: 'tl',
  teentje: 'teen', teentjes: 'teen', teen: 'teen', tenen: 'teen',
  blikken: 'blik', blik: 'blik', blikje: 'blik',
  pakken: 'pak', pak: 'pak', pakje: 'pak',
  bosje: 'bos', bosjes: 'bos', bos: 'bos', bussel: 'bos',
  plakken: 'plak', plakje: 'plak', plakjes: 'plak', plak: 'plak',
  sneden: 'snee', sneetje: 'snee', sneetjes: 'snee', snee: 'snee',
  potten: 'pot', potje: 'pot', potjes: 'pot', pot: 'pot',
  zakjes: 'zakje', zak: 'zakje', zakje: 'zakje',
};

const FACTOR = { kg: 1000, kilogram: 1000, l: 1000, liter: 1000, dl: 100, deciliter: 100, cl: 10, centiliter: 10 };

/** { quantity, unit } in de basiseenheid (g, ml, stuk, el, …). */
export function toCanonical(quantity, rawUnit) {
  const clean = (rawUnit ?? '').trim().toLowerCase() || null;
  const unit = clean == null ? null : (UNIT_MAP[clean] ?? clean);
  if (quantity == null) return { quantity: null, unit };
  return { quantity: quantity * (FACTOR[clean] ?? 1), unit };
}

/** 1500 → "1,5"; 2 → "2"; maximaal twee decimalen. */
export function formatNumber(value) {
  const whole = Math.trunc(value);
  if (Math.abs(value - whole) < 0.001) return String(whole);
  return (Math.round(value * 100) / 100).toString().replace('.', ',');
}

/** 1500 g → "1,5 kg"; 250 ml → "250 ml"; zonder eenheid enkel het getal. */
export function formatQuantity(quantity, unit) {
  if (quantity == null) return '';
  let q = quantity;
  let u = unit ? unit.trim().toLowerCase() : null;
  if (u === 'g' && q >= 1000) { q /= 1000; u = 'kg'; }
  else if (u === 'ml' && q >= 1000) { q /= 1000; u = 'l'; }
  const num = formatNumber(q);
  return u ? `${num} ${u}` : num;
}

/** "1,5" of "250" → getal ≥ 0, anders null. */
export function parseAmount(text) {
  const t = String(text ?? '').trim().replace(',', '.');
  if (t === '') return null;
  const n = Number(t);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

export function round3(v) {
  return Math.round(v * 1000) / 1000;
}

/** 25 → "25 min"; 75 → "1 u 15 min". */
export function formatPrepTime(minutes) {
  if (minutes < 60) return `${minutes} min`;
  if (minutes % 60 === 0) return `${minutes / 60} u`;
  return `${Math.floor(minutes / 60)} u ${minutes % 60} min`;
}

export const capitalize = s => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);
