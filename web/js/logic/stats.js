// Statistieken per maand of jaar, gelijk aan StatsLogic in de Android-app.
import { kindOf } from './planner.js';

/**
 * @param periodPrefix "2026" voor een jaar of "2026-10" voor een maand.
 * @param todayIso alleen dagen tot en met vandaag tellen mee.
 */
export function computeStats(days, periodPrefix, todayIso, topDishLimit = 5) {
  const inPeriod = days.filter(d => d.dateId.startsWith(periodPrefix) && d.dateId <= todayIso);
  const cooked = inPeriod.filter(d => kindOf(d) === 'koken');

  const byDish = new Map();
  for (const d of cooked) {
    const key = d.dishId || d.dishSnapshot?.name?.trim().toLowerCase() || 'onbekend';
    (byDish.get(key) || byDish.set(key, []).get(key)).push(d);
  }
  const topDishes = [...byDish.values()]
    .map(group => ({
      label: group.map(g => g.dishSnapshot?.name).find(n => n && n.trim()) || 'Onbekend gerecht',
      count: group.length,
    }))
    .sort((a, b) => b.count - a.count || a.label.toLowerCase().localeCompare(b.label.toLowerCase(), 'nl'))
    .slice(0, topDishLimit);

  const byType = new Map();
  for (const d of cooked) {
    const t = d.dishSnapshot?.type?.trim().toLowerCase();
    if (t) byType.set(t, (byType.get(t) || 0) + 1);
  }
  const types = [...byType.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'nl'));

  const count = k => inPeriod.filter(d => kindOf(d) === k).length;
  const s = {
    cooked: cooked.length,
    takeaway: count('afhaal'),
    reheated: count('opwarm'),
    noCook: count('niet_koken'),
    topDishes,
    types,
  };
  s.total = s.cooked + s.takeaway + s.reheated + s.noCook;
  return s;
}
