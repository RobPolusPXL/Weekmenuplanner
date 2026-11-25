// Voorraadlogica, gelijk aan PantryLogic in de Android-app.
//
// Een voorraaditem bevat de VRIJE hoeveelheid. Wat geplande maaltijden van de huidige en komende weken
// nodig hebben, wordt per week gereserveerd en van de vrije voorraad afgetrokken. Wordt een maaltijd
// geschrapt, dan komt de reservering terug. Is een week voorbij, dan blijft haar reservering staan.
import { aggregateGroceryList } from './planner.js';
import { weekStart, isIsoDate } from './dates.js';
import { formatQuantity, round3 } from './units.js';
import { parseIngredient } from './parser.js';

const EPS = 0.0001;

export const pantryKey = item => `${item.name}|${item.unit ?? ''}`;
export const isTracked = item => item.quantity != null;

/** Op of onder het minimum (of helemaal op als er geen minimum is). */
export function isLow(item) {
  if (item.quantity == null) return false;
  const q = item.quantity;
  return q <= EPS || (item.minQuantity != null && q <= item.minQuantity + EPS);
}

/** Benodigde hoeveelheden per itemKey voor één week (enkel regels met een hoeveelheid). */
export function weekNeeds(weekDays) {
  const out = {};
  for (const item of aggregateGroceryList(weekDays, {})) {
    if (item.totalQuantity != null) out[item.itemKey] = item.totalQuantity;
  }
  return out;
}

/** Groepeert dagen per weekstart. → { weekStartIso: [day, …] } */
export function daysByWeek(allDays, firstDayOfWeek = 1) {
  const out = {};
  for (const day of Object.values(allDays)) {
    if (!isIsoDate(day.dateId)) continue;
    const w = weekStart(day.dateId, firstDayOfWeek);
    (out[w] ||= []).push(day);
  }
  return out;
}

/** Huidige en toekomstige weken met geplande dagen of reserveringen, gesorteerd. */
export function activeWeeks(currentWeekStart, plannedWeeks, reservedWeeks) {
  return [...new Set([...plannedWeeks, ...reservedWeeks])].filter(w => w >= currentWeekStart).sort();
}

/**
 * Verdeelt de voorraad over de actieve weken, vroegste week eerst.
 * pool = vrije voorraad + wat nu al in actieve weken gereserveerd staat.
 * → { freeStock: {key: qty}, reservations: {week: {key: qty}} }
 */
export function reconcile(freeStock, currentReservations, needsPerWeek, weeksIn) {
  const weeks = [...weeksIn].sort();
  const newFree = {};
  const newRes = Object.fromEntries(weeks.map(w => [w, {}]));
  for (const [key, free] of Object.entries(freeStock)) {
    let pool = free + weeks.reduce((s, w) => s + (currentReservations[w]?.[key] ?? 0), 0);
    for (const w of weeks) {
      const need = needsPerWeek[w]?.[key] ?? 0;
      const take = round3(Math.min(need, pool));
      if (take > EPS) { newRes[w][key] = take; pool -= take; }
    }
    newFree[key] = round3(Math.max(0, pool));
  }
  return { freeStock: newFree, reservations: newRes };
}

export function hasChanges(freeStock, currentReservations, weeks, result) {
  for (const [k, v] of Object.entries(result.freeStock)) {
    if (Math.abs((freeStock[k] ?? 0) - v) > EPS) return true;
  }
  for (const w of weeks) {
    const a = Object.fromEntries(Object.entries(currentReservations[w] ?? {}).filter(([, v]) => v > EPS));
    const b = result.reservations[w] ?? {};
    const ak = Object.keys(a).sort(); const bk = Object.keys(b).sort();
    if (ak.join('\u0000') !== bk.join('\u0000')) return true;
    if (ak.some(k => Math.abs(a[k] - (b[k] ?? 0)) > EPS)) return true;
  }
  return false;
}

/** Splitst de boodschappenlijst van een week in "nog kopen" en "uit voorraad". */
export function splitGroceryList(items, reserved, pantry) {
  const basisNames = new Set(pantry.filter(p => !isTracked(p)).map(p => p.name));
  const toBuy = [];
  const fromStock = [];
  for (const item of items) {
    const total = item.totalQuantity;
    const r = reserved[item.itemKey] ?? 0;
    if (basisNames.has(item.name)) {
      fromStock.push({ ...item, fromStockText: 'in huis' });
    } else if (total == null) {
      const inHouse = r > EPS || pantry.some(p => p.name === item.name && (p.quantity ?? 0) > EPS);
      (inHouse ? fromStock : toBuy).push(inHouse ? { ...item, fromStockText: 'in huis' } : item);
    } else if (r <= EPS) {
      toBuy.push(item);
    } else if (total - r <= EPS) {
      fromStock.push({ ...item, fromStockText: formatQuantity(total, item.canonicalUnit) });
    } else {
      const remaining = round3(total - r);
      toBuy.push({
        ...item,
        totalQuantity: remaining,
        displayAmount: formatQuantity(remaining, item.canonicalUnit),
        fromStockText: formatQuantity(r, item.canonicalUnit),
      });
    }
  }
  return { toBuy, fromStock };
}

/** "1 kg rijst" → { name: 'rijst', unit: 'g', quantity: 1000 }; "zout" → basisvoorraad (quantity null). */
export function parsePantryInput(text) {
  if (!String(text ?? '').trim()) return null;
  const ing = parseIngredient(text);
  const name = ing.name.trim().toLowerCase();
  if (!name) return null;
  return { name, unit: ing.quantity != null ? ing.unit : null, quantity: ing.quantity };
}
