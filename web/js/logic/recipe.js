// Recept uit de schema.org/Recipe JSON-LD van een receptpagina, gelijk aan RecipeJsonLdParser in de Android-app.

const SCRIPT_RE = /<script[^>]*type\s*=\s*["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
const DURATION_RE = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:\d+S)?)?$/i;

/** → { name, ingredientLines, totalMinutes } of null. */
export function parseRecipeHtml(html) {
  for (const m of String(html).matchAll(SCRIPT_RE)) {
    const json = m[1].trim();
    if (!json) continue;
    let root;
    try { root = JSON.parse(json); } catch { continue; }
    const recipe = findRecipe(root, 0);
    if (!recipe) continue;
    const lines = readIngredients(recipe);
    if (!lines.length) continue;
    return {
      name: cleanText(typeof recipe.name === 'string' ? recipe.name : '') || null,
      ingredientLines: lines,
      totalMinutes: readMinutes(recipe),
    };
  }
  return null;
}

function findRecipe(node, depth) {
  if (depth > 6 || node == null || typeof node !== 'object') return null;
  if (Array.isArray(node)) {
    for (const child of node) {
      const r = findRecipe(child, depth + 1);
      if (r) return r;
    }
    return null;
  }
  if (isRecipeType(node['@type'])) return node;
  for (const child of Object.values(node)) {
    if (child && typeof child === 'object') {
      const r = findRecipe(child, depth + 1);
      if (r) return r;
    }
  }
  return null;
}

function isRecipeType(type) {
  if (typeof type === 'string') return type.toLowerCase() === 'recipe';
  if (Array.isArray(type)) return type.some(t => typeof t === 'string' && t.toLowerCase() === 'recipe');
  return false;
}

function readIngredients(recipe) {
  const src = recipe.recipeIngredient ?? recipe.ingredients;
  const raw = Array.isArray(src) ? src.map(x => (typeof x === 'string' ? x : '')) : typeof src === 'string' ? src.split('\n') : [];
  return raw.map(cleanText).filter(Boolean);
}

function readMinutes(recipe) {
  const total = parseIsoDurationMinutes(recipe.totalTime ?? '');
  if (total) return total;
  const sum = (parseIsoDurationMinutes(recipe.prepTime ?? '') ?? 0) + (parseIsoDurationMinutes(recipe.cookTime ?? '') ?? 0);
  return sum > 0 ? sum : null;
}

/** "PT1H15M" → 75; null als er geen tijd in zit. */
export function parseIsoDurationMinutes(text) {
  const m = DURATION_RE.exec(String(text).trim());
  if (!m) return null;
  const total = (Number(m[1] || 0) * 24 + Number(m[2] || 0)) * 60 + Number(m[3] || 0);
  return total > 0 ? total : null;
}

function cleanText(input) {
  let s = String(input).replace(/<[^>]*>/g, ' ');
  s = s.replace(/&#(x?)([0-9a-fA-F]+);/g, (all, hex, code) => {
    const n = parseInt(code, hex ? 16 : 10);
    return n >= 1 && n <= 0x10ffff ? String.fromCodePoint(n) : all;
  });
  s = s.replaceAll('&nbsp;', ' ').replaceAll('&quot;', '"').replaceAll('&apos;', "'")
    .replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&');
  return s.replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
}

export function normalizeUrl(raw) {
  const t = String(raw ?? '').trim();
  if (!t || t.includes(' ')) return null;
  let u;
  if (/^https:\/\//i.test(t)) u = t;
  else if (/^http:\/\//i.test(t)) u = 'https://' + t.slice(7);
  else u = 'https://' + t;
  return u.length > 8 && u.includes('.') ? u : null;
}

/**
 * Probeert een receptpagina op te halen. De meeste receptsites laten dat vanuit een browser niet toe
 * (CORS); dan komt er een duidelijke foutmelding in plaats van een stille mislukking.
 */
export async function importRecipe(url) {
  const clean = normalizeUrl(url);
  if (!clean) throw new Error('Dat is geen geldige link.');
  let html;
  try {
    const res = await fetch(clean, { mode: 'cors', credentials: 'omit' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    html = await res.text();
  } catch {
    throw new Error('Deze site laat niet toe dat een webapp het recept ophaalt. Kopieer de ingrediënten en plak ze in het veld Ingrediënten.');
  }
  const recipe = parseRecipeHtml(html);
  if (!recipe) throw new Error('Geen recept gevonden op die pagina.');
  return { ...recipe, url: clean };
}
