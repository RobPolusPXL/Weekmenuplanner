// Gerechtenbibliotheek: zoeken, filteren op type, sterren, bewerken en types beheren.
import { esc, icon, stars, openSheet, closeSheet, refreshSheets, confirmSheet } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { parseIngredient } from '../logic/parser.js';
import { formatQuantity, capitalize } from '../logic/units.js';
import { importRecipe } from '../logic/recipe.js';
import { dishMeta, emptyState } from './common.js';

let search = '';
let typeFilter = '';

function matches(dish, q, type) {
  if (type && dish.type.toLowerCase() !== type) return false;
  if (!q) return true;
  const needle = q.toLowerCase();
  return dish.name.toLowerCase().includes(needle) || dish.ingredients.some(i => (i.name || i.raw).toLowerCase().includes(needle));
}

function filterBar(idPrefix, q, type) {
  return `<div class="filter">
    <label class="search">${icon('search')}<input id="${idPrefix}-search" type="search" value="${esc(q)}" placeholder="Zoek op naam of ingrediënt…" autocomplete="off"></label>
    <div class="chips scroll-x">
      <button class="chip-btn${type ? '' : ' on'}" data-action="filter-type" data-type="">Alle types</button>
      ${S.dishTypes().map(t => `<button class="chip-btn${type === t.toLowerCase() ? ' on' : ''}" data-action="filter-type" data-type="${esc(t.toLowerCase())}">${esc(t)}</button>`).join('')}
    </div></div>`;
}

export function render() {
  const list = state.dishes.filter(d => matches(d, search, typeFilter));
  const body = !state.dishes.length
    ? emptyState('book', 'Nog geen gerechten', 'Voeg jullie vaste gerechten toe. Daarna kan de app de week voor jullie invullen.',
      `<button class="btn primary" data-action="new">${icon('plus')}Eerste gerecht toevoegen</button>`)
    : !list.length
      ? `<p class="muted center pad">Geen gerechten gevonden.</p>`
      : `<div class="dish-grid">${list.map(dishCard).join('')}</div>`;
  return `<div class="view view-dishes">
    <div class="view-head">
      <div><h1 class="title">Gerechten</h1><p class="sub">${state.dishes.length} in jullie bibliotheek</p></div>
      <div class="head-actions">
        <button class="btn ghost" data-action="types">${icon('tag')}Types</button>
        <button class="btn primary" data-action="new">${icon('plus')}Nieuw</button>
      </div>
    </div>
    ${state.dishes.length ? filterBar('dishes', search, typeFilter) : ''}
    ${body}
  </div>`;
}

function dishCard(d) {
  return `<article class="dish-card">
    <button class="dish-card-main" data-action="edit" data-id="${esc(d.id)}">
      ${d.photoUrl ? `<img class="dish-thumb" src="${esc(d.photoUrl)}" alt="">` : `<span class="dish-thumb placeholder">${icon('pot')}</span>`}
      <span class="dish-card-text"><span class="dish-name">${esc(d.name)}</span>
      ${dishMeta(d, { withStars: false })}
      <span class="muted small">${d.ingredients.length} ingrediënten</span></span>
    </button>
    <div class="dish-card-rate">${stars(d.rating, 'rate', `data-id="${esc(d.id)}"`)}</div>
  </article>`;
}

export function onAction(action, el) {
  if (action === 'new') openDishEditor(null);
  else if (action === 'edit') openDishEditor(state.dishes.find(d => d.id === el.dataset.id));
  else if (action === 'rate') {
    const dish = state.dishes.find(d => d.id === el.dataset.id);
    if (dish) S.rateDish(dish, Number(el.dataset.rating));
  } else if (action === 'filter-type') { typeFilter = el.dataset.type; rerender(); }
  else if (action === 'types') openTypesSheet();
}

export function onInput(el) {
  if (el.id === 'dishes-search') { search = el.value; rerender(); }
}

let rerender = () => {};
export function setRerender(fn) { rerender = fn; }

// ---------- Gerecht kiezen (vanuit een dag) ----------

export function openDishPicker(onPick) {
  let q = '';
  let type = '';
  openSheet({
    title: 'Kies een gerecht',
    wide: true,
    render: () => {
      if (!state.dishes.length) return '<p class="lead">Nog geen gerechten in de bibliotheek.</p>';
      const list = state.dishes.filter(d => matches(d, q, type));
      return `${filterBar('picker', q, type)}
        <div class="list">${list.map(d => `<button class="list-row pick" data-action="pick" data-id="${esc(d.id)}">
          <span><strong>${esc(d.name)}</strong>${dishMeta(d)}</span><span class="muted small">${d.ingredients.length} ingr.</span></button>`).join('')
          || '<p class="muted center pad">Geen gerechten gevonden.</p>'}</div>`;
    },
    onInput: el => { if (el.id === 'picker-search') { q = el.value; refreshSheets(); } },
    onAction: (a, el) => {
      if (a === 'filter-type') { type = el.dataset.type; refreshSheets(); }
      else if (a === 'pick') onPick(state.dishes.find(d => d.id === el.dataset.id));
    },
  });
}

