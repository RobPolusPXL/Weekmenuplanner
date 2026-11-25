// Weekplanning: zeven dagen, per dag koken (gerecht), afhaal, opwarm of niet koken.
import { esc, icon, openSheet, closeSheet, closeAllSheets, confirmSheet, refreshSheets } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { today, dayName, belgian, shortBelgian, weekDates } from '../logic/dates.js';
import { DAY_KINDS, kindOf } from '../logic/planner.js';
import { capitalize } from '../logic/units.js';
import { weekNav, dishMeta, typeColor } from './common.js';
import { openDishEditor, openDishPicker } from './dishes.js';
import { openCookMode } from './cook.js';
import { portionFactor, scaledAmount, DEFAULT_SERVINGS } from '../logic/cooking.js';

const KIND_ICON = { afhaal: 'bag', opwarm: 'pot', niet_koken: 'moon' };

export function render() {
  const t = today();
  const past = S.isPastWeek();
  const cards = S.selectedWeekDates().map(id => dayCard(id, state.days[id], id === t, past)).join('');
  return `<div class="view view-week">
    <h1 class="sr-only">Weekmenu</h1>
    <section class="week-card">
      ${weekNav()}
      ${past ? '' : `<button class="btn primary full" data-action="generate">${icon('dice')}Genereer hele week</button>`}
    </section>
    <div class="days">${cards}</div>
  </div>`;
}

// Zoals in de Android-app: dagnaam + datum (+ Vandaag), daaronder het gerecht of de markering, rechts "Kies voor mij".
function dayCard(id, day, isToday, pastWeek) {
  const kind = kindOf(day);
  let body;
  if (kind === 'koken' && day.dishSnapshot) {
    const snap = day.dishSnapshot;
    body = `<span class="day-dish">${esc(snap.name)}</span>${snap.type ? `<span class="chip type" data-c="${typeColor(snap.type)}">${esc(snap.type)}</span>` : ''}`;
  } else if (kind !== 'leeg') {
    body = `<span class="day-marker">${icon(KIND_ICON[kind])}${esc(DAY_KINDS[kind])}</span>${day?.note ? `<span class="day-note">${esc(day.note)}</span>` : ''}`;
  } else {
    body = '<span class="day-empty">Nog leeg — tik om te plannen</span>';
  }
  return `<div class="day kind-${kind}${isToday ? ' today' : ''}">
    <div class="day-row">
      <button class="day-main" data-action="open-day" data-date="${id}">
        <span class="day-head"><span class="day-name">${esc(dayName(id))}</span><span class="day-date">${esc(belgian(id))}</span>
          ${isToday ? '<span class="badge-today">Vandaag</span>' : ''}</span>
        ${body}
      </button>
      <button class="btn tonal small" data-action="pick-day" data-date="${id}">${icon('dice')}Kies voor mij</button>
    </div>
    ${pastWeek && kind === 'koken' ? `<div class="day-foot"><button class="link-btn" data-action="copy-day" data-date="${id}">${icon('calendarCopy')}Kopieer naar huidige week</button></div>` : ''}
  </div>`;
}

export function onAction(action, el) {
  if (action === 'generate') S.generateWeek();
  else if (action === 'open-day') openDaySheet(el.dataset.date);
  else if (action === 'pick-day') S.pickForDay(el.dataset.date);
  else if (action === 'copy-day') { const d = state.days[el.dataset.date]; if (d) openCopySheet(d); }
}

// ---------- Paneel voor één dag ----------

function openDaySheet(dateId) {
  let markerKind = null;
  let markerNote = '';
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
    onAction: (a, el) => {
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
    },
  });
}

function cookingDay(day, past) {
  const snap = day.dishSnapshot;
  const lib = state.dishes.find(d => d.id === day.dishId);
  const photo = snap.photoUrl || lib?.photoUrl;
  const servings = lib?.servings ?? snap.servings ?? DEFAULT_SERVINGS;
  const portions = snap.portions ?? servings;
  const factor = portionFactor(portions, servings);
  const ingredients = snap.ingredients.length
    ? `<ul class="ing-list">${snap.ingredients.map(i => {
      const amount = scaledAmount(i, factor);
      return `<li>${esc(capitalize(i.name || i.raw))}${amount ? `<span class="muted"> · ${esc(amount)}</span>` : ''}</li>`;
    }).join('')}</ul>`
    : '<p class="muted">Geen ingrediënten.</p>';
  return `${photo ? `<img class="dish-photo" src="${esc(photo)}" alt="">` : ''}
    <h3 class="dish-title">${esc(snap.name)}</h3>
    ${dishMeta({ ...snap, prepMinutes: lib?.prepMinutes, rating: lib?.rating ?? 0 })}
    ${day.note ? `<p class="note-box">${esc(day.note)}</p>` : ''}
    ${snap.note ? `<p class="note-box">${esc(snap.note)}</p>` : ''}
    <button class="btn primary full cook-btn" data-action="cook">${icon('chef')}Kookmodus${lib?.steps?.length ? ` · ${lib.steps.length} stappen` : ''}</button>
    <div class="ing-head"><h4 class="section-label">Ingrediënten</h4>
      <div class="portions"><span>Voor</span>
        <button class="icon-btn small" data-action="portions" data-delta="-1" aria-label="Minder personen" ${portions <= 1 ? 'disabled' : ''}>${icon('minus')}</button>
        <strong>${portions}</strong>
        <button class="icon-btn small" data-action="portions" data-delta="1" aria-label="Meer personen" ${portions >= 12 ? 'disabled' : ''}>${icon('plus')}</button>
        <span>${portions === 1 ? 'persoon' : 'personen'}</span></div></div>${ingredients}
    ${snap.recipeUrl ? `<a class="btn ghost full" href="${esc(snap.recipeUrl)}" target="_blank" rel="noopener">${icon('link')}Open receptlink</a>` : ''}
    <div class="action-grid">
      ${past ? `<button class="btn primary" data-action="copy">${icon('calendarCopy')}Kopieer naar huidige week</button>` : ''}
      <button class="btn" data-action="choose">${icon('book')}Ander gerecht kiezen</button>
      <button class="btn" data-action="pick-for-me">${icon('dice')}Kies voor mij (vervangen)</button>
      ${lib ? `<button class="btn" data-action="edit-dish">${icon('edit')}Gerecht bewerken</button>` : ''}
      ${past ? '' : `<button class="btn swap" data-action="swap">${icon('swap')}Dag wisselen</button>`}
      <button class="btn danger-ghost" data-action="clear">${icon('trash')}Dag leegmaken</button>
    </div>`;
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

