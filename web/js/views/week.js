// Weekplanning: zeven dagen, per dag koken (gerecht), afhaal, opwarm of niet koken.
import { esc, icon, stars, openSheet, closeSheet, closeAllSheets, confirmSheet, refreshSheets } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { today, dayName, belgian, shortBelgian, weekDates } from '../logic/dates.js';
import { DAY_KINDS, kindOf } from '../logic/planner.js';
import { capitalize, formatPrepTime } from '../logic/units.js';
import { weekNav, dishMeta } from './common.js';
import { openDishEditor, openDishPicker } from './dishes.js';
import { openCookMode } from './cook.js';
import { portionFactor, scaledAmount, DEFAULT_SERVINGS } from '../logic/cooking.js';

const KIND_ICON = { afhaal: 'bag', opwarm: 'pot', niet_koken: 'moon' };

export function render() {
  const t = today();
  const past = S.isPastWeek();
  const ids = S.selectedWeekDates();
  const rows = ids.map(id => dayRow(id, state.days[id], id === t, id < t)).join('');
  const anyEmpty = ids.some(id => id >= t && kindOf(state.days[id]) === 'leeg');
  return `<div class="view view-week">
    <h1 class="sr-only">Weekmenu</h1>
    ${weekNav()}
    ${S.isCurrentWeek() ? tonightCard(t, state.days[t]) : ''}
    <section class="week-section" aria-label="De hele week">
      <h2 class="list-title">De hele week</h2>
      <div class="week-list">${rows}</div>
    </section>
    ${!past && anyEmpty ? `<button class="btn full fill-btn" data-action="generate">${icon('sparkle')}Lege dagen automatisch invullen</button>` : ''}
  </div>`;
}

const dayAbbr = id => `${dayName(id).slice(0, 2)} ${Number(id.slice(8))}`;

function libDish(day) {
  return day?.dishId ? state.dishes.find(d => d.id === day.dishId) : null;
}

function tonightCard(id, day) {
  const kind = kindOf(day);
  const kicker = `Vanavond · ${dayName(id).toLowerCase()}`;
  if (kind === 'koken' && day.dishSnapshot) {
    const snap = day.dishSnapshot;
    const lib = libDish(day);
    const portions = snap.portions ?? lib?.servings ?? snap.servings ?? DEFAULT_SERVINGS;
    const meta = [lib?.prepMinutes ? formatPrepTime(lib.prepMinutes) : null, `${portions} ${portions === 1 ? 'persoon' : 'personen'}`, snap.type].filter(Boolean);
    return `<section class="tonight" aria-label="Vanavond">
      <div class="tonight-kicker">${esc(kicker)}</div>
      <div class="tonight-name">${esc(snap.name)}</div>
      <div class="tonight-meta">${esc(meta.join(' · '))}</div>
      <div class="tonight-actions">
        <button class="btn primary grow" data-action="cook-today" data-date="${id}">${icon('play')}Koken</button>
        <button class="btn" data-action="open-day" data-date="${id}">Bekijken</button>
      </div>
    </section>`;
  }
  if (kind === 'leeg') {
    return `<section class="tonight" aria-label="Vanavond">
      <div class="tonight-kicker">${esc(kicker)}</div>
      <div class="tonight-name">Nog niets gepland</div>
      <div class="tonight-actions"><button class="btn primary grow" data-action="open-day" data-date="${id}">${icon('plus')}Plannen</button></div>
    </section>`;
  }
  return `<section class="tonight marker" aria-label="Vanavond">
    <div class="tonight-kicker">${esc(kicker)}</div>
    <div class="tonight-name">${icon(KIND_ICON[kind])}${esc(DAY_KINDS[kind])}</div>
    ${day?.note ? `<div class="tonight-meta">${esc(day.note)}</div>` : ''}
    <div class="tonight-actions"><button class="btn" data-action="open-day" data-date="${id}">Wijzigen</button></div>
  </section>`;
}