// ---------- Gerecht bewerken / nieuw ----------

const MAX_PHOTO_CHARS = 340000;

/** Verkleint een foto tot een data-URL die in Firestore past (zoals PhotoUtils in de Android-app). */
async function photoToDataUrl(file) {
  const bitmap = await createImageBitmap(file);
  for (const max of [720, 600, 480, 360]) {
    const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.82, 0.7, 0.58]) {
      const url = canvas.toDataURL('image/jpeg', quality);
      if (url.length <= MAX_PHOTO_CHARS) return url;
    }
  }
  throw new Error('Deze foto is te groot.');
}

export function openDishEditor(dish, { assignToDateId = null, onSaved = null } = {}) {
  const f = {
    name: dish?.name ?? '',
    type: dish?.type ?? (S.dishTypes()[0] ?? ''),
    newType: '',
    ingredientsText: (dish?.ingredients ?? []).map(i => i.raw || [formatQuantity(i.quantity, i.unit), i.name].filter(Boolean).join(' ')).join('\n'),
    recipeUrl: dish?.recipeUrl ?? '',
    note: dish?.note ?? '',
    photoUrl: dish?.photoUrl ?? '',
    prep: dish?.prepMinutes ? String(dish.prepMinutes) : '',
    importing: false,
    saving: false,
  };
  const original = new Map((dish?.ingredients ?? []).map(i => [i.raw, i]));

  const lines = () => f.ingredientsText.split('\n').map(l => l.trim()).filter(Boolean);
  // Ongewijzigde regels behouden hun bestaande verwerking; nieuwe regels gaan door de parser.
  const ingredients = () => lines().map(l => {
    const o = original.get(l);
    return o && o.name ? o : parseIngredient(l);
  });

  openSheet({
    title: dish ? 'Gerecht bewerken' : 'Nieuw gerecht',
    wide: true,
    render: () => {
      const types = S.dishTypes();
      const parsed = ingredients();
      return `<form class="form" id="dish-form" novalidate>
        <label class="field"><span>Naam</span><input id="f-name" name="name" required maxlength="120" value="${esc(f.name)}" ${dish ? '' : 'autofocus'}></label>
        <div class="two">
          <label class="field"><span>Type</span><select id="f-type" name="type">
            ${types.map(t => `<option value="${esc(t)}"${t.toLowerCase() === f.type.toLowerCase() ? ' selected' : ''}>${esc(t)}</option>`).join('')}
            <option value="__new"${f.type === '__new' ? ' selected' : ''}>+ Nieuw type…</option></select></label>
          <label class="field"><span>Bereidingstijd (min)</span><input id="f-prep" name="prep" inputmode="numeric" pattern="[0-9]*" value="${esc(f.prep)}" placeholder="bv. 30"></label>
        </div>
        ${f.type === '__new' ? `<label class="field"><span>Nieuw type</span><input id="f-newtype" name="newType" maxlength="40" value="${esc(f.newType)}" placeholder="bv. stoofpot"></label>` : ''}
        <label class="field"><span>Ingrediënten <em class="muted">(één per regel, bv. "500 g gehakt")</em></span>
          <textarea id="f-ing" name="ingredients" rows="7">${esc(f.ingredientsText)}</textarea></label>
        ${parsed.length ? `<div class="parsed">${parsed.map(i => `<span class="chip">${esc(capitalize(i.name))}${i.quantity != null ? ` · ${esc(formatQuantity(i.quantity, i.unit))}` : ''}</span>`).join('')}</div>` : ''}
        <label class="field"><span>Receptlink</span>
          <div class="input-row"><input id="f-url" name="recipeUrl" type="url" inputmode="url" value="${esc(f.recipeUrl)}" placeholder="https://…">
          <button type="button" class="btn ghost" data-action="import" ${f.importing ? 'disabled' : ''}>${f.importing ? 'Bezig…' : 'Importeer'}</button></div></label>
        <label class="field"><span>Notitie</span><textarea id="f-note" name="note" rows="2" maxlength="2000">${esc(f.note)}</textarea></label>
        <div class="field"><span>Foto</span>
          <div class="photo-row">${f.photoUrl ? `<img class="photo-preview" src="${esc(f.photoUrl)}" alt="">` : ''}
            <label class="btn ghost">${icon('camera')}${f.photoUrl ? 'Andere foto' : 'Foto kiezen'}<input id="f-photo" type="file" accept="image/*" hidden></label>
            ${f.photoUrl ? '<button type="button" class="btn danger-ghost" data-action="remove-photo">Foto weg</button>' : ''}</div></div>
        ${dish ? `<div class="field"><span>Waardering</span>${stars(dish.rating, 'rate-in-editor')}</div>` : ''}
        <div class="row-end sticky-actions">
          ${dish ? `<button type="button" class="btn danger-ghost" data-action="delete">${icon('trash')}Verwijderen</button>` : ''}
          <button type="submit" class="btn primary" ${f.saving ? 'disabled' : ''}>${f.saving ? 'Opslaan…' : 'Opslaan'}</button>
        </div>
      </form>`;
    },
    onInput: async el => {
      if (el.id === 'f-name') f.name = el.value;
      else if (el.id === 'f-type') { f.type = el.value; refreshSheets(); }
      else if (el.id === 'f-newtype') f.newType = el.value;
      else if (el.id === 'f-prep') f.prep = el.value.replace(/\D/g, '');
      else if (el.id === 'f-ing') { f.ingredientsText = el.value; refreshSheets(); }
      else if (el.id === 'f-url') f.recipeUrl = el.value;
      else if (el.id === 'f-note') f.note = el.value;
      else if (el.id === 'f-photo' && el.files?.[0]) {
        try { f.photoUrl = await photoToDataUrl(el.files[0]); refreshSheets(); } catch (e) { S.toast(e.message || 'Kon foto niet laden.'); }
      }
    },
    onAction: async (a, el) => {
      if (a === 'remove-photo') { f.photoUrl = ''; refreshSheets(); }
      else if (a === 'rate-in-editor' && dish) S.rateDish(dish, Number(el.dataset.rating));
      else if (a === 'delete') confirmSheet({
        title: 'Gerecht verwijderen', message: `'${dish.name}' verwijderen? Bestaande weekplanningen blijven behouden.`,
        onConfirm: () => { S.deleteDish(dish); closeSheet(); },
      });
      else if (a === 'import') {
        f.importing = true; refreshSheets();
        try {
          const r = await importRecipe(f.recipeUrl);
          if (!f.name.trim() && r.name) f.name = r.name;
          f.ingredientsText = r.ingredientLines.join('\n');
          if (r.totalMinutes && !f.prep) f.prep = String(r.totalMinutes);
          f.recipeUrl = r.url;
          S.toast(`${r.ingredientLines.length} ingrediënten geïmporteerd.`);
        } catch (e) { S.toast(e.message); }
        f.importing = false; refreshSheets();
      }
    },
    onSubmit: async () => {
      const type = f.type === '__new' ? f.newType : f.type;
      const prep = f.prep ? Number(f.prep) : null;
      f.saving = true; refreshSheets();
      const ok = await S.saveDish(dish?.id ?? null, {
        name: f.name, type, ingredients: ingredients(), recipeUrl: f.recipeUrl, note: f.note,
        photoUrl: f.photoUrl, prepMinutes: Number.isInteger(prep) ? prep : null, rating: dish?.rating ?? 0,
      }, assignToDateId);
      f.saving = false;
      if (ok) { closeSheet(); onSaved?.(); } else refreshSheets();
    },
  });
}

