// Recept uit de schema.org/Recipe JSON-LD van een receptpagina, gelijk aan RecipeJsonLdParser in de Android-app.

const SCRIPT_RE = /<script[^>]*type\s*=\s*["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
const DURATION_RE = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:\d+S)?)?$/i;

/** → { name, ingredientLines, totalMinutes, steps, servings } of null. */
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
      steps: readSteps(recipe.recipeInstructions, 0).slice(0, 60),
      servings: readServings(recipe.recipeYield),
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

// recipeInstructions: tekst, lijst van teksten, HowToStep { text } of HowToSection { itemListElement }.
// Een stap met een HTML-lijst wordt per <li> opgesplitst, zoals de stappen op de pagina zelf.
function readSteps(node, depth) {
  if (depth > 4 || node == null) return [];
  if (typeof node === 'string') return splitStepText(node);
  if (Array.isArray(node)) return node.flatMap(n => readSteps(n, depth + 1));
  if (typeof node === 'object') {
    if (node.itemListElement) return readSteps(node.itemListElement, depth + 1);
    return splitStepText(typeof node.text === 'string' ? node.text : typeof node.name === 'string' ? node.name : '');
  }
  return [];
}

function splitStepText(text) {
  return String(text)
    .split(/<\/li>|<\/p>|<br\s*\/?>|\n/i)
    .map(t => cleanText(t).replace(/\s+([.,;:!?])/g, '$1'))
    .filter(Boolean);
}

function readServings(yieldValue) {
  const values = Array.isArray(yieldValue) ? yieldValue : [yieldValue];
  for (const v of values) {
    const m = /\d+/.exec(String(v ?? ''));
    if (m) {
      const n = Number(m[0]);
      if (n >= 1 && n <= 20) return n;
    }
  }
  return null;
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

/** De Cloudflare Worker die receptpagina's ophaalt (een browser mag dat niet rechtstreeks, CORS). */
export const RECIPE_PROXY = 'https://recept-proxy.rob-polus.workers.dev/';

/** Haalt een receptpagina op via de proxy en leest het recept eruit. Gooit een Fout met uitleg. */
export async function importRecipe(url, fetchFn = fetch) {
  const clean = normalizeUrl(url);
  if (!clean) throw new Error('Dat is geen geldige link.');
  let html;
  try {
    const res = await fetchFn(`${RECIPE_PROXY}?url=${encodeURIComponent(clean)}`, { credentials: 'omit' });
    html = await res.text();
    if (!res.ok) throw new Error(html);
  } catch (e) {
    throw new Error(`Ophalen lukte niet${e?.message ? ` (${e.message.slice(0, 80)})` : ''}.`);
  }
  const recipe = parseRecipeHtml(html);
  if (!recipe) throw new Error('Op deze pagina staat geen recept dat de app kan lezen.');
  return { ...recipe, url: clean };
}
