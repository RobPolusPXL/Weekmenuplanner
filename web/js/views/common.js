// Onderdelen die op meerdere schermen terugkomen.
import { esc, icon, stars } from '../ui.js';
import { state, isCurrentWeek, isPastWeek } from '../store.js';
import { addDays, monthName } from '../logic/dates.js';
import { formatPrepTime } from '../logic/units.js';

/** "23 – 29 november", of "30 nov – 6 dec" over twee maanden. */
export function shortWeekRange(start) {
  const end = addDays(start, 6);
  const [d1, m1] = [Number(start.slice(8)), Number(start.slice(5, 7))];
  const [d2, m2] = [Number(end.slice(8)), Number(end.slice(5, 7))];
  const year = Number(end.slice(0, 4)) !== Number(new Date().getFullYear()) ? ` ${end.slice(0, 4)}` : '';
  if (m1 === m2) return `${d1} – ${d2} ${monthName(m1).toLowerCase()}${year}`;
  return `${d1} ${monthName(m1).slice(0, 3).toLowerCase()} – ${d2} ${monthName(m2).slice(0, 3).toLowerCase()}${year}`;
}

export function weekNav(subtitle) {
  const label = isCurrentWeek() ? 'Deze week' : isPastWeek() ? 'Voorbije week' : 'Komende week';
  return `<div class="weeknav">
    <button class="icon-btn" data-action="week-prev" aria-label="Vorige week">${icon('left')}</button>
    <div class="weeknav-mid">
      <div class="weeknav-kicker">${esc(subtitle ? `${label} · ${subtitle}` : label)}</div>
      <div class="weeknav-dates">${esc(shortWeekRange(state.selectedWeekStart))}</div>
      ${isCurrentWeek() ? '' : '<button class="link-btn" data-action="week-now">Naar deze week</button>'}
    </div>
    <button class="icon-btn" data-action="week-next" aria-label="Volgende week">${icon('right')}</button>
  </div>`;
}

export function dishMeta(dish, { withStars = true } = {}) {
  const parts = [`<span class="chip type">${esc(dish.type)}</span>`];
  if (dish.prepMinutes) parts.push(`<span class="meta">${icon('clock')}${esc(formatPrepTime(dish.prepMinutes))}</span>`);
  if (withStars && dish.rating) parts.push(stars(dish.rating));
  return `<div class="dish-meta">${parts.join('')}</div>`;
}

export function emptyState(iconName, title, text, actionHtml = '') {
  return `<div class="empty">${icon(iconName, 'empty-ic')}<h3>${esc(title)}</h3><p>${esc(text)}</p>${actionHtml}</div>`;
}