function dayRow(id, day, isToday, isPast) {
  const kind = kindOf(day);
  let text;
  if (kind === 'koken' && day.dishSnapshot) text = esc(day.dishSnapshot.name);
  else if (kind === 'leeg') text = isPast ? 'Niets gepland' : 'Nog niets gepland';
  else text = `<span class="row-marker">${icon(KIND_ICON[kind])}${esc(DAY_KINDS[kind])}</span>${day?.note ? ` <span class="row-note">· ${esc(day.note)}</span>` : ''}`;
  let end;
  if (isToday) end = '<span class="row-tag today">Vandaag</span>';
  else if (isPast) end = kind === 'koken' || kind === 'opwarm' ? '<span class="row-tag">Gegeten</span>' : '';
  else if (kind === 'leeg') end = `<span class="plan-pill">${icon('plus')}Plannen</span>`;
  else end = icon('right', 'row-chev');
  const cls = ['week-row', `kind-${kind}`, isToday ? 'today' : '', isPast && !isToday ? 'past' : ''].filter(Boolean).join(' ');
  return `<button class="${cls}" data-action="open-day" data-date="${id}">
    <span class="row-day">${esc(dayAbbr(id))}</span>
    <span class="row-text">${text}</span>${end}
  </button>`;
}

export function onAction(action, el) {
  if (action === 'generate') S.generateWeek();
  else if (action === 'open-day') openDaySheet(el.dataset.date);
  else if (action === 'cook-today') openCookMode({ dateId: el.dataset.date });
}

// ---------- Paneel voor één dag ----------

function openDaySheet(dateId) {
  let markerKind = null;
  let markerNote = '';
  const handle = (a, el) => {
    const day = state.days[dateId];
    if (a === 'pick-for-me') { S.pickForDay(dateId); closeSheet(); }
    else if (a === 'cook') openCookMode({ dateId });
    else if (a === 'portions') {
      const snap = day?.dishSnapshot;
      const lib = state.dishes.find(d => d.id === day?.dishId);
      const current = snap?.portions ?? lib?.servings ?? snap?.servings ?? DEFAULT_SERVINGS;
      S.setPortions(dateId, current + Number(el.dataset.delta));
    }
    else if (a === 'choose') openDishPicker(dish => { S.assignDish(dateId, dish); closeAllSheets(); });
    else if (a === 'new-dish') openDishEditor(null, { assignToDateId: dateId, onSaved: closeAllSheets });
    else if (a === 'edit-dish') {
      const dish = state.dishes.find(d => d.id === day.dishId);
      if (dish) openDishEditor(dish);
      else S.toast('Dit gerecht staat niet meer in de bibliotheek.');
    }
    else if (a === 'marker') { markerKind = el.dataset.kind; refreshSheets(); }
    else if (a === 'save-marker') { S.markDay(dateId, markerKind, markerNote); closeSheet(); }
    else if (a === 'clear') confirmSheet({
      title: 'Dag leegmaken', message: `${dayName(dateId)} leegmaken?`, confirmLabel: 'Leegmaken',
      onConfirm: () => { S.clearDay(dateId); closeAllSheets(); },
    });
    else if (a === 'swap') openSwapSheet(dateId);
    else if (a === 'copy') openCopySheet(day);
    else if (a === 'options') openDayOptions(dateId, (b, e2) => { if (b === 'pick-for-me') { S.pickForDay(dateId); closeAllSheets(); } else handle(b, e2); });
  };
  openSheet({
    title: () => `${dayName(dateId)} ${belgian(dateId)}`,
    render: () => {
      const day = state.days[dateId];
      const kind = kindOf(day);
      const past = S.isPastWeek();
      if (kind === 'koken' && day.dishSnapshot) return cookingDay(day, past);
      return emptyOrMarkerDay(day, kind, markerKind, markerNote, past);
    },
    onInput: el => { if (el.id === 'marker-note') markerNote = el.value; },
    onAction: handle,
  });
}

