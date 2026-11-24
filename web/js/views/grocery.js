// Boodschappenlijst: samengeteld uit de geplande gerechten, per afdeling, plus losse items.
import { esc, icon } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { groupByDepartment } from '../logic/departments.js';
import { capitalize, formatQuantity } from '../logic/units.js';
import { weekNav } from './common.js';

let loose = { name: '', qty: '', unit: '', recurring: false };

export function render() {
  const { toBuy, fromStock } = S.groceryList();
  const cookDays = S.cookingDaysInWeek();
  const checkedCount = toBuy.filter(i => i.checked).length;

  let planned;
  if (!toBuy.length) {
    planned = `<p class="muted pad">${fromStock.length ? 'Alles voor de geplande gerechten zit in de voorraad.'
      : cookDays ? 'De geplande gerechten hebben geen ingrediënten.' : 'Nog geen kookdagen gepland in deze week.'}</p>`;
  } else {
    planned = groupByDepartment(toBuy, i => i.name).map(([key, label, items]) => `
      <div class="dept"><h3 class="dept-head"><span>${esc(label)}</span><span class="count">${items.filter(i => i.checked).length}/${items.length}</span></h3>
      ${items.map(groceryRow).join('')}</div>`).join('');
  }

  const stock = fromStock.length ? `<section class="block">
      <h2 class="block-title">Uit voorraad <span class="count">${fromStock.length}</span></h2>
      <p class="muted small">Hoef je niet te kopen, wordt van de voorraad afgetrokken.</p>
      <div class="stock-list">${fromStock.map(i => `<div class="stock-row"><span>${esc(capitalize(i.name))}</span><span class="muted">${esc(i.fromStockText)}</span></div>`).join('')}</div>
    </section>` : '';

  const looseGroups = groupByDepartment(state.looseItems, i => i.name).map(([key, label, items]) => `
    <div class="dept"><h3 class="dept-head"><span>${esc(label)}</span><span class="count">${items.filter(i => i.checked).length}/${items.length}</span></h3>
    ${items.map(looseRow).join('')}</div>`).join('');

  return `<div class="view view-grocery">
    <div class="view-head"><div><h1 class="title">Boodschappen</h1>
      <p class="sub">${cookDays} kookdag${cookDays === 1 ? '' : 'en'} in deze week</p></div></div>
    ${weekNav()}
    <div class="grocery-cols">
      <div>
        <section class="block">
          <h2 class="block-title">Van geplande gerechten <span class="count">${toBuy.length}</span>
            ${toBuy.length ? `<span class="muted small right">${checkedCount}/${toBuy.length} afgevinkt</span>` : ''}</h2>
          ${planned}
        </section>
        ${stock}
      </div>
      <div>
        <section class="block">
          <h2 class="block-title">Losse items <span class="count">${state.looseItems.length}</span></h2>
          <form class="loose-form" data-form="loose">
            <input id="loose-name" placeholder="Wat moet er nog bij? bv. keukenrol" value="${esc(loose.name)}" maxlength="120" autocomplete="off">
            <div class="loose-form-row">
              <input id="loose-qty" placeholder="Aantal" inputmode="decimal" value="${esc(loose.qty)}">
              <input id="loose-unit" placeholder="Eenheid" value="${esc(loose.unit)}" maxlength="30">
              <label class="check-label"><input id="loose-recurring" type="checkbox" ${loose.recurring ? 'checked' : ''}>Vast</label>
              <button class="btn primary" type="submit" ${loose.name.trim() ? '' : 'disabled'}>${icon('plus')}Toevoegen</button>
            </div>
          </form>
          ${looseGroups || '<p class="muted pad">Nog geen losse items.</p>'}
        </section>
        <button class="btn finish full" data-action="finish" ${state.looseItems.some(i => i.checked) ? '' : 'disabled'}>${icon('check')}Winkelen afronden</button>
        <p class="muted small center">Afgevinkte eenmalige items verdwijnen, vaste items komen terug op de lijst.</p>
      </div>
    </div>
  </div>`;
}

function groceryRow(i) {
  return `<label class="g-row${i.checked ? ' done' : ''}">
    <input type="checkbox" data-grocery="${esc(i.itemKey)}" ${i.checked ? 'checked' : ''}>
    <span class="g-text"><span class="g-name">${esc(capitalize(i.name))}</span>
      ${i.fromStockText ? `<span class="g-stock">+ ${esc(i.fromStockText)} uit voorraad</span>` : ''}
      ${i.dishNames.length ? `<span class="g-dishes">${esc(i.dishNames.join(', '))}</span>` : ''}</span>
    ${i.displayAmount ? `<span class="amount">${esc(i.displayAmount)}</span>` : ''}
  </label>`;
}

function looseRow(i) {
  const amount = i.quantity != null ? formatQuantity(i.quantity, i.unit) : i.unit ?? '';
  return `<div class="g-row${i.checked ? ' done' : ''}">
    <label class="g-check"><input type="checkbox" data-loose="${esc(i.id)}" ${i.checked ? 'checked' : ''}>
    <span class="g-text"><span class="g-name">${esc(capitalize(i.name))}</span>${i.recurring ? '<span class="tag">vast</span>' : ''}</span></label>
    ${amount ? `<span class="amount">${esc(amount)}</span>` : ''}
    <button class="icon-btn small" data-action="delete-loose" data-id="${esc(i.id)}" aria-label="Verwijderen">${icon('trash')}</button>
  </div>`;
}

export function onAction(action, el) {
  if (action === 'finish') S.finishShopping();
  else if (action === 'delete-loose') S.deleteLoose(el.dataset.id);
}

export function onChangeEvent(el) {
  if (el.dataset.grocery) S.toggleGrocery(el.dataset.grocery, el.checked);
  else if (el.dataset.loose) S.toggleLoose(el.dataset.loose, el.checked);
  else if (el.id === 'loose-recurring') loose.recurring = el.checked;
}

export function onInput(el, rerender) {
  if (el.id === 'loose-name') { const had = loose.name.trim(); loose.name = el.value; if (!had !== !el.value.trim()) rerender(); }
  else if (el.id === 'loose-qty') loose.qty = el.value;
  else if (el.id === 'loose-unit') loose.unit = el.value;
}

export function onSubmit(form, rerender) {
  if (form.dataset.form !== 'loose' || !loose.name.trim()) return;
  S.addLooseItem(loose.name, loose.qty, loose.unit, loose.recurring);
  loose = { name: '', qty: '', unit: '', recurring: loose.recurring };
  rerender();
  setTimeout(() => document.getElementById('loose-name')?.focus(), 0);
}
