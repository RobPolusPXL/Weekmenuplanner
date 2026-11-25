// Lokale parser voor ingrediëntregels (geen AI), gelijk aan IngredientParser in de Android-app.
//  "150 gram gehakt" → gehakt, 150, g     "1,5 kg patatten" → aardappel, 1500, g
//  "2 uien" → ui, 2, stuk                 "snuf zout" → zout, geen hoeveelheid
import { toCanonical } from './units.js';

const UNIT_WORDS = new Set([
  'kg', 'kilogram', 'kilo', 'g', 'gr', 'gram',
  'l', 'liter', 'dl', 'deciliter', 'cl', 'centiliter', 'ml', 'milliliter',
  'stuk', 'stuks', 'st',
  'el', 'eetlepel', 'eetlepels',
  'tl', 'kl', 'theelepel', 'theelepels', 'koffielepel',
  'teen', 'tenen', 'teentje', 'teentjes',
  'blik', 'blikken', 'blikje',
  'pak', 'pakken', 'pakje',
  'bos', 'bosje', 'bosjes', 'bussel',
  'plak', 'plakken', 'plakje', 'plakjes',
  'snee', 'sneden', 'sneetje', 'sneetjes',
  'pot', 'potten', 'potje', 'potjes',
  'zak', 'zakje', 'zakjes',
]);

const NO_QUANTITY_WORDS = new Set(['snuf', 'snufje', 'scheut', 'scheutje', 'beetje', 'handje', 'handvol', 'mespuntje']);

const SINGULARS = {
  uien: 'ui', tomaten: 'tomaat', aardappelen: 'aardappel', aardappels: 'aardappel', patatten: 'aardappel',
  eieren: 'ei', eitjes: 'ei', wortelen: 'wortel', wortels: 'wortel', "paprika's": 'paprika', paprikas: 'paprika',
  champignons: 'champignon', courgettes: 'courgette', preien: 'prei', citroenen: 'citroen', appels: 'appel',
  appelen: 'appel', bananen: 'banaan', komkommers: 'komkommer', sjalotten: 'sjalot', look: 'knoflook',
};

const LEADING_NUMBER = /^(\d+(?:[.,]\d+)?(?:\s*\/\s*\d+)?)\s*(\p{L}[\p{L}']*)?\s*([\s\S]*)$/u;

function parseNumber(text) {
  const t = text.replace(/\s/g, '');
  if (t.includes('/')) {
    const [a, b] = t.split('/');
    const num = Number(a.replace(',', '.'));
    const den = Number((b ?? '').replace(',', '.'));
    if (!Number.isFinite(num) || !Number.isFinite(den) || den === 0) return null;
    return num / den;
  }
  const n = Number(t.replace(',', '.'));
  return Number.isFinite(n) ? n : null;
}

function singularizeLastWord(name) {
  if (!name) return name;
  const words = name.split(' ');
  const singular = SINGULARS[words[words.length - 1]];
  if (!singular) return name;
  return [...words.slice(0, -1), singular].join(' ');
}

const stripPrefix = (s, p) => (s.startsWith(p) ? s.slice(p.length) : s);

/** → { raw, name, quantity, unit, needsNormalization } (zelfde velden als in Firestore). */
export function parseIngredient(rawLine) {
  const raw = String(rawLine ?? '').trim();
  const cleaned = raw.toLowerCase()
    .replace(/\([^)]*\)/g, ' ')
    .replaceAll('naar smaak', ' ')
    .replace(/\s+/g, ' ')
    .trim();

  let quantity = null;
  let unitWord = null;
  let nameText = cleaned;

  const match = LEADING_NUMBER.exec(cleaned);
  const number = match ? parseNumber(match[1]) : null;
  if (match && number != null) {
    quantity = number;
    const word = match[2];
    const rest = match[3].trim();
    if (word && UNIT_WORDS.has(word)) {
      unitWord = word;
      nameText = rest;
    } else {
      unitWord = 'stuk';
      nameText = [word, rest || null].filter(Boolean).join(' ');
    }
  } else {
    const words = cleaned.split(' ');
    if (words.length > 1 && NO_QUANTITY_WORDS.has(words[0])) nameText = words.slice(1).join(' ');
  }

  let base = stripPrefix(stripPrefix(nameText, 'van '), 'aan ').trim().replace(/[,.;]+$/, '');
  const name = singularizeLastWord(base);

  if (!name) {
    return { raw, name: cleaned || raw, quantity: null, unit: null, needsNormalization: false };
  }

  const conv = toCanonical(quantity, unitWord === 'kilo' ? 'kg' : unitWord);
  return {
    raw,
    name,
    quantity: conv.quantity,
    unit: conv.quantity != null ? conv.unit : null,
    needsNormalization: false,
  };
}