function cookingDay(day, past) {
  const snap = day.dishSnapshot;
  const lib = state.dishes.find(d => d.id === day.dishId);
  const photo = snap.photoUrl || lib?.photoUrl;
  const servings = lib?.servings ?? snap.servings ?? DEFAULT_SERVINGS;
  const portions = snap.portions ?? servings;
  const factor = portionFactor(portions, servings);
  const steps = lib?.steps ?? [];
  const chips = [
    `<span class="chip type">${esc(snap.type)}</span>`,
    lib?.prepMinutes ? `<span class="chip">${esc(formatPrepTime(lib.prepMinutes))}</span>` : '',
    steps.length ? `<span class="chip">${steps.length} stappen</span>` : '',
    lib?.rating ? stars(lib.rating) : '',
  ].join('');
  const ingredients = snap.ingredients.length
    ? `<ul class="ing-rows">${snap.ingredients.map(i => {
      const amount = scaledAmount(i, factor);
      return `<li><span>${esc(capitalize(i.name || i.raw))}</span><span class="muted">${esc(amount ?? '')}</span></li>`;
    }).join('')}</ul>`
    : '<p class="muted">Geen ingrediënten.</p>';
  const stepList = steps.length ? `<section class="card-sec">
      <h4 class="card-title">Bereiding</h4>
      <ol class="step-rows">${steps.slice(0, 2).map((t, i) => `<li><span class="step-n">${i + 1}</span><span>${esc(t)}</span></li>`).join('')}
        ${steps.length > 2 ? `<li class="muted"><span class="step-n more">…</span><span>Nog ${steps.length - 2} ${steps.length - 2 === 1 ? 'stap' : 'stappen'} in de kookmodus</span></li>` : ''}</ol>
    </section>` : '';
  return `${photo ? `<img class="dish-photo" src="${esc(photo)}" alt="">` : ''}
    <h3 class="dish-title">${esc(snap.name)}</h3>
    <div class="chips">${chips}</div>
    ${day.note ? `<p class="note-box">${esc(day.note)}</p>` : ''}
    ${snap.note ? `<p class="note-box">${esc(snap.note)}</p>` : ''}
    <section class="card-sec">
      <div class="ing-head"><h4 class="card-title">Ingrediënten</h4>
        <div class="portions">
          <button class="icon-btn small" data-action="portions" data-delta="-1" aria-label="Minder personen" ${portions <= 1 ? 'disabled' : ''}>${icon('minus')}</button>
          <strong>${portions} ${portions === 1 ? 'persoon' : 'personen'}</strong>
          <button class="icon-btn small" data-action="portions" data-delta="1" aria-label="Meer personen" ${portions >= 12 ? 'disabled' : ''}>${icon('plus')}</button>
        </div></div>${ingredients}
    </section>
    ${stepList}
    <div class="sticky-actions">
      <button class="btn primary full big" data-action="cook">${icon('play')}Start met koken</button>
      <button class="btn full" data-action="options">${icon('more')}Wijzigen, wisselen of leegmaken</button>
    </div>`;
}

function openDayOptions(dateId, onAct) {
  openSheet({
    title: 'Wijzigen',
    render: () => {
      const day = state.days[dateId];
      const lib = state.dishes.find(d => d.id === day?.dishId);
      const past = S.isPastWeek();
      const url = day?.dishSnapshot?.recipeUrl;
      return `<div class="option-list">
        ${past ? `<button class="list-row" data-action="copy">${icon('calendarCopy')}<span class="grow-text">Kopieer naar huidige week</span></button>` : ''}
        <button class="list-row" data-action="choose">${icon('book')}<span class="grow-text">Ander gerecht kiezen</span></button>
        <button class="list-row" data-action="pick-for-me">${icon('dice')}<span class="grow-text">Kies voor mij (vervangen)</span></button>
        ${lib ? `<button class="list-row" data-action="edit-dish">${icon('edit')}<span class="grow-text">Gerecht bewerken</span></button>` : ''}
        ${url ? `<a class="list-row" href="${esc(url)}" target="_blank" rel="noopener">${icon('link')}<span class="grow-text">Receptlink openen</span></a>` : ''}
        ${past ? '' : `<button class="list-row" data-action="swap">${icon('swap')}<span class="grow-text">Dag wisselen</span></button>`}
        <button class="list-row danger-row" data-action="clear">${icon('trash')}<span class="grow-text">Dag leegmaken</span></button>
      </div>`;
    },
    onAction: (a, el) => onAct(a, el),
  });
}

