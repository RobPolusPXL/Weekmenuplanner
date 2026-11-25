// Statistieken per maand of per jaar: hoe vaak gekookt, afhaal, favoriete gerechten en types.
import { esc, icon } from '../ui.js';
import { state } from '../store.js';
import { today, monthName } from '../logic/dates.js';
import { computeStats } from '../logic/stats.js';

let mode = 'maand';
let year = Number(today().slice(0, 4));
let month = Number(today().slice(5, 7));

function bars(items) {
  if (!items.length) return '<p class="muted">Nog niets gekookt in deze periode.</p>';
  const max = Math.max(...items.map(i => i.count));
  return `<div class="bars">${items.map(i => `<div class="bar-row">
    <span class="bar-label">${esc(i.label)}</span>
    <span class="bar-track"><span class="bar-fill" style="width:${Math.max(6, (i.count / max) * 100)}%"></span></span>
    <span class="bar-count">${i.count}×</span></div>`).join('')}</div>`;
}

export function render() {
  const prefix = mode === 'maand' ? `${year}-${String(month).padStart(2, '0')}` : String(year);
  const label = mode === 'maand' ? `${monthName(month)} ${year}` : String(year);
  const s = computeStats(Object.values(state.days), prefix, today());
  const tile = (n, l, cls = '') => `<div class="tile ${cls}"><span class="tile-n">${n}</span><span class="tile-l">${l}</span></div>`;
  const cookedPct = s.total ? Math.round((s.cooked / s.total) * 100) : 0;
  return `<div class="view view-stats">
    <div class="view-head"><div><h1 class="title">Statistieken</h1><p class="sub">Enkel dagen tot en met vandaag tellen mee.</p></div>
      <div class="segmented" role="tablist">
        <button class="${mode === 'maand' ? 'on' : ''}" data-action="mode" data-mode="maand">Maand</button>
        <button class="${mode === 'jaar' ? 'on' : ''}" data-action="mode" data-mode="jaar">Jaar</button></div></div>
    <div class="weeknav">
      <button class="icon-btn" data-action="prev" aria-label="Vorige">${icon('left')}</button>
      <div class="weeknav-mid"><div class="weeknav-dates cap">${esc(label)}</div></div>
      <button class="icon-btn" data-action="next" aria-label="Volgende">${icon('right')}</button>
    </div>
    <div class="tiles">
      ${tile(s.cooked, 'Zelf gekookt', 'accent')}${tile(s.takeaway, 'Afhaal')}${tile(s.reheated, 'Opwarm')}${tile(s.noCook, 'Niet koken')}
    </div>
    ${s.total ? `<p class="muted small">${cookedPct}% van de ingevulde avonden zelf gekookt.</p>` : ''}
    <div class="stats-cols">
      <section class="block"><h2 class="block-title">Meest gekookt</h2>${bars(s.topDishes)}</section>
      <section class="block"><h2 class="block-title">Per type</h2>${bars(s.types)}</section>
    </div>
  </div>`;
}

export function onAction(action, el) {
  if (action === 'mode') mode = el.dataset.mode;
  else if (action === 'prev') {
    if (mode === 'jaar') year--; else if (--month < 1) { month = 12; year--; }
  } else if (action === 'next') {
    if (mode === 'jaar') year++; else if (++month > 12) { month = 1; year++; }
  }
}
