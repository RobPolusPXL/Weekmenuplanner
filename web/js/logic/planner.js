// Weekplanning en boodschappenlijst, gelijk aan MealPlannerLogic in de Android-app.
import { toCanonical, formatQuantity } from './units.js';

export const DAY_KINDS = {
  leeg: 'Leeg',
  koken: 'Koken',
  afhaal: 'Afhaal',
  opwarm: 'Opwarmmaaltijd',
  niet_koken: 'Niet koken',
};

export const DEFAULT_DISH_TYPES = ['pasta', 'rijst', 'aardappelen', 'vlees', 'vis', 'vegetarisch', 'soep', 'wok', 'ovenschotel', 'brood/snel'];

export const kindOf = day => (day && DAY_KINDS[day.kind] ? day.kind : 'leeg');

/** Snapshot die bij een geplande dag bewaard wordt (zelfde velden als DishSnapshot.toMap()). */
export function dishSnapshot(dish) {
  const snap = {
    name: dish.name,
    type: dish.type,
    ingredients: (dish.ingredients || []).map(ingredientMap),
  };
  if (dish.recipeUrl) snap.recipeUrl = dish.recipeUrl;
  if (dish.note) snap.note = dish.note;
  if (dish.photoUrl) snap.photoUrl = dish.photoUrl;
  return snap;
}

export function ingredientMap(i) {
  return {
    raw: i.raw ?? '',
    name: i.name ?? '',
    quantity: i.quantity ?? null,
    unit: i.unit ?? null,
    needsNormalization: Boolean(i.needsNormalization),
  };
}

/** Meer sterren = grotere kans bij "Kies voor mij"; niet beoordeeld telt als 3 sterren. */
export function ratingWeight(dish) {
  switch (dish.rating) {
    case 1: return 1;
    case 2: return 2;
    case 4: return 5;
    case 5: return 8;
    default: return 3;
  }
}

function pickWeighted(candidates, random) {
  const total = candidates.reduce((s, d) => s + ratingWeight(d), 0);
  let roll = Math.floor(random() * total);
  for (const dish of candidates) {
    roll -= ratingWeight(dish);
    if (roll < 0) return dish;
  }
  return candidates[candidates.length - 1];
}

/**
 * Kiest een gerecht voor een dag: liefst een type dat deze week nog niet op het menu staat.
 * → null (lege bibliotheek) of { dish, ruleIgnored }.
 */
export function pickDishForDay(targetDateId, weekPlans, allDishes, random = Math.random) {
  if (!allDishes.length) return null;
  const usedTypes = new Set(
    Object.values(weekPlans)
      .filter(d => d.dateId !== targetDateId && kindOf(d) === 'koken')
      .map(d => d.dishSnapshot?.type?.toLowerCase().trim())
      .filter(Boolean),
  );
  const current = weekPlans[targetDateId];
  const currentDishId = current && kindOf(current) === 'koken' ? current.dishId : null;

  const strict = allDishes.filter(d => d.id !== currentDishId && !usedTypes.has(d.type.toLowerCase().trim()));
  if (strict.length) return { dish: pickWeighted(strict, random), ruleIgnored: false };

  let fallback = allDishes.filter(d => d.id !== currentDishId);
  if (!fallback.length) fallback = allDishes;
  return { dish: pickWeighted(fallback, random), ruleIgnored: true };
}

/** Vult alle lege dagen van een week. → { assignments: {dateId: dish}, anyRuleIgnored } */
export function generateWholeWeek(weekDateIds, existingPlans, allDishes, random = Math.random) {
  const assignments = {};
  let anyRuleIgnored = false;
  if (!allDishes.length) return { assignments, anyRuleIgnored };

  const empty = weekDateIds.filter(id => !existingPlans[id] || kindOf(existingPlans[id]) === 'leeg');
  for (let i = empty.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [empty[i], empty[j]] = [empty[j], empty[i]];
  }
  const simulated = { ...existingPlans };
  for (const dateId of empty) {
    const res = pickDishForDay(dateId, simulated, allDishes, random);
    if (!res) break;
    assignments[dateId] = res.dish;
    if (res.ruleIgnored) anyRuleIgnored = true;
    simulated[dateId] = { dateId, kind: 'koken', dishId: res.dish.id, dishSnapshot: dishSnapshot(res.dish) };
  }
  return { assignments, anyRuleIgnored };
}

/**
 * Telt de ingrediënten van alle kookdagen samen per naam + eenheid.
 * → [{ itemKey, name, totalQuantity, canonicalUnit, displayAmount, checked, dishNames, fromStockText }]
 */
export function aggregateGroceryList(weekDays, checkedMap = {}) {
  const groups = new Map();
  for (const day of weekDays) {
    if (kindOf(day) !== 'koken') continue;
    const snap = day.dishSnapshot;
    if (!snap) continue;
    for (const ing of snap.ingredients || []) {
      const name = (ing.name || '').trim().toLowerCase() || (ing.raw || '').trim().toLowerCase();
      if (!name) continue;
      const conv = toCanonical(ing.quantity ?? null, ing.unit ?? null);
      const itemKey = `${name}|${conv.unit ?? ''}`;
      if (!groups.has(itemKey)) {
        groups.set(itemKey, { name, unit: conv.unit, sum: 0, hasQty: false, dishNames: new Set() });
      }
      const acc = groups.get(itemKey);
      if (conv.quantity != null) { acc.sum += conv.quantity; acc.hasQty = true; }
      if (snap.name && snap.name.trim()) acc.dishNames.add(snap.name);
    }
  }
  const items = [...groups.entries()].map(([itemKey, acc]) => {
    const total = acc.hasQty ? acc.sum : null;
    return {
      itemKey,
      name: acc.name,
      totalQuantity: total,
      canonicalUnit: acc.unit,
      displayAmount: formatQuantity(total, acc.unit),
      checked: checkedMap[itemKey] === true,
      dishNames: [...acc.dishNames],
      fromStockText: '',
    };
  });
  return items.sort((a, b) =>
    (a.checked - b.checked)
    || a.name.localeCompare(b.name, 'nl')
    || (a.canonicalUnit ?? '').localeCompare(b.canonicalUnit ?? ''));
}