function emptyOrMarkerDay(day, kind, markerKind, markerNote, past) {
  const current = kind !== 'leeg' && kind !== 'koken'
    ? `<div class="marker-now">${icon(KIND_ICON[kind])}<div><strong>${esc(DAY_KINDS[kind])}</strong>${day.note ? `<div class="muted">${esc(day.note)}</div>` : ''}</div></div>` : '';
  const chosen = markerKind;
  return `${current}
    <div class="action-grid">
      <button class="btn primary" data-action="pick-for-me">${icon('dice')}Kies voor mij</button>
      <button class="btn" data-action="choose">${icon('book')}Gerecht uit bibliotheek kiezen</button>
      <button class="btn" data-action="new-dish">${icon('plus')}Nieuw gerecht ingeven</button>
    </div>
    <h4 class="section-label">Of markeer zonder koken</h4>
    <div class="chips">${['afhaal', 'opwarm', 'niet_koken'].map(k =>
      `<button class="chip-btn${chosen === k ? ' on' : ''}" data-action="marker" data-kind="${k}">${icon(KIND_ICON[k])}${esc(DAY_KINDS[k])}</button>`).join('')}</div>
    ${chosen ? `<label class="field"><span>Optionele notitie</span>
        <input id="marker-note" type="text" maxlength="200" value="${esc(markerNote)}" placeholder="bv. Frituur, Restje lasagne, Uit eten"></label>
        <button class="btn primary full" data-action="save-marker">Opslaan als ${esc(DAY_KINDS[chosen])}</button>` : ''}
    ${kind !== 'leeg' ? `<div class="action-grid">
      ${past ? '' : `<button class="btn swap" data-action="swap">${icon('swap')}Dag wisselen</button>`}
      <button class="btn danger-ghost" data-action="clear">${icon('trash')}Dag leegmaken</button></div>` : ''}`;
}

function describe(day) {
  const kind = kindOf(day);
  if (kind === 'koken') return day.dishSnapshot?.name ?? 'Koken';
  if (kind === 'leeg') return 'Leeg';
  return DAY_KINDS[kind];
}

function openSwapSheet(dateId) {
  openSheet({
    title: 'Dag wisselen',
    render: () => `<p class="lead">${esc(dayName(dateId))} (${esc(describe(state.days[dateId]))}) wisselen met:</p>
      <div class="list">${weekDates(S.state.selectedWeekStart).filter(id => id !== dateId).map(id =>
        `<button class="list-row" data-action="swap-with" data-date="${id}"><span><strong>${esc(dayName(id))}</strong> ${esc(shortBelgian(id))}</span><span class="muted">${esc(describe(state.days[id]))}</span></button>`).join('')}</div>`,
    onAction: (a, el) => { if (a === 'swap-with') { S.swapDays(dateId, el.dataset.date); closeAllSheets(); } },
  });
}

function openCopySheet(sourceDay) {
  openSheet({
    title: 'Kopieer naar huidige week',
    render: () => {
      const empty = weekDates(state.currentWeekStart).filter(id => kindOf(state.days[id]) === 'leeg');
      if (!empty.length) return '<p class="lead">Er zijn geen lege dagen meer in de huidige week. Maak eerst een dag leeg.</p>';
      return `<p class="lead">Kies een lege dag in de huidige week voor '${esc(sourceDay.dishSnapshot.name)}':</p>
        <div class="list">${empty.map(id => `<button class="list-row" data-action="copy-to" data-date="${id}"><strong>${esc(dayName(id))}</strong><span class="muted">${esc(belgian(id))}</span></button>`).join('')}</div>`;
    },
    onAction: (a, el) => { if (a === 'copy-to') { S.copyToCurrentWeek(sourceDay, el.dataset.date); closeAllSheets(); } },
  });
}

