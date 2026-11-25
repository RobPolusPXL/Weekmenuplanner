// Meer: kleur van de app, huishouden en statistieken.
import { esc, icon } from '../ui.js';
import { state } from '../store.js';
import { ACCENTS, currentAccent, setAccent } from '../theme.js';
import { openHouseholdSheet } from './account.js';
import * as stats from './stats.js';

export function render() {
  const h = state.household;
  const cur = currentAccent();
  return `<div class="view view-more">
    <h1 class="title">Meer</h1>
    <section class="block">
      <h2 class="block-title">Kleur</h2>
      <div class="swatches" role="radiogroup" aria-label="Kleur van de app">${ACCENTS.map(a => `
        <button class="swatch${a.id === cur ? ' on' : ''}" role="radio" aria-checked="${a.id === cur}" data-action="accent" data-accent="${a.id}">
          <span class="swatch-dot" style="background:${a.color}">${a.id === cur ? icon('check') : ''}</span><span>${esc(a.label)}</span></button>`).join('')}
      </div>
      <p class="muted small">Geldt op dit toestel.</p>
    </section>
    <button class="list-row more-row" data-action="household">
      <span class="more-row-main">${icon('people')}<span><strong>Huishouden</strong><span class="muted small block-line">${h ? `${h.members.length}/2 leden · uitnodigingscode, weekstart, afmelden` : ''}</span></span></span>${icon('right')}
    </button>
    ${stats.render()}
  </div>`;
}

export function onAction(action, el) {
  if (action === 'accent') setAccent(el.dataset.accent);
  else if (action === 'household') openHouseholdSheet();
  else stats.onAction(action, el);
}
