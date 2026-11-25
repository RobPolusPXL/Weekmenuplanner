// Onderdelen die op meerdere schermen terugkomen.
import { esc, icon, stars } from '../ui.js';
import { state, isCurrentWeek, isPastWeek } from '../store.js';
import { weekHeader } from '../logic/dates.js';
import { formatPrepTime } from '../logic/units.js';

export function weekNav(subtitle) {
  const label = isCurrentWeek() ? 'Deze week' : isPastWeek() ? 'Voorbije week' : 'Komende week';
  return `<div class="weeknav">
    <button class="icon-btn" data-action="week-prev" aria-label="Vorige week">${icon('left')}</button>
    <div class="weeknav-mid">
      <div class="weeknav-kicker">${esc(subtitle ? `${label} · ${subtitle}` : label)}</div>
      <div class="weeknav-dates">${esc(weekHeader(state.selectedWeekStart))}</div>
      ${isCurrentWeek() ? '' : '<button class="link-btn" data-action="week-now">Naar deze week</button>'}
    </div>
    <button class="icon-btn" data-action="week-next" aria-label="Volgende week">${icon('right')}</button>
  </div>`;
}

export function typeColor(type) {
  let h = 0;
  for (const ch of String(type || '').toLowerCase()) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h % 6;
}

export function dishMeta(dish, { withStars = true } = {}) {
  const parts = [`<span class="chip type" data-c="${typeColor(dish.type)}">${esc(dish.type)}</span>`];
  if (dish.prepMinutes) parts.push(`<span class="meta">${icon('clock')}${esc(formatPrepTime(dish.prepMinutes))}</span>`);
  if (withStars && dish.rating) parts.push(stars(dish.rating));
  return `<div class="dish-meta">${parts.join('')}</div>`;
}

export function emptyState(iconName, title, text, actionHtml = '') {
  return `<div class="empty">${icon(iconName, 'empty-ic')}<h3>${esc(title)}</h3><p>${esc(text)}</p>${actionHtml}</div>`;
}
