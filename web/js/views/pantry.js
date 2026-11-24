// Voorraad: wat in huis is komt niet op de boodschappenlijst; geplande gerechten trekken het af.
import { esc, icon, openSheet, closeSheet, confirmSheet } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { groupByDepartment } from '../logic/departments.js';
import { capitalize, formatQuantity, formatNumber } from '../logic/units.js';
import { isLow, pantryKey } from '../logic/pantry.js';

let add = { text: '', min: '' };

const freeText = item => (item.quantity == null ? 'in huis' : formatQuantity(item.quantity, item.unit) || '0');

export function render() {
  const low = S.lowStock();
  const reserved = S.reservedByKey();
  const lowCard = low.length ? `<section class="low-card">
      <h2>${icon('warning')}Bijna op <span class="count">${low.length}</span></h2>
      ${low.map(p => `<div class="low-row"><span><strong>${esc(capitalize(p.name))}</strong> — nog ${esc(freeText(p))}</span>
        <button class="btn small" data-action="to-list" data-id="${esc(p.id)}">${icon('addcart')}Op lijst</button></div>`).join('')}
    </section>` : '';

  const groups = groupByDepartment(state.pantry, p => p.name).map(([key, label, items]) => `
    <div class="dept"><h3 class="dept-head"><span>${esc(label)}</span></h3>
    ${items.map(p => pantryRow(p, reserved[pantryKey(p)] ?? 0)).join('')}</div>`).join('');

  return `<div class="view view-pantry">
    <div class="view-head"><div><h1 class="title">Voorraad</h1>
      <p class="sub">Wat in huis is komt niet op de boodschappenlijst. Geplande gerechten trekken het automatisch af.</p></div></div>
    <div class="pantry-cols">
      <div>
        ${lowCard}
        <form class="block add-card" data-form="pantry-add">
          <label class="field"><span>Product + hoeveelheid</span>
            <input id="pantry-text" value="${esc(add.text)}" placeholder="bv. 1 kg rijst, 6 eieren, zout" autocomplete="off"></label>
          <div class="input-row">
            <label class="field grow"><span>Minimumvoorraad (optioneel)</span>
              <input id="pantry-min" value="${esc(add.min)}" inputmode="decimal"></label>
            <button class="btn primary" type="submit" ${add.text.trim() ? '' : 'disabled'}>${icon('plus')}Toevoegen</button>
          </div>
          <p class="muted small">Bestaat het product al, dan wordt het aangevuld. Minimum in g, ml of stuk. Daaronder krijg je een melding. Zonder hoeveelheid (bv. "zout") telt iets als altijd in huis.</p>
        </form>
      </div>
      <div>${groups || (state.pantryLoaded ? '<p class="muted pad">Nog niets in de voorraad.</p>' : '')}</div>
    </div>
  </div>`;
}

function pantryRow(p, reserved) {
  const sub = [];
  if (reserved > 0.0001) sub.push(`${formatQuantity(reserved, p.unit)} gepland`);
  if (p.minQuantity != null) sub.push(`min. ${formatQuantity(p.minQuantity, p.unit)}`);
  return `<button class="p-row" data-action="edit" data-id="${esc(p.id)}">
    <span class="p-text"><span class="p-name">${esc(capitalize(p.name))}</span>${sub.length ? `<span class="muted small">${esc(sub.join(' · '))}</span>` : ''}</span>
    <span class="amount${isLow(p) ? ' low' : ''}">${esc(freeText(p))}</span>
  </button>`;
}

export function onAction(action, el) {
  const item = state.pantry.find(p => p.id === el.dataset.id);
  if (action === 'to-list' && item) S.lowStockToList(item);
  else if (action === 'edit' && item) openEdit(item);
}

export function onInput(el, rerender) {
  if (el.id === 'pantry-text') { const had = add.text.trim(); add.text = el.value; if (!had !== !el.value.trim()) rerender(); }
  else if (el.id === 'pantry-min') add.min = el.value;
}

export function onSubmit(form, rerender) {
  if (form.dataset.form !== 'pantry-add' || !add.text.trim()) return;
  S.addPantry(add.text, add.min);
  add = { text: '', min: '' };
  rerender();
  setTimeout(() => document.getElementById('pantry-text')?.focus(), 0);
}

function openEdit(item) {
  const unit = item.unit ? ` (${item.unit})` : '';
  const f = { add: '', exact: '', min: item.minQuantity != null ? formatNumber(item.minQuantity) : '' };
  openSheet({
    title: capitalize(item.name),
    render: () => {
      const cur = state.pantry.find(p => p.id === item.id) ?? item;
      const reserved = S.reservedByKey()[pantryKey(cur)] ?? 0;
      return `<p class="lead">Vrij: <strong>${esc(freeText(cur))}</strong>${reserved > 0.0001 ? ` · gepland: ${esc(formatQuantity(reserved, cur.unit))}` : ''}</p>
      <form class="form" data-form="edit">
        <label class="field"><span>Aanvullen met${esc(unit)}</span><input id="pe-add" value="${esc(f.add)}" inputmode="decimal" placeholder="bv. 500">
          <small class="muted">Negatief = verbruikt, bv. -200</small></label>
        <label class="field"><span>Of exact vrij instellen op${esc(unit)}</span><input id="pe-exact" value="${esc(f.exact)}" inputmode="decimal"></label>
        <label class="field"><span>Minimumvoorraad${esc(unit)}</span><input id="pe-min" value="${esc(f.min)}" inputmode="decimal">
          <small class="muted">Melding als het minder wordt. Leeg = pas als het op is.</small></label>
        <div class="row-end">
          <button type="button" class="btn danger-ghost" data-action="delete">${icon('trash')}Verwijderen</button>
          <button type="submit" class="btn primary">Opslaan</button>
        </div>
      </form>`;
    },
    onInput: el => {
      if (el.id === 'pe-add') f.add = el.value;
      else if (el.id === 'pe-exact') f.exact = el.value;
      else if (el.id === 'pe-min') f.min = el.value;
    },
    onAction: a => {
      if (a === 'delete') confirmSheet({
        title: 'Uit voorraad verwijderen', message: `${capitalize(item.name)} uit de voorraad verwijderen?`,
        onConfirm: () => { S.deletePantry(item); closeSheet(); },
      });
    },
    onSubmit: () => {
      const cur = state.pantry.find(p => p.id === item.id) ?? item;
      S.updatePantry(cur, f.add, f.exact, f.min);
      closeSheet();
    },
  });
}