// ---------- Types beheren ----------

function openTypesSheet() {
  let editing = null;
  let draft = '';
  let newType = '';
  openSheet({
    title: 'Gerechttypes',
    render: () => `<div class="list">${S.dishTypes().map(t => {
      const used = state.dishes.filter(d => d.type.toLowerCase() === t.toLowerCase()).length;
      if (editing === t) {
        return `<form class="list-row edit-row" data-form="rename"><input id="type-rename" value="${esc(draft)}" maxlength="40" autofocus>
          <button class="btn primary" type="submit">Opslaan</button><button class="btn ghost" type="button" data-action="cancel">Annuleren</button></form>`;
      }
      return `<div class="list-row"><span><strong>${esc(t)}</strong> <span class="muted small">${used} gerecht${used === 1 ? '' : 'en'}</span></span>
        <span class="row-actions"><button class="icon-btn" data-action="rename" data-type="${esc(t)}" aria-label="Hernoemen">${icon('edit')}</button>
        <button class="icon-btn danger" data-action="delete" data-type="${esc(t)}" aria-label="Verwijderen">${icon('trash')}</button></span></div>`;
    }).join('')}</div>
    <form class="input-row top-gap" data-form="add"><input id="type-new" value="${esc(newType)}" maxlength="40" placeholder="Nieuw type">
      <button class="btn primary" type="submit">${icon('plus')}Toevoegen</button></form>`,
    onInput: el => { if (el.id === 'type-rename') draft = el.value; if (el.id === 'type-new') newType = el.value; },
    onAction: (a, el) => {
      if (a === 'rename') { editing = el.dataset.type; draft = editing; refreshSheets(); }
      else if (a === 'cancel') { editing = null; refreshSheets(); }
      else if (a === 'delete') S.deleteType(el.dataset.type);
    },
    onSubmit: form => {
      if (form.dataset.form === 'rename') { S.renameType(editing, draft); editing = null; refreshSheets(); }
      else { S.addType(newType); newType = ''; refreshSheets(); }
    },
  });
}
