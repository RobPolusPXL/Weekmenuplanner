// Kleine UI-hulpmiddelen: escapen, iconen, panelen (sheets) en opnieuw tekenen zonder focusverlies.

export const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const P = {
  week: '<rect x="3" y="4.5" width="18" height="16" rx="2.5"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/><path d="M7.5 13.5h2M11 13.5h2M14.5 13.5h2M7.5 17h2M11 17h2"/>',
  book: '<path d="M4 4.5A1.5 1.5 0 0 1 5.5 3H11v17H5.5A1.5 1.5 0 0 1 4 18.5z"/><path d="M20 4.5A1.5 1.5 0 0 0 18.5 3H13v17h5.5a1.5 1.5 0 0 0 1.5-1.5z"/>',
  cart: '<path d="M2.5 3.5h2.2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.2h8.8a1.5 1.5 0 0 0 1.5-1.1l1.6-6.3H6"/><circle cx="9.5" cy="20" r="1.4"/><circle cx="17" cy="20" r="1.4"/>',
  kitchen: '<rect x="5" y="2.5" width="14" height="19" rx="2"/><path d="M5 9.5h14M8.5 5.5v2M8.5 12.5v4"/>',
  chart: '<path d="M4 20.5h16"/><rect x="5.5" y="11" width="3" height="7" rx="1"/><rect x="10.5" y="5" width="3" height="13" rx="1"/><rect x="15.5" y="8.5" width="3" height="9.5" rx="1"/>',
  people: '<circle cx="9" cy="8" r="3.2"/><path d="M3 19.5c.6-3.3 3-5 6-5s5.4 1.7 6 5"/><circle cx="16.8" cy="9" r="2.5"/><path d="M16.5 14.6c2.3.2 4 1.6 4.5 4.4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  left: '<path d="M15 5l-7 7 7 7"/>',
  right: '<path d="M9 5l7 7-7 7"/>',
  swap: '<path d="M7 4l-3 3 3 3M4 7h13M17 20l3-3-3-3M20 17H7"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 12.5A1.5 1.5 0 0 0 8.5 21h7a1.5 1.5 0 0 0 1.5-1.5L18 7M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7"/>',
  star: '<path d="M12 3.2l2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 17l-5.4 2.9 1.1-6.1-4.5-4.2 6.1-.8z"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.3-4.3"/>',
  dice: '<rect x="4" y="4" width="16" height="16" rx="3.5"/><circle cx="9" cy="9" r="1.2" fill="currentColor"/><circle cx="15" cy="15" r="1.2" fill="currentColor"/><circle cx="15" cy="9" r="1.2" fill="currentColor"/><circle cx="9" cy="15" r="1.2" fill="currentColor"/>',
  sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  edit: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8"/>',
  logout: '<path d="M14 4h4.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H14M10 16l-4-4 4-4M6 12h10"/>',
  warning: '<path d="M12 4l9 16H3z"/><path d="M12 10v4.5M12 17.5v.5"/>',
  addcart: '<path d="M2.5 3.5h2.2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.2h8.8a1.5 1.5 0 0 0 1.5-1.1l1.6-6.3H6"/><circle cx="9.5" cy="20" r="1.4"/><circle cx="17" cy="20" r="1.4"/><path d="M13 6.5v4M11 8.5h4"/>',
  camera: '<path d="M4 8.5A1.5 1.5 0 0 1 5.5 7h2.3l1.5-2.5h5.4L16.2 7h2.3A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z"/><circle cx="12" cy="12.8" r="3.3"/>',
  pot: '<path d="M4 10h16v6.5A3.5 3.5 0 0 1 16.5 20h-9A3.5 3.5 0 0 1 4 16.5z"/><path d="M2 10h2M20 10h2M8 7c0-1.5 1.8-2.5 4-2.5S16 5.5 16 7"/>',
  bag: '<path d="M5 8h14l-1 12.5a1 1 0 0 1-1 .9H7a1 1 0 0 1-1-.9z"/><path d="M9 10V6.5a3 3 0 0 1 6 0V10"/>',
  moon: '<path d="M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"/>',
  calendarCopy: '<rect x="3" y="4.5" width="18" height="16" rx="2.5"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4M12 12.5v5M9.5 15h5"/>',
  play: '<path d="M8 5.5v13l10.5-6.5z"/>',
  minus: '<path d="M5 12h14"/>',
  share: '<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="M8.2 10.8l7.6-4M8.2 13.2l7.6 4"/>',
  chef: '<path d="M7 14.5a4 4 0 0 1-.6-7.9A5 5 0 0 1 12 3.5a5 5 0 0 1 5.6 3.1A4 4 0 0 1 17 14.5"/><path d="M7 14.5V20h10v-5.5M7 17h10"/>',
  bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
  timer: '<circle cx="12" cy="13.5" r="7.5"/><path d="M12 9.5v4l2.5 1.5M9.5 2.5h5"/>',
  sparkle2: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/>',
  tag: '<path d="M3.5 12.2V4.5a1 1 0 0 1 1-1h7.7l8.3 8.3a1.5 1.5 0 0 1 0 2.1l-6.1 6.1a1.5 1.5 0 0 1-2.1 0z"/><circle cx="8" cy="8" r="1.3"/>',
};

export function icon(name, cls = '') {
  return `<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${P[name] ?? ''}</svg>`;
}

