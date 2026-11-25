// Koken: timers uit bereidingsstappen, porties schalen, recept uit een AI-antwoord, lijst als tekst.
import { formatQuantity, capitalize } from './units.js';
import { groupByDepartment } from './departments.js';

export const DEFAULT_SERVINGS = 2;

/**
 * Zoekt tijden in een bereidingsstap: "10 minuten", "10–12 min", "1 uur", "1,5 uur", "30 sec".
 * Bij een bereik telt de hoogste waarde. → [{ label, seconds }]
 */
export function stepTimers(step) {
  const out = [];
  const re = /(\d+(?:[.,]\d+)?)(?:\s*(?:-|–|tot|à)\s*(\d+(?:[.,]\d+)?))?\s*(uur|uren|u|minuten|minuut|min|seconden|sec|s)\b/gi;
  for (const m of String(step).matchAll(re)) {
    const value = Number((m[2] ?? m[1]).replace(',', '.'));
    const unit = m[3].toLowerCase();
    const factor = unit.startsWith('u') ? 3600 : unit.startsWith('m') ? 60 : 1;
    const seconds = Math.round(value * factor);
    if (seconds >= 10 && seconds <= 12 * 3600) out.push({ label: m[0].replace(/\s+/g, ' ').trim(), seconds });
  }
  return out;
}

export function formatTimer(seconds) {
  const s = Math.max(0, Math.round(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(h ? 2 : 1, '0');
  return h ? `${h}:${mm}:${String(sec).padStart(2, '0')}` : `${mm}:${String(sec).padStart(2, '0')}`;
}

/** Ingrediënten die in een stap genoemd worden (op naam of laatste woord van de naam). */
export function ingredientsForStep(step, ingredients) {
  const text = String(step).toLowerCase();
  return ingredients.filter(i => {
    const name = (i.name || '').toLowerCase();
    if (!name) return false;
    if (text.includes(name)) return true;
    const last = name.split(' ').pop();
    return last.length >= 4 && text.includes(last.slice(0, Math.max(4, last.length - 1)));
  });
}

/** Factor voor porties: geplande porties / porties van het recept. */
export function portionFactor(portions, servings) {
  const p = Number(portions);
  const s = Number(servings) || DEFAULT_SERVINGS;
  return p > 0 && s > 0 ? p / s : 1;
}

export function scaledAmount(ingredient, factor) {
  if (ingredient.quantity == null) return '';
  return formatQuantity(ingredient.quantity * factor, ingredient.unit);
}

// ---------- Recept via een AI-chat ----------

export function aiPrompt(source, types) {
  return `Zet dit recept om naar JSON voor mijn maaltijdplanner. Antwoord met ALLEEN de JSON, zonder uitleg.

Formaat:
{
  "name": "naam van het gerecht",
  "type": "één van: ${types.join(', ')}",
  "servings": aantal personen (getal),
  "prepMinutes": totale bereidingstijd in minuten (getal),
  "ingredients": ["500 g gehakt", "2 uien", "1 blik tomatenblokjes"],
  "steps": ["Stap 1 ...", "Stap 2 ..."]
}

Regels:
- Alles in het Nederlands (Vlaams mag).
- Eén ingrediënt per regel, hoeveelheid vooraan, in g, ml, el, tl, stuk, blik of teen.
- Stappen kort en concreet, met tijden erin (bv. "bak 8 minuten").

Recept:
${source.trim() || '(plak hier de link of de tekst van het recept)'}`;
}

/** Leest het antwoord van de AI. Gooit een Fout met een duidelijke uitleg als het niet lukt. */
export function parseAiRecipe(answer, types = []) {
  const text = String(answer ?? '').trim();
  if (!text) throw new Error('Plak eerst het antwoord van de AI.');
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) throw new Error('Geen JSON gevonden in het antwoord. Vraag de AI om alleen de JSON te geven.');
  let obj;
  try { obj = JSON.parse(text.slice(start, end + 1)); } catch {
    throw new Error('Het antwoord is geen geldige JSON. Vraag de AI het opnieuw te geven, zonder uitleg.');
  }
  const strList = v => (Array.isArray(v) ? v : typeof v === 'string' ? v.split('\n') : [])
    .map(x => (typeof x === 'string' ? x : x && typeof x === 'object' ? Object.values(x).join(' ') : String(x ?? '')))
    .map(s => s.replace(/^\s*(?:\d+[.)]|[-•*])\s*/, '').trim()).filter(Boolean);
  const ingredients = strList(obj.ingredients ?? obj.ingredienten ?? obj['ingrediënten']);
  if (!ingredients.length) throw new Error('Het antwoord bevat geen ingrediënten.');
  const type = String(obj.type ?? '').trim().toLowerCase();
  const int = (v, lo, hi) => { const n = Math.round(Number(v)); return Number.isFinite(n) && n >= lo && n <= hi ? n : null; };
  return {
    name: String(obj.name ?? obj.naam ?? '').trim().slice(0, 120),
    type: types.map(t => t.toLowerCase()).includes(type) ? type : (type || null),
    servings: int(obj.servings ?? obj.porties, 1, 20),
    prepMinutes: int(obj.prepMinutes ?? obj.tijd ?? obj.time, 1, 1440),
    ingredients: ingredients.slice(0, 100),
    steps: strList(obj.steps ?? obj.stappen ?? obj.bereiding).slice(0, 60),
  };
}

// ---------- Boodschappenlijst als tekst ----------

export function groceryText(toBuy, looseItems, weekLabel) {
  const lines = [`Boodschappen ${weekLabel}`];
  const open = toBuy.filter(i => !i.checked);
  for (const [, label, items] of groupByDepartment(open, i => i.name)) {
    lines.push('', label);
    for (const i of items) lines.push(`☐ ${capitalize(i.name)}${i.displayAmount ? ` – ${i.displayAmount}` : ''}`);
  }
  const loose = looseItems.filter(i => !i.checked);
  if (loose.length) {
    lines.push('', 'Extra');
    for (const i of loose) {
      const amount = i.quantity != null ? formatQuantity(i.quantity, i.unit) : i.unit ?? '';
      lines.push(`☐ ${capitalize(i.name)}${amount ? ` – ${amount}` : ''}`);
    }
  }
  if (lines.length === 1) lines.push('', 'Niets meer te kopen.');
  return lines.join('\n');
}

