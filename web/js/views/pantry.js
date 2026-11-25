// Voorraad: wat in huis is komt niet op de boodschappenlijst; geplande gerechten trekken het af.
import { esc, icon, openSheet, closeSheet, confirmSheet, refreshSheets } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { groupByDepartment } from '../logic/departments.js';
import { capitalize, formatQuantity, formatNumber, parseAmount } from '../logic/units.js';
import { isLow, pantryKey, PANTRY_UNITS } from '../logic/pantry.js';

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
  for (const [u, f] of Object.entries(p.altUnits ?? {})) sub.push(linkText(p.unit, u, f));
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

const linkText = (main, unit, factor) => `1 ${main ?? 'stuk'} = ${formatNumber(factor)} ${unit}`;

function unitOptions(selected, exclude = []) {
  const list = [...new Set([...(selected ? [selected] : []), ...PANTRY_UNITS])].filter(u => u === selected || !exclude.includes(u));
  return list.map(u => `<option value="${esc(u)}"${u === selected ? ' selected' : ''}>${esc(u)}</option>`).join('');
}

/** Uitleg bij een andere hoofdeenheid: omgerekend via een koppeling naar de oude eenheid, of hetzelfde getal. */
function unitChangeNote(item, f) {
  const total = (item.quantity ?? 0) + (S.reservedByKey()[pantryKey(item)] ?? 0);
  const link = f.alt.find(a => a.unit === (item.unit ?? 'stuk'));
  return link
    ? `Wordt omgerekend: ${formatNumber(total)} ${item.unit} = ${formatNumber(total / link.factor)} ${f.unit} (vóór aftrek van wat gepland is).`
    : `Het getal blijft hetzelfde: ${formatNumber(total)} ${f.unit} (vóór aftrek van wat gepland is). Koppel ${item.unit} hieronder om om te rekenen.`;
}

/** Minimum zoals getoond: bij een andere hoofdeenheid met koppeling naar de oude omgerekend, tot je het zelf aanpast. */
function minValue(item, f) {
  if (f.minTouched || f.unit === (item.unit ?? 'stuk') || item.minQuantity == null) return f.min;
  const link = f.alt.find(a => a.unit === (item.unit ?? 'stuk'));
  return link ? formatNumber(item.minQuantity / link.factor) : f.min;
}

function openEdit(item) {
  const f = {
    add: '', exact: '', min: item.minQuantity != null ? formatNumber(item.minQuantity) : '',
    unit: item.unit ?? 'stuk',
    alt: Object.entries(item.altUnits ?? {}).map(([unit, factor]) => ({ unit, factor })),
    newFactor: '', newUnit: '', error: '', minTouched: false,
  };
  const tracked = item.quantity != null;
  openSheet({
    title: capitalize(item.name),
    render: () => {
      const cur = state.pantry.find(p => p.id === item.id) ?? item;
      const reserved = S.reservedByKey()[pantryKey(cur)] ?? 0;
      const u = ` (${f.unit})`;
      const taken = [f.unit, ...f.alt.map(a => a.unit)];
      const newUnit = f.newUnit && !taken.includes(f.newUnit) ? f.newUnit : PANTRY_UNITS.find(x => !taken.includes(x));
      f.newUnit = newUnit;
      return `<p class="lead">Vrij: <strong>${esc(freeText(cur))}</strong>${reserved > 0.0001 ? ` · gepland: ${esc(formatQuantity(reserved, cur.unit))}` : ''}</p>
      <form class="form" data-form="edit">
        ${tracked ? `<label class="field"><span>Eenheid</span><select id="pe-unit">${unitOptions(f.unit)}</select>
          ${f.unit !== (item.unit ?? 'stuk') ? `<small class="muted">${esc(unitChangeNote(item, f))}</small>` : ''}</label>` : ''}
        <label class="field"><span>Aanvullen met${esc(tracked ? u : '')}</span><input id="pe-add" value="${esc(f.add)}" inputmode="decimal" placeholder="bv. 500">
          <small class="muted">Negatief = verbruikt, bv. -200</small></label>
        <label class="field"><span>Of exact vrij instellen op${esc(tracked ? u : '')}</span><input id="pe-exact" value="${esc(f.exact)}" inputmode="decimal"></label>
        <label class="field"><span>Minimumvoorraad${esc(tracked ? u : '')}</span><input id="pe-min" value="${esc(minValue(item, f))}" inputmode="decimal">
          <small class="muted">Melding als het minder wordt. Leeg = pas als het op is.</small></label>
        ${tracked ? `<div class="field"><span>Gekoppelde eenheden</span>
          <small class="muted">Ook aftrekken als een gerecht dit product in een andere eenheid gebruikt.</small>
          <div class="list">${f.alt.map((a, i) => `<div class="list-row"><span>${esc(linkText(f.unit, a.unit, a.factor))}</span>
            <button type="button" class="icon-btn danger" data-action="alt-del" data-i="${i}" aria-label="Koppeling weg">${icon('trash')}</button></div>`).join('')}</div>
          ${newUnit ? `<div class="link-row"><span class="link-eq">1 ${esc(f.unit)} =</span>
            <input id="pe-alt-factor" value="${esc(f.newFactor)}" inputmode="decimal" placeholder="bv. 400">
            <select id="pe-alt-unit">${unitOptions(newUnit, taken)}</select>
            <button type="button" class="btn" data-action="alt-add" ${parseAmount(f.newFactor) > 0 ? '' : 'disabled'}>${icon('plus')}Koppelen</button></div>` : ''}
          ${f.error ? `<p class="error">${esc(f.error)}</p>` : ''}</div>` : ''}
        <div class="row-end">
          <button type="button" class="btn danger-ghost" data-action="delete">${icon('trash')}Verwijderen</button>
          <button type="submit" class="btn primary">Opslaan</button>
        </div>
      </form>`;
    },
    onInput: el => {
      if (el.id === 'pe-add') f.add = el.value;
      else if (el.id === 'pe-exact') f.exact = el.value;
      else if (el.id === 'pe-min') { f.min = el.value; f.minTouched = true; }
      else if (el.id === 'pe-unit') { f.unit = el.value; f.alt = f.alt.filter(a => a.unit !== f.unit); refreshSheets(); }
      else if (el.id === 'pe-alt-unit') f.newUnit = el.value;
      else if (el.id === 'pe-alt-factor') { const had = parseAmount(f.newFactor) > 0; f.newFactor = el.value; f.error = ''; if (had !== parseAmount(el.value) > 0) refreshSheets(); }
    },
    onAction: (a, el) => {
      if (a === 'alt-add') {
        const factor = parseAmount(f.newFactor);
        if (!(factor > 0)) { f.error = 'Geef een getal groter dan 0.'; refreshSheets(); return; }
        f.alt.push({ unit: f.newUnit, factor });
        f.newFactor = ''; f.newUnit = '';
        refreshSheets();
      } else if (a === 'alt-del') { f.alt.splice(Number(el.dataset.i), 1); refreshSheets(); }
      else if (a === 'delete') confirmSheet({
        title: 'Uit voorraad verwijderen', message: `${capitalize(item.name)} uit de voorraad verwijderen?`,
        onConfirm: () => { S.deletePantry(item); closeSheet(); },
      });
    },
    onSubmit: () => {
      const cur = state.pantry.find(p => p.id === item.id) ?? item;
      if (tracked) S.updatePantry(cur, f.add, f.exact, minValue(item, f), f.unit, Object.fromEntries(f.alt.map(a => [a.unit, a.factor])));
      else S.updatePantry(cur, f.add, f.exact, f.min);
      closeSheet();
    },
  });
}