/** Sterren: alleen-lezen, of klikbaar met data-action. */
export function stars(rating, action = null, extra = '') {
  return `<span class="stars${action ? ' stars-edit' : ''}" role="${action ? 'radiogroup' : 'img'}" aria-label="${rating ? `${rating} van 5 sterren` : 'Nog niet beoordeeld'}">${
    [1, 2, 3, 4, 5].map(n => (action
      ? `<button type="button" class="star${n <= rating ? ' on' : ''}" data-action="${action}" data-rating="${n === rating ? 0 : n}" ${extra} aria-label="${n} ster${n > 1 ? 'ren' : ''}">${icon('star')}</button>`
      : `<span class="star${n <= rating ? ' on' : ''}">${icon('star')}</span>`)).join('')
  }</span>`;
}

// ---------- Opnieuw tekenen met behoud van focus en cursor ----------

export function renderInto(el, html) {
  const active = document.activeElement;
  let keep = null;
  if (active && el.contains(active) && active.id) {
    keep = { id: active.id, start: active.selectionStart, end: active.selectionEnd, scroll: active.scrollTop };
  }
  const scrollers = [...el.querySelectorAll('[data-keep-scroll]')].map(s => [s.dataset.keepScroll, s.scrollTop]);
  el.innerHTML = html;
  for (const [key, top] of scrollers) {
    const s = el.querySelector(`[data-keep-scroll="${key}"]`);
    if (s) s.scrollTop = top;
  }
  if (keep) {
    const again = el.querySelector(`#${CSS.escape(keep.id)}`);
    if (again) {
      again.focus({ preventScroll: true });
      try { if (keep.start != null) again.setSelectionRange(keep.start, keep.end); } catch { /* geen tekstveld */ }
      again.scrollTop = keep.scroll;
    }
  }
}

// ---------- Panelen (sheets) ----------

const stack = [];
let layer = null;

function ensureLayer() {
  if (layer) return layer;
  layer = document.getElementById('sheets');
  layer.addEventListener('click', e => {
    if (e.target.classList.contains('sheet-backdrop')) closeSheet();
  });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && stack.length) closeSheet(); });
  return layer;
}

/**
 * Opent een paneel. render() geeft de inhoud (HTML) en wordt opnieuw aangeroepen bij elke wijziging;
 * onAction(action, el, event) handelt klikken op [data-action] af; onSubmit(form, event) voor formulieren.
 */
export function openSheet({ title, render, onAction, onSubmit, onInput, onClose, wide = false, full = false }) {
  ensureLayer();
  const wrap = document.createElement('div');
  wrap.className = 'sheet-wrap';
  wrap.innerHTML = `<div class="sheet-backdrop"></div>
    <section class="sheet${wide ? ' sheet-wide' : ''}${full ? ' sheet-full' : ''}" role="dialog" aria-modal="true" aria-label="${esc(typeof title === 'function' ? title() : title)}">
      <header class="sheet-head"><h2 class="sheet-title"></h2>
        <button type="button" class="icon-btn" data-close aria-label="Sluiten">${icon('close')}</button></header>
      <div class="sheet-body" data-keep-scroll="sheet"></div>
    </section>`;
  layer.appendChild(wrap);
  const entry = { wrap, title, render, onAction, onSubmit, onInput, onClose };
  stack.push(entry);
  document.body.classList.add('has-sheet');

  wrap.querySelector('[data-close]').addEventListener('click', () => closeSheet());
  const body = wrap.querySelector('.sheet-body');
  body.addEventListener('click', e => {
    const el = e.target.closest('[data-action]');
    if (el && body.contains(el) && onAction) onAction(el.dataset.action, el, e);
  });
  body.addEventListener('submit', e => { e.preventDefault(); onSubmit?.(e.target, e); });
  body.addEventListener('input', e => onInput?.(e.target, e));
  refreshSheet(entry);
  requestAnimationFrame(() => wrap.classList.add('open'));
  const first = body.querySelector('[autofocus]');
  if (first) setTimeout(() => first.focus(), 60);
  return entry;
}

function refreshSheet(entry) {
  const t = typeof entry.title === 'function' ? entry.title() : entry.title;
  entry.wrap.querySelector('.sheet-title').textContent = t;
  renderInto(entry.wrap.querySelector('.sheet-body'), entry.render());
}

export function refreshSheets() {
  stack.forEach(refreshSheet);
}

export function closeSheet() {
  const entry = stack.pop();
  if (!entry) return;
  try { entry.onClose?.(); } catch (e) { console.warn(e); }
  entry.wrap.classList.remove('open');
  setTimeout(() => entry.wrap.remove(), 180);
  if (!stack.length) document.body.classList.remove('has-sheet');
}

export function closeAllSheets() {
  while (stack.length) closeSheet();
}

/** Bevestiging binnen de app (de browser-dialoog werkt niet overal). */
export function confirmSheet({ title, message, confirmLabel = 'Verwijderen', danger = true, onConfirm }) {
  openSheet({
    title,
    render: () => `<p class="lead">${esc(message)}</p>
      <div class="row-end"><button class="btn ghost" data-action="cancel">Annuleren</button>
      <button class="btn ${danger ? 'danger' : 'primary'}" data-action="ok">${esc(confirmLabel)}</button></div>`,
    onAction: a => { closeSheet(); if (a === 'ok') onConfirm(); },
  });
}
